"""Durable command consumer and coalesced result publisher; no model calls on HTTP requests."""
import asyncio
import hashlib
import hmac
import json
import logging
from uuid import UUID
from rocketmq.v5.client.client_configuration import ClientConfiguration, Credentials
from rocketmq.v5.consumer.simple.simple_consumer import SimpleConsumer
from rocketmq.v5.producer import Producer
from rocketmq.v5.model import Message, FilterExpression
from app.messaging.store import TaskStore

log = logging.getLogger(__name__)


class RocketTransport:
    def __init__(self, settings, chat, knowledge=None):
        self.knowledge = knowledge
        self.settings, self.chat = settings, chat
        self.store = TaskStore(chat.repo.pool)
        self.secret = settings.internal_token.get_secret_value().encode()
        self.ready = False
        self.tasks = []
        self.producer = self.consumer = None

    def envelope(self, payload):
        body = json.dumps(payload, ensure_ascii=False, separators=(',', ':'))
        signature = hmac.new(self.secret, body.encode(), hashlib.sha256).hexdigest()
        return json.dumps({'body': body, 'signature': signature}, ensure_ascii=False).encode()

    def decode(self, data):
        envelope = json.loads(data)
        body = envelope['body']
        expected = hmac.new(self.secret, body.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, envelope['signature']):
            raise ValueError('invalid signature')
        payload = json.loads(body)
        if payload.get('action') == 'index_knowledge':
            UUID(payload['jobId'])
            return payload
        UUID(payload['run_id'])
        if payload['action'] not in ('generate', 'stop'):
            raise ValueError('invalid action')
        return payload

    def connect(self):
        cfg = ClientConfiguration(self.settings.mq_endpoint, Credentials(self.settings.mq_access_key, self.settings.mq_secret_key.get_secret_value()), request_timeout=5)
        self.producer = Producer(cfg, [self.settings.mq_command_topic, self.settings.mq_result_topic])
        self.producer.startup()
        self.consumer = SimpleConsumer(cfg, self.settings.mq_consumer_group,
            {self.settings.mq_command_topic: FilterExpression('*')}, await_duration=5)
        self.consumer.startup()
        # SDK 5.1.2 returns before SimpleConsumer's telemetry handshake.
        if not self.consumer._init_settings_event.wait(10):
            raise TimeoutError("RocketMQ subscription handshake timed out")

    async def start(self):
        self.tasks = [asyncio.create_task(self.supervise())]

    async def supervise(self):
        while True:
            try:
                await asyncio.to_thread(self.connect)
                self.ready = True
                log.info('rocketmq_ready')
                async with asyncio.TaskGroup() as group:
                    group.create_task(self.consume())
                    group.create_task(self.publish())
                    group.create_task(self.watch_stops())
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                self.ready = False
                log.warning('rocketmq_reconnecting type=%s', type(exc).__name__)
            finally:
                self.ready = False
                await asyncio.to_thread(self.disconnect)
            await asyncio.sleep(3)

    def disconnect(self):
        for client in (self.consumer, self.producer):
            if client:
                try:
                    client.shutdown()
                except Exception:
                    pass
        self.consumer = self.producer = None

    async def handle(self, payload):
        if payload.get('action') == 'index_knowledge':
            await self.index_article(payload)
            return
        run_id = UUID(payload['run_id'])
        if payload['action'] == 'stop':
            task = self.chat.run_tasks.get(run_id)
            # A command alone cannot modify state: stop must have been authorized and committed.
            if task and await self.store.stopped([run_id]):
                task.cancel()
        else:
            claimed = await self.store.claim(run_id, self.settings.model_timeout_seconds)
            if claimed:
                self.chat.launch(*claimed)

    async def index_article(self, payload):
        from fastapi import HTTPException
        try:
            state = await self.knowledge.apply(payload)
            reason = ''
        except HTTPException as exc:
            state = 'WAIT_CONFIG' if exc.status_code == 409 else 'FAILED'
            reason = str(exc.detail)
        except Exception as exc:
            log.warning('knowledge_index_failed article=%s type=%s',payload['articleId'],type(exc).__name__)
            state, reason = 'FAILED', '向量化失败，请检查网络与模型服务后重试'
        result = {k:payload[k] for k in ('jobId','articleId','version')}
        result.update(action='index_result', state=state, reason=reason)
        message = Message()
        message.keys, message.topic = payload['jobId'], self.settings.mq_result_topic
        message.body = self.envelope(result)
        await asyncio.to_thread(self.producer.send, message)

    async def consume(self):
        while True:
            if len(self.chat.run_tasks) >= self.settings.mq_max_concurrency:
                await asyncio.sleep(.2)
                continue
            messages = await asyncio.to_thread(self.consumer.receive, 1, 30)
            for message in messages:
                try:
                    payload = self.decode(message.body)
                except (ValueError, KeyError, TypeError):
                    log.warning('rocketmq_invalid_command')
                    await asyncio.to_thread(self.consumer.ack, message)
                    continue
                # Ack only after the durable claim. Redelivery cannot call the model twice.
                await self.handle(payload)
                await asyncio.to_thread(self.consumer.ack, message)

    async def publish(self):
        while True:
            for row in await self.store.pending():
                message = Message()
                message.keys = str(row['id'])
                if row['kind'] == 'result':
                    message.topic = self.settings.mq_result_topic
                    payload = {'owner_id': row['owner_id'], 'portal': row['portal'], 'snapshot': self.chat.public(row)}
                else:
                    # An expired/stopped task should never be started by an old outbox item.
                    if row['kind'] == 'generate' and row['status'] != 'running':
                        await self.store.delivered(row)
                        continue
                    message.topic = self.settings.mq_command_topic
                    payload = {'run_id': str(row['id']), 'action': row['kind']}
                message.body = self.envelope(payload)
                await asyncio.to_thread(self.producer.send, message)
                await self.store.delivered(row)
            await asyncio.sleep(.35)

    async def watch_stops(self):
        while True:
            for run_id in await self.store.stopped(list(self.chat.run_tasks)):
                task = self.chat.run_tasks.get(run_id)
                if task:
                    task.cancel()
            await asyncio.sleep(.5)

    async def close(self):
        for task in self.tasks:
            task.cancel()
        await asyncio.gather(*self.tasks, return_exceptions=True)

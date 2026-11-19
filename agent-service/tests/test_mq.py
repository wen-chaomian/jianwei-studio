import asyncio
import json
from uuid import uuid4
import pytest
from app.schemas.chat import Actor, NewMessage
from app.messaging.rocketmq import RocketTransport
from app.messaging.store import TaskStore

USER = Actor(user_id=31, portal='customer', role='CUSTOMER')

async def prepared(env):
    env.settings.mq_enabled = True
    c = await env.repo.create(USER, uuid4())
    return await env.chat.submit(USER, c['id'], NewMessage(request_id=uuid4(), content='MQ test'))

async def test_admission_is_durable_and_does_not_call_model(env):
    run = await prepared(env)
    assert not env.chat.tasks
    pending = await TaskStore(env.pool).pending()
    assert {r['kind'] for r in pending} == {'generate', 'result'}
    assert all(str(r['id']) == run['id'] for r in pending)

async def test_redelivery_claims_only_once_and_uses_persisted_actor(env):
    run = await prepared(env)
    store = TaskStore(env.pool)
    claimed = await asyncio.gather(*(store.claim(run['id'],120) for _ in range(12)))
    assert sum(r is not None for r in claimed) == 1
    who, row = next(r for r in claimed if r)
    assert who == USER
    assert row['worker_started_at'] is not None

async def test_stopped_queued_command_cannot_run_and_late_output_cannot_overwrite(env):
    run = await prepared(env)
    from uuid import UUID
    rid = UUID(run['id'])
    await env.chat.stop(USER,rid)
    assert await TaskStore(env.pool).claim(rid,120) is None
    await env.repo.progress(rid,'late')
    await env.repo.finish(rid,'late')
    row = await env.repo.run(USER,rid)
    assert row['error_code'] == 'STOPPED'
    assert row['output'] == ''
    assert 'stop' in {r['kind'] for r in await TaskStore(env.pool).pending()}

async def test_new_revision_survives_old_outbox_ack(env):
    run = await prepared(env)
    store = TaskStore(env.pool)
    old = next(r for r in await store.pending() if r['kind']=='result')
    await env.repo.progress(run['id'],'newer')
    await store.delivered(old)
    fresh = next(r for r in await store.pending() if r['kind']=='result')
    assert fresh['event_revision'] > old['event_revision']
    await env.repo.finish(run['id'],'done')
    await store.delivered(fresh)
    terminal = next(r for r in await store.pending() if r['kind']=='result')
    assert terminal['status']=='completed'

async def test_crashed_worker_is_not_reexecuted_and_recovers_terminal_event(env):
    run = await prepared(env)
    store=TaskStore(env.pool)
    assert await store.claim(run['id'],120)
    async with env.pool.connection() as c:
        await c.execute("UPDATE agent_runs SET deadline=now()-interval '1 second' WHERE id=%s",(run['id'],))
    await env.repo.expire()
    assert await store.claim(run['id'],120) is None
    event=next(r for r in await store.pending() if r['kind']=='result')
    assert event['status']=='failed' and event['error_code']=='INTERRUPTED'

async def test_signed_envelope_rejects_forgery(env):
    transport=RocketTransport(env.settings,env.chat)
    payload={'run_id':str(uuid4()),'action':'generate'}
    assert transport.decode(transport.envelope(payload))==payload
    envelope=json.loads(transport.envelope(payload));envelope['body']=envelope['body'].replace('generate','stop')
    with pytest.raises(ValueError):transport.decode(json.dumps(envelope).encode())

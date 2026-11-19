import asyncio
import logging
import time
from contextlib import AsyncExitStack
from app.tools.business import connect_business
from fastapi.encoders import jsonable_encoder
from fastapi import HTTPException
from app.agents.dialogue import build_graph
from app.agents.business import has_internal_markup

log = logging.getLogger(__name__)


class ChatService:
    def __init__(
        self, repository, checkpointer, settings, configs, graph_factory=build_graph
    ):
        self.repo, self.checkpointer, self.settings = repository, checkpointer, settings
        self.configs, self.graph_factory = configs, graph_factory
        self.tasks = set()
        self.run_tasks = {}
        self.closing = False

    async def submit(self, actor, conversation_id, message):
        if self.closing:
            raise HTTPException(503, "助手正在重启，请稍后重试")
        run, created = await self.repo.start(
            actor, conversation_id, message, self.settings.model_timeout_seconds,
            mq=self.settings.mq_enabled,
        )
        if created and not self.settings.mq_enabled:
            self.launch(actor, run)
        return self.public(run)

    def launch(self, actor, run):
        task = asyncio.create_task(self.execute(actor, run))
        self.tasks.add(task)
        self.run_tasks[run["id"]] = task
        task.add_done_callback(lambda _: self.run_tasks.pop(run["id"], None))
        task.add_done_callback(self.tasks.discard)

    @staticmethod
    def public(run):
        return jsonable_encoder(
            {
                k: run.get(k)
                for k in [
                    "id",
                    "conversation_id",
                    "status",
                    "output",
                    "error_code",
                    "error_message",
                    "revision",
                    "progress_label",
                    "result_links",
                ]
            }
        )

    async def execute(self, actor, run):
        output, error = "", None
        stack = AsyncExitStack()
        business = None
        try:
            config_snapshot = await self.configs.snapshot()
            if not config_snapshot.ready:
                error = (
                    "MODEL_NOT_CONFIGURED",
                    "尚未配置 DeepSeek 模型与 API Key，请配置后重新发送。",
                )
                return
            if actor.mcp_grant.get_secret_value():
                try:
                    business = await stack.enter_async_context(connect_business(self.settings, actor, self.repo, run['id']))
                except Exception:
                    error = ('BUSINESS_UNAVAILABLE', '业务查询连接不可用或登录已失效，请稍后重试或重新登录。')
                    return
            if business:
                graph = self.graph_factory(self.settings, self.checkpointer, config_snapshot, business=business)
            else:
                graph = self.graph_factory(self.settings, self.checkpointer, config_snapshot)
            context = await self.repo.context(run["conversation_id"], run["id"])
            # A distinct checkpoint thread per run prevents failed/partial answers contaminating later context.
            config = {
                "configurable": {
                    "thread_id": f"{actor.portal}:{actor.user_id}:{run['conversation_id']}:{run['id']}"
                }
            }
            last_save = 0
            async with asyncio.timeout(self.settings.model_timeout_seconds):
                async for mode, value in graph.astream(
                    {"messages": context, "role": actor.role, "answer": ""},
                    config,
                    stream_mode=["messages", "updates"],
                ):
                    if mode == "messages":
                        chunk, metadata = value
                        if business and metadata.get("langgraph_node") != "respond":
                            continue
                        if isinstance(chunk.content, str):
                            output += chunk.content
                    elif mode == "updates" and value.get("respond"):
                        answer = value["respond"].get("answer")
                        if isinstance(answer, str):
                            output = answer
                    if time.monotonic() - last_save > 0.25:
                        current = await self.repo.run(actor, run["id"])
                        if current["status"] != "running":
                            return
                        await self.repo.progress(run["id"], output.split("<", 1)[0] if business else output)
                        last_save = time.monotonic()
            if business and output.strip() and not has_internal_markup(output):
                await business.finalize_sources(output)
            if not output.strip():
                error = ("EMPTY_RESPONSE", "模型未返回有效回复，请重新发送。")
        except asyncio.CancelledError:
            error = ("INTERRUPTED", "服务重启，回复已中断，请重新发送。")
        except TimeoutError:
            error = ("MODEL_TIMEOUT", "模型响应超时，请稍后重新发送。")
        except Exception as exc:
            # Never log credentials, prompts, provider response bodies or personal content.
            log.warning(
                "agent_run_failed run=%s type=%s", run["id"], type(exc).__name__
            )
            error = ("MODEL_FAILED", "模型调用失败，请检查配置或稍后重新发送。")
        finally:
            if business and has_internal_markup(output):
                output = output.split("<", 1)[0].rstrip()
                error = ("INVALID_RESPONSE", "回复格式异常，请重新发送。")
            try:
                await stack.aclose()
            except Exception as exc:
                log.warning("mcp_close_failed type=%s", type(exc).__name__)
            try:
                await self.repo.finish(run["id"], output, error)
            except Exception as exc:
                log.error(
                    "agent_run_persist_failed run=%s type=%s",
                    run["id"],
                    type(exc).__name__,
                )

    async def stop(self, actor, run_id):
        run = await self.repo.run(actor, run_id)
        if run["status"] == "running":
            # Commit the terminal state before cancelling: late chunks cannot overwrite it.
            await self.repo.finish(run_id, run["output"], ("STOPPED", "已停止生成"))
            task = self.run_tasks.get(run_id)
            if task:
                task.cancel()
                await asyncio.gather(task, return_exceptions=True)
        return self.public(await self.repo.run(actor, run_id))

    async def close(self):
        self.closing = True
        tasks = list(self.tasks)
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

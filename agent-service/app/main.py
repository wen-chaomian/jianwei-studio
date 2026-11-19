import asyncio
import logging
from contextlib import asynccontextmanager, suppress
from pathlib import Path
from fastapi import FastAPI
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from app.core.config import Settings
from app.core.local_postgres import ensure_local_postgres
from app.repositories.chat import ChatRepository
from app.services.chat import ChatService
from app.services.model_config import ModelConfigService
from app.api.chat import router
from app.api.knowledge import router as knowledge_router
from app.services.embedding import EmbeddingService
from app.services.embedding_rebuild import EmbeddingRebuild
from app.services.knowledge import KnowledgeIndex
from app.messaging.rocketmq import RocketTransport
from app.messaging.local import ensure_local_mq


@asynccontextmanager
async def lifespan(app):
    settings = Settings()
    await ensure_local_postgres(settings)
    await ensure_local_mq(settings)
    pool = AsyncConnectionPool(
        settings.database_url.get_secret_value(),
        min_size=1,
        max_size=8,
        open=False,
        kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row},
    )
    await pool.open(wait=True)
    try:
        # Serialise schema setup across local service starts.
        async with pool.connection() as conn:
            await conn.execute("SELECT pg_advisory_lock(73510241)")
            try:
                await conn.execute(
                    (Path(__file__).parents[1] / "migrations/001_chat.sql").read_text(),
                    prepare=False,
                )
                await conn.execute((Path(__file__).parents[1] / "migrations/002_mq.sql").read_text(), prepare=False)
                await conn.execute((Path(__file__).parents[1] / "migrations/003_mcp.sql").read_text(), prepare=False)
                await conn.execute((Path(__file__).parents[1] / "migrations/004_knowledge.sql").read_text(), prepare=False)
                await conn.execute((Path(__file__).parents[1] / "migrations/005_embedding_switch.sql").read_text(), prepare=False)
                checkpointer = AsyncPostgresSaver(pool)
                await checkpointer.setup()
            finally:
                await conn.execute("SELECT pg_advisory_unlock(73510241)")
        repo = ChatRepository(pool)
        app.state.settings, app.state.repo = settings, repo
        app.state.configs = ModelConfigService(pool, settings)
        app.state.embeddings = EmbeddingService(pool, settings)
        app.state.knowledge = KnowledgeIndex(pool, app.state.embeddings)
        app.state.chat = ChatService(repo, checkpointer, settings, app.state.configs)

        async def reap():
            while True:
                try:
                    await repo.expire()
                except Exception as exc:
                    logging.getLogger(__name__).warning(
                        "recovery_failed type=%s", type(exc).__name__
                    )
                await asyncio.sleep(5)

        transport = RocketTransport(settings, app.state.chat, app.state.knowledge) if settings.mq_enabled else None
        app.state.transport = transport
        if transport:
            await transport.start()
        reaper = asyncio.create_task(reap())
        rebuild = asyncio.create_task(EmbeddingRebuild(pool, app.state.embeddings).run())
        try:
            yield
        finally:
            rebuild.cancel()
            with suppress(asyncio.CancelledError):
                await rebuild
        if transport:
            await transport.close()
        await app.state.chat.close()
        reaper.cancel()
        with suppress(asyncio.CancelledError):
            await reaper
    finally:
        await pool.close()


app = FastAPI(
    title="Jianwei Agent internal service",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
app.include_router(router)
app.include_router(knowledge_router)


@app.get("/health")
async def health():
    return {"status": "ok"}

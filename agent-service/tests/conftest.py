from pathlib import Path
from uuid import uuid4
from types import SimpleNamespace
import pytest_asyncio
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from psycopg import sql
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from app.core.config import Settings
from app.repositories.chat import ChatRepository
from app.services.model_config import ModelConfigService
from app.services.chat import ChatService
from app.api.chat import router
from app.api.knowledge import router as knowledge_router
from app.services.embedding import EmbeddingService
from app.services.knowledge import KnowledgeIndex


@pytest_asyncio.fixture
async def env():
    settings = Settings(mq_enabled=False)
    url = (
        settings.database_url.get_secret_value().rsplit("/", 1)[0]
        + "/shiguang_agent_test"
    )
    schema = "test_" + uuid4().hex
    root = AsyncConnectionPool(
        url, open=False, kwargs={"autocommit": True, "row_factory": dict_row}
    )
    await root.open(wait=True)
    async with root.connection() as c:
        await c.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public")
        await c.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
    pool = AsyncConnectionPool(
        url,
        open=False,
        min_size=1,
        max_size=8,
        kwargs={
            "autocommit": True,
            "row_factory": dict_row,
            "prepare_threshold": 0,
            "options": "-c search_path=" + schema + ",public",
        },
    )
    await pool.open(wait=True)
    async with pool.connection() as c:
        await c.execute(Path("migrations/001_chat.sql").read_text(), prepare=False)
        await c.execute(Path("migrations/002_mq.sql").read_text(), prepare=False)
        await c.execute(Path("migrations/003_mcp.sql").read_text(), prepare=False)
        await c.execute(Path("migrations/004_knowledge.sql").read_text(), prepare=False)
        await c.execute(Path("migrations/005_embedding_switch.sql").read_text(), prepare=False)
    checkpointer = AsyncPostgresSaver(pool)
    await checkpointer.setup()
    repo = ChatRepository(pool)
    configs = ModelConfigService(pool, settings)
    chat = ChatService(repo, checkpointer, settings, configs)
    app = FastAPI()
    app.include_router(router)
    app.include_router(knowledge_router)
    app.state.embeddings = EmbeddingService(pool, settings)
    app.state.knowledge = KnowledgeIndex(pool, app.state.embeddings)
    app.state.settings = settings
    app.state.repo = repo
    app.state.chat = chat
    app.state.configs = configs
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        yield SimpleNamespace(
            settings=settings,
            pool=pool,
            repo=repo,
            configs=configs,
            chat=chat,
            app=app,
            client=client,
            checkpointer=checkpointer,
        )
    await chat.close()
    await pool.close()
    async with root.connection() as c:
        await c.execute(
            sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(schema))
        )
    await root.close()

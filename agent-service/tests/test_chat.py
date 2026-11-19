import asyncio
from uuid import uuid4
import pytest
from fastapi import HTTPException
from langchain_core.language_models.fake_chat_models import FakeListChatModel
from app.schemas.chat import Actor, NewMessage, SaveModelConfig
from app.agents.dialogue import build_graph
from app.services.chat import ChatService

USER = Actor(user_id=1, portal="customer", role="CUSTOMER")
ADMIN = Actor(user_id=9, portal="admin", role="ADMIN")


def headers(env, actor=USER):
    return {
        "X-Agent-Token": env.settings.internal_token.get_secret_value(),
        "X-Actor-Id": str(actor.user_id),
        "X-Actor-Portal": actor.portal,
        "X-Actor-Role": actor.role,
    }


async def configured(env):
    from pydantic import SecretStr

    await env.configs.save(
        ADMIN,
        SaveModelConfig(
            api_key=SecretStr("test-only-not-a-provider-key"),
            model="test-model",
            version=0,
        ),
    )


async def drain(chat):
    await asyncio.gather(*list(chat.tasks))


async def test_internal_auth_and_portal_validation(env):
    assert (await env.client.get("/internal/v1/status")).status_code == 401
    h = headers(env)
    h["X-Actor-Role"] = "ADMIN"
    assert (await env.client.get("/internal/v1/status", headers=h)).status_code == 403
    assert (
        await env.client.get("/internal/v1/model-config", headers=headers(env))
    ).status_code == 403
    assert (
        await env.client.put(
            "/internal/v1/model-config",
            headers=headers(env),
            json={"model": "x", "version": 0},
        )
    ).status_code == 403


async def test_owned_conversation_and_runs_are_isolated(env):
    c = await env.repo.create(USER, uuid4())
    run, _ = await env.repo.start(
        USER, c["id"], NewMessage(request_id=uuid4(), content="hello"), 30
    )
    for other in [
        Actor(user_id=2, portal="customer", role="CUSTOMER"),
        Actor(user_id=1, portal="staff", role="RECEPTIONIST"),
    ]:
        assert (await env.repo.conversations(other, 0))["items"] == []
        for path in [f"/conversations/{c['id']}", f"/runs/{run['id']}/events"]:
            response = await env.client.get(
                "/internal/v1" + path, headers=headers(env, other)
            )
            assert response.status_code == 404
        response = await env.client.post(
            f"/internal/v1/conversations/{c['id']}/messages",
            headers=headers(env, other),
            json={"content": "steal", "request_id": str(uuid4())},
        )
        assert response.status_code == 404


async def test_duplicate_send_and_concurrent_send(env):
    key = uuid4()
    c = await env.repo.create(USER, key)
    assert (await env.repo.create(USER, key))["id"] == c["id"]
    msg = NewMessage(request_id=uuid4(), content="same")
    results = await asyncio.gather(
        *(env.repo.start(USER, c["id"], msg, 30) for _ in range(8))
    )
    assert sum(created for _, created in results) == 1
    assert len({row["id"] for row, _ in results}) == 1
    with pytest.raises(HTTPException) as exc:
        await env.repo.start(
            USER, c["id"], NewMessage(request_id=uuid4(), content="second"), 30
        )
    assert exc.value.status_code == 409
    with pytest.raises(HTTPException):
        await env.repo.start(
            USER, c["id"], NewMessage(request_id=msg.request_id, content="changed"), 30
        )


async def test_missing_model_explicit_failure_persisted(env):
    c = await env.repo.create(USER, uuid4())
    run = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="帮我预约")
    )
    await drain(env.chat)
    saved = await env.repo.run(USER, run["id"])
    assert (
        saved["status"] == "failed"
        and saved["error_code"] == "MODEL_NOT_CONFIGURED"
        and saved["output"] == ""
    )
    data = await env.repo.detail(USER, c["id"], 2**63 - 1)
    assert len(data["messages"]) == 2 and data["messages"][1]["error_message"]
    response = await env.client.get(
        "/internal/v1/runs/" + run["id"] + "/events", headers=headers(env)
    )
    assert (
        "MODEL_NOT_CONFIGURED" in response.text and "event: snapshot" in response.text
    )


async def test_stream_checkpoint_and_restart_history(env):
    await configured(env)
    env.chat.graph_factory = lambda s, c, m: build_graph(
        s, c, m, FakeListChatModel(responses=["测试回复，非真实模型输出。"])
    )
    c = await env.repo.create(USER, uuid4())
    run = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="你好")
    )
    await drain(env.chat)
    saved = await env.repo.run(USER, run["id"])
    assert (
        saved["status"] == "completed"
        and saved["output"] == "测试回复，非真实模型输出。"
    )
    new_service = ChatService(env.repo, env.checkpointer, env.settings, env.configs)
    data = await new_service.repo.detail(USER, c["id"], 2**63 - 1)
    assert len(data["messages"]) == 2
    thread = f"customer:1:{c['id']}:{run['id']}"
    checkpoint = await env.checkpointer.aget_tuple(
        {"configurable": {"thread_id": thread}}
    )
    assert checkpoint is not None
    # Reconnecting twice returns the same final result without invoking the model again.
    for _ in range(2):
        response = await env.client.get(
            "/internal/v1/runs/" + run["id"] + "/events", headers=headers(env)
        )
        assert "completed" in response.text
    assert len((await env.repo.detail(USER, c["id"], 2**63 - 1))["messages"]) == 2


async def test_failed_runs_not_in_next_context(env):
    c = await env.repo.create(USER, uuid4())
    run, _ = await env.repo.start(
        USER, c["id"], NewMessage(request_id=uuid4(), content="failed question"), 30
    )
    await env.repo.finish(run["id"], "partial", ("MODEL_FAILED", "failure"))
    next_run, _ = await env.repo.start(
        USER, c["id"], NewMessage(request_id=uuid4(), content="next question"), 30
    )
    assert await env.repo.context(c["id"], next_run["id"]) == [
        {"role": "user", "content": "next question"}
    ]


async def test_crashed_run_expires_and_new_run_allowed(env):
    c = await env.repo.create(USER, uuid4())
    run, _ = await env.repo.start(
        USER, c["id"], NewMessage(request_id=uuid4(), content="hello"), 30
    )
    async with env.pool.connection() as conn:
        await conn.execute(
            "UPDATE agent_runs SET deadline=now()-interval '1 second' WHERE id=%s",
            (run["id"],),
        )
    await env.repo.expire()
    await env.repo.expire()
    assert (await env.repo.run(USER, run["id"]))["status"] == "failed"
    assert len((await env.repo.detail(USER, c["id"], 2**63 - 1))["messages"]) == 2
    assert (
        await env.repo.start(
            USER, c["id"], NewMessage(request_id=uuid4(), content="again"), 30
        )
    )[1]


async def test_model_error_sanitized(env):
    await configured(env)

    class Broken:
        async def astream(self, *a, **kw):
            raise RuntimeError(
                "sensitive upstream details test-only-not-a-provider-key"
            )
            yield

    env.chat.graph_factory = lambda *a: Broken()
    c = await env.repo.create(USER, uuid4())
    run = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="hello")
    )
    await drain(env.chat)
    saved = await env.repo.run(USER, run["id"])
    assert (
        saved["error_code"] == "MODEL_FAILED"
        and "sensitive" not in saved["error_message"]
    )


async def test_runtime_model_update_applies_to_next_message(env):
    await configured(env)
    seen = []
    started = asyncio.Event()
    release = asyncio.Event()

    class Graph:
        def __init__(self, cfg):
            self.cfg = cfg

        async def astream(self, *a, **kw):
            seen.append(self.cfg.model)
            started.set()
            await release.wait()
            yield "updates", {"respond": {"answer": self.cfg.model}}

    env.chat.graph_factory = lambda s, c, m: Graph(m)
    c = await env.repo.create(USER, uuid4())
    first = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="one")
    )
    await started.wait()
    await env.configs.save(ADMIN, SaveModelConfig(model="new-model", version=1))
    release.set()
    await drain(env.chat)
    second = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="two")
    )
    await drain(env.chat)
    assert seen == ["test-model", "new-model"]
    assert (await env.repo.run(USER, first["id"]))["output"] == "test-model"
    assert (await env.repo.run(USER, second["id"]))["output"] == "new-model"


async def test_config_encrypted_masked_and_versioned(env):
    await configured(env)
    public = await env.configs.public()
    assert public["key_configured"] and "key" not in public
    async with env.pool.connection() as conn:
        row = await (
            await conn.execute(
                "SELECT encrypted_key FROM agent_model_config WHERE id=1"
            )
        ).fetchone()
    assert "test-only" not in row["encrypted_key"]
    with pytest.raises(HTTPException) as exc:
        await env.configs.save(ADMIN, SaveModelConfig(model="stale", version=0))
    assert exc.value.status_code == 409
    await env.configs.save(ADMIN, SaveModelConfig(model="new", version=1))
    assert (await env.configs.snapshot()).key == "test-only-not-a-provider-key"
    response = await env.client.get(
        "/internal/v1/model-config", headers=headers(env, ADMIN)
    )
    assert "test-only" not in response.text


async def test_blank_and_oversized_messages_rejected(env):
    c = await env.repo.create(USER, uuid4())
    for content in ["   ", "x" * 4001]:
        response = await env.client.post(
            f"/internal/v1/conversations/{c['id']}/messages",
            headers=headers(env),
            json={"request_id": str(uuid4()), "content": content},
        )
        assert response.status_code == 422


async def test_timeout_and_shutdown_leave_recoverable_failure(env):
    await configured(env)

    class Slow:
        async def astream(self, *a, **kw):
            await asyncio.sleep(60)
            yield "updates", {"respond": {"answer": "should not complete"}}

    env.chat.graph_factory = lambda *a: Slow()
    env.chat.settings = env.settings.model_copy(update={"model_timeout_seconds": 0.02})
    c = await env.repo.create(USER, uuid4())
    run = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="slow")
    )
    await drain(env.chat)
    assert (await env.repo.run(USER, run["id"]))["error_code"] == "MODEL_TIMEOUT"
    env.chat.settings = env.settings
    second = await env.chat.submit(
        USER, c["id"], NewMessage(request_id=uuid4(), content="shutdown")
    )
    await asyncio.sleep(0.02)
    await env.chat.close()
    assert (await env.repo.run(USER, second["id"]))["error_code"] == "INTERRUPTED"


async def test_account_concurrency_limit_does_not_block_other_users(env):
    for _ in range(3):
        c = await env.repo.create(USER, uuid4())
        await env.repo.start(
            USER, c["id"], NewMessage(request_id=uuid4(), content="active"), 30
        )
    c = await env.repo.create(USER, uuid4())
    with pytest.raises(HTTPException) as exc:
        await env.repo.start(
            USER, c["id"], NewMessage(request_id=uuid4(), content="fourth"), 30
        )
    assert exc.value.status_code == 429
    other = Actor(user_id=2, portal="customer", role="CUSTOMER")
    c = await env.repo.create(other, uuid4())
    assert (
        await env.repo.start(
            other, c["id"], NewMessage(request_id=uuid4(), content="independent"), 30
        )
    )[1]


async def test_conversation_controls_and_isolated_context(env):
    a = await env.repo.create(USER, uuid4())
    b = await env.repo.create(USER, uuid4())
    response = await env.client.patch(f"/internal/v1/conversations/{a['id']}", headers=headers(env), json={"title": "新的标题"})
    assert response.status_code == 200
    run, _ = await env.repo.start(USER, a['id'], NewMessage(request_id=uuid4(), content="secret A"), 30)
    await env.repo.finish(run['id'], "answer A", None)
    other_run, _ = await env.repo.start(USER, b['id'], NewMessage(request_id=uuid4(), content="hello B"), 30)
    assert await env.repo.context(b['id'], other_run['id']) == [{"role": "user", "content": "hello B"}]
    assert (await env.repo.detail(USER, a['id'], 2**63-1))['conversation']['title'] == '新的标题'
    for other in [Actor(user_id=2, portal="customer", role="CUSTOMER"), Actor(user_id=1, portal="staff", role="RECEPTIONIST")]:
        h = headers(env, other)
        assert (await env.client.patch(f"/internal/v1/conversations/{a['id']}", headers=h, json={"title": "bad"})).status_code == 404
        assert (await env.client.delete(f"/internal/v1/conversations/{a['id']}", headers=h)).status_code == 404
        assert (await env.client.post(f"/internal/v1/runs/{other_run['id']}/stop", headers=h)).status_code == 404
    assert (await env.client.delete(f"/internal/v1/conversations/{b['id']}", headers=headers(env))).status_code == 409
    assert (await env.client.delete(f"/internal/v1/conversations/{a['id']}", headers=headers(env))).status_code == 200
    assert (await env.client.get(f"/internal/v1/conversations/{a['id']}", headers=headers(env))).status_code == 404


async def test_stop_cancels_model_and_keeps_partial(env):
    from types import SimpleNamespace
    from uuid import UUID
    await configured(env)
    entered, cancelled = asyncio.Event(), asyncio.Event()
    class SlowGraph:
        async def astream(self, *args, **kwargs):
            try:
                yield 'messages', (SimpleNamespace(content='partial'), {})
                entered.set()
                await asyncio.sleep(30)
            finally:
                cancelled.set()
    env.chat.graph_factory = lambda *args: SlowGraph()
    c = await env.repo.create(USER, uuid4())
    run = await env.chat.submit(USER, c['id'], NewMessage(request_id=uuid4(), content='go'))
    await asyncio.wait_for(entered.wait(), 3)
    stopped = await env.chat.stop(USER, UUID(run['id']))
    assert stopped['error_code'] == 'STOPPED' and stopped['output'] == 'partial'
    assert cancelled.is_set()
    assert (await env.chat.stop(USER, UUID(run['id'])))['revision'] == stopped['revision']
    await env.repo.finish(UUID(run['id']), 'late overwrite', None)
    assert (await env.repo.run(USER, UUID(run['id'])))['output'] == 'partial'
    next_run, _ = await env.repo.start(USER, c['id'], NewMessage(request_id=uuid4(), content='next'), 30)
    assert await env.repo.context(c['id'], next_run['id']) == [{'role': 'user', 'content': 'next'}]


@pytest.mark.asyncio
async def test_copy_key_requires_admin_and_is_not_cached(env):
    await configured(env)
    for who in (Actor(user_id=1, portal="customer", role="CUSTOMER"), Actor(user_id=2, portal="staff", role="RECEPTIONIST")):
        response = await env.client.post("/internal/v1/model-config/key", headers=headers(env, who))
        assert response.status_code == 403
    response = await env.client.post("/internal/v1/model-config/key", headers=headers(env, ADMIN))
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert response.json()["api_key"] == "test-only-not-a-provider-key"
    public = await env.configs.public()
    assert public["key_masked"] != response.json()["api_key"]
    assert "api_key" not in public

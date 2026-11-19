import asyncio
import hmac
import json
from uuid import UUID
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Query
from fastapi.responses import StreamingResponse, JSONResponse
from app.schemas.chat import Actor, NewConversation, NewMessage, SaveModelConfig, RenameConversation

router = APIRouter(prefix="/internal/v1")


async def actor(
    request: Request,
    x_agent_token: str = Header(default=""),
    x_actor_id: int = Header(default=0),
    x_actor_portal: str = Header(default=""),
    x_actor_role: str = Header(default=""),
    x_mcp_grant: str = Header(default=""),
):
    secret = request.app.state.settings.internal_token.get_secret_value()
    if not hmac.compare_digest(x_agent_token, secret):
        raise HTTPException(401, "内部认证失败")
    allowed = {
        "customer": {"CUSTOMER"},
        "staff": {"RECEPTIONIST", "PHOTOGRAPHER"},
        "admin": {"ADMIN"},
    }
    if x_actor_id <= 0 or x_actor_role not in allowed.get(x_actor_portal, set()):
        raise HTTPException(403, "身份无效")
    return Actor(user_id=x_actor_id, portal=x_actor_portal, role=x_actor_role, mcp_grant=x_mcp_grant)


@router.get("/status")
async def status(request: Request, who: Actor = Depends(actor)):
    config = await request.app.state.configs.snapshot()
    return {
        "available": True,
        "model_ready": config.ready,
        "message": "助手已就绪" if config.ready else "尚未配置 DeepSeek 模型与 API Key",
    }


@router.get("/conversations")
async def conversations(
    request: Request,
    offset: int = Query(0, ge=0, le=10000),
    who: Actor = Depends(actor),
):
    return await request.app.state.repo.conversations(who, offset)


@router.post("/conversations")
async def create(body: NewConversation, request: Request, who: Actor = Depends(actor)):
    return await request.app.state.repo.create(who, body.request_id)


@router.get("/conversations/{conversation_id}")
async def detail(
    conversation_id: UUID,
    request: Request,
    before: int = Query(9223372036854775807, gt=0),
    who: Actor = Depends(actor),
):
    return await request.app.state.repo.detail(who, conversation_id, before)


@router.post("/conversations/{conversation_id}/messages")
async def submit(
    conversation_id: UUID,
    body: NewMessage,
    request: Request,
    who: Actor = Depends(actor),
):
    return await request.app.state.chat.submit(who, conversation_id, body)


@router.get("/runs/{run_id}")
async def run_snapshot(run_id: UUID, request: Request, who: Actor = Depends(actor)):
    return request.app.state.chat.public(await request.app.state.repo.run(who, run_id))


@router.get("/runs/{run_id}/events")
async def events(run_id: UUID, request: Request, who: Actor = Depends(actor)):
    repo = request.app.state.repo
    await repo.run(who, run_id)  # Authorize before streaming headers are committed.

    async def stream():
        revision = -1
        heartbeat = 0
        while not await request.is_disconnected():
            run = await repo.run(who, run_id)
            if run["revision"] != revision:
                revision = run["revision"]
                yield (
                    "event: snapshot\ndata: "
                    + json.dumps(request.app.state.chat.public(run), ensure_ascii=False)
                    + "\n\n"
                )
            if run["status"] != "running":
                return
            heartbeat += 1
            if heartbeat % 10 == 0:
                yield ": heartbeat\n\n"
            await asyncio.sleep(0.5)

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )


async def admin(who: Actor = Depends(actor)):
    if who.portal != "admin" or who.role != "ADMIN":
        raise HTTPException(403, "仅管理员可配置模型")
    return who


@router.get("/model-config")
async def config(request: Request, who: Actor = Depends(admin)):
    return await request.app.state.configs.public()


@router.post("/model-config/key")
async def copy_config_key(request: Request, who: Actor = Depends(admin)):
    config = await request.app.state.configs.snapshot()
    return JSONResponse({"api_key": config.key}, headers={"Cache-Control": "no-store"})


@router.put("/model-config")
async def save_config(
    body: SaveModelConfig, request: Request, who: Actor = Depends(admin)
):
    return await request.app.state.configs.save(who, body)


@router.get("/models")
async def models(request: Request, who: Actor = Depends(admin)):
    return await request.app.state.configs.models()


@router.patch("/conversations/{conversation_id}")
async def rename_conversation(conversation_id: UUID, body: RenameConversation, request: Request, who: Actor = Depends(actor)):
    return await request.app.state.repo.rename(who, conversation_id, body.title)


@router.delete("/conversations/{conversation_id}")
async def delete_conversation(conversation_id: UUID, request: Request, who: Actor = Depends(actor)):
    return await request.app.state.repo.delete(who, conversation_id)


@router.post("/runs/{run_id}/stop")
async def stop_run(run_id: UUID, request: Request, who: Actor = Depends(actor)):
    return await request.app.state.chat.stop(who, run_id)

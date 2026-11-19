from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse
from fastapi import HTTPException
from pydantic import BaseModel, Field
from app.api.chat import actor, admin
from app.services.embedding import SettingsUpdate

router = APIRouter(prefix='/internal/v1/knowledge')


class Version(BaseModel):
    id: int = Field(gt=0)
    version: int = Field(gt=0)


class Search(BaseModel):
    question: str = Field(min_length=1,max_length=500)
    allowed: list[Version]


@router.get('/config', dependencies=[Depends(admin)])
async def config(request: Request):
    return await request.app.state.embeddings.public()


@router.get('/config/models', dependencies=[Depends(admin)])
async def models(request: Request):
    return await request.app.state.embeddings.models()


@router.post('/config/key', dependencies=[Depends(admin)])
async def copy_key(request: Request):
    service = request.app.state.embeddings
    row = await service.configuration()
    if not row['encrypted_key']:
        raise HTTPException(409, '请先保存密钥')
    key = service.cipher.decrypt(row['encrypted_key'].encode()).decode()
    return JSONResponse({'api_key':key}, headers={'Cache-Control':'no-store'})


@router.put('/config', dependencies=[Depends(admin)])
async def save_config(body: SettingsUpdate, request: Request):
    return await request.app.state.embeddings.save(body)


@router.post('/search', dependencies=[Depends(actor)])
async def search(body: Search, request: Request):
    return await request.app.state.knowledge.search(body.question,[v.model_dump() for v in body.allowed])

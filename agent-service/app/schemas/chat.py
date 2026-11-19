from typing import Literal
from uuid import UUID
from pydantic import BaseModel, Field, SecretStr, field_validator


class Actor(BaseModel):
    mcp_grant: SecretStr = SecretStr("")
    user_id: int = Field(gt=0)
    portal: Literal["customer", "staff", "admin"]
    role: Literal["CUSTOMER", "RECEPTIONIST", "PHOTOGRAPHER", "ADMIN"]


class NewConversation(BaseModel):
    request_id: UUID


class NewMessage(BaseModel):
    request_id: UUID
    content: str = Field(min_length=1, max_length=4000)

    @field_validator("content")
    @classmethod
    def clean(cls, value):
        value = value.strip()
        if not value:
            raise ValueError("消息不能为空")
        return value


class SaveModelConfig(BaseModel):
    api_key: SecretStr | None = None
    model: str = Field(default="", max_length=100, pattern=r"^[a-zA-Z0-9_.:/-]*$")
    version: int = Field(ge=0)

    @field_validator("api_key")
    @classmethod
    def key_length(cls, value):
        if value and len(value.get_secret_value()) > 512:
            raise ValueError("API Key 过长")
        return value


class RenameConversation(BaseModel):
    title: str = Field(min_length=1, max_length=80)

    @field_validator("title")
    @classmethod
    def clean(cls, value):
        if not value.strip():
            raise ValueError("标题不能为空")
        return value.strip()

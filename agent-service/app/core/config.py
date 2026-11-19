from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_prefix="AGENT_", extra="ignore"
    )
    postgres_autostart: bool = False
    database_url: SecretStr
    internal_token: SecretStr = Field(min_length=32)
    encryption_key: SecretStr
    deepseek_api_key: SecretStr = SecretStr("")
    deepseek_model: str = ""
    model_timeout_seconds: int = Field(default=120, ge=5, le=300)

    mcp_url: str = "http://127.0.0.1:8080/internal/mcp"
    embedding_workspace: str = Field(default="", pattern=r"^(?:ws-[a-z0-9-]{1,60})?$")
    mq_enabled: bool = True
    mq_autostart: bool = False
    mq_access_key: str = ""
    mq_secret_key: SecretStr = SecretStr("")
    mq_endpoint: str = "127.0.0.1:8081"
    mq_command_topic: str = "JianweiAgentCommand"
    mq_result_topic: str = "JianweiAgentResult"
    mq_consumer_group: str = "JianweiAgentWorkers"
    mq_max_concurrency: int = Field(default=8, ge=1, le=32)

    @property
    def model_ready(self):
        return bool(
            self.deepseek_api_key.get_secret_value().strip()
            and self.deepseek_model.strip()
        )

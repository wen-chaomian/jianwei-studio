# 见微摄影预约平台

面向摄影工作室的预约与客户接待平台。客户、员工和管理员通过各自的工作台办理业务，也可以通过「见微助手」查询知识、准备操作，并在确认后执行。

| 工作台 | 主要功能 |
| --- | --- |
| 客户端 `/customer` | 浏览套餐与档期、预约、提交改期或取消申请、查进度和消息、管理拍摄偏好 |
| 员工端 `/staff` | 接待办理预约、处理申请、登记到店和收退款；摄影师查看自己的安排并更新拍摄进度 |
| 管理端 `/admin` | 管理套餐、资源、周排班、员工权限、预约规则、知识库及语言模型和向量模型配置 |

## 代码结构

| 目录 | 职责 |
| --- | --- |
| `frontend/` | Vue 3、Vue Router、Element Plus 和 Vite。三端页面和 SSE 客户端 |
| `backend/` | Java 21、Spring Boot、Spring Security、MyBatis-Plus 和 Flyway。负责鉴权、预约、资金登记、消息、业务工具和最终写入 |
| `agent-service/` | Python 3.12+、FastAPI 和 LangGraph。负责对话、MCP 工具调用和知识检索。PostgreSQL 保存会话与任务，pgvector 支持相似度检索 |
| `scripts/` | 前后端打包、Agent 与本地 RocketMQ 启动、隔离环境验收和压测脚本 |

MySQL 保存预约等业务记录。Redis 用于会话、缓存、预约资源锁和进度快照。启用助手时，RocketMQ 传递任务与结果，三端通过 Java 访问助手。

## 快速运行三端业务

安装 Docker 和 Docker Compose，在仓库根目录执行：

```sh
cp .env.example .env
```

编辑 `.env`，填写数据库应用密码 `DB_PASSWORD`、数据库管理员密码 `MYSQL_ROOT_PASSWORD`、自己指定的 11 位管理员登录手机号 `ADMIN_PHONE` 和登录密码 `ADMIN_PASSWORD`。请为每项密码设置独立值。然后执行：

```sh
docker compose up --build -d
docker compose logs -f app
```

打开 [客户端](http://localhost:8080/customer)、[员工端](http://localhost:8080/staff) 或 [管理端](http://localhost:8080/admin)。第一次启动会自动迁移数据库并创建管理员账号。客户可自行注册；管理员可在「员工与权限」创建接待或摄影师账号。收退款操作记录工作室的收退款流水。

Compose 启动 Java、MySQL 和 Redis。助手需要下节列出的独立服务。

**可选演示数据**　首次在空的本地演示库启动前，可设置 `SEED_ENABLED=true` 和 `SEED_PASSWORD`（至少 8 个字符，最多 72 个 UTF-8 字节）。这样会生成虚构的客户、摄影师、影棚、套餐和预约。演示客户从 `19899000001` 起，演示员工使用 `1999900` 开头的号码，演示账号共用本次指定的密码。请为初始管理员另选号码。初始化完成后可以关闭演示开关。

## 本机开发与完整助手

以下步骤适用于 macOS/Linux 本机开发，命令从仓库根目录执行。Java、Python、MySQL、Redis、PostgreSQL、RocketMQ 在同一主机上运行。

1. 安装 JDK 21、Maven 3.9、Node.js 24、Python 3.12+、MySQL 8.4、Redis 7，以及支持 pgvector 的 PostgreSQL。启动数据库和 Redis。在 MySQL 中创建 `shiguang_studio` 库及有该库迁移、读写权限的应用用户；在 PostgreSQL 中创建 `shiguang_agent` 库及其所有者，用管理员身份在该库执行 `CREATE EXTENSION IF NOT EXISTS vector;`。
2. 创建 `backend/.local/application-local.properties`，填写下列项目。Java 从其工作目录的 `.local/` 读取配置。若数据库使用其他地址，可在同一文件设置 `spring.datasource.url` 和 `spring.data.redis.host`。

   ```properties
   DB_USERNAME=你的MySQL应用用户名
   DB_PASSWORD=你的MySQL应用密码
   studio.bootstrap.admin.phone=你的管理员手机号
   studio.bootstrap.admin.password=你的管理员密码
   studio.agent.url=http://127.0.0.1:8000
   studio.agent.internal-token=你生成的内部通信令牌
   ```

3. 创建 Python 环境与配置文件：

   ```sh
   python3 -m venv agent-service/.venv
   agent-service/.venv/bin/python -m pip install -r agent-service/requirements.lock
   cp agent-service/.env.example agent-service/.env
   ```

   用 `openssl rand -hex 32` 生成内部通信令牌，将同一值填入 Java 的 `studio.agent.internal-token` 和 Python 的 `AGENT_INTERNAL_TOKEN`。将 `AGENT_DATABASE_URL` 改为自己的 PostgreSQL 连接串，密码中的 URL 特殊字符需要编码。用下面的命令生成单独的模型密钥加密材料，将输出填入 `AGENT_ENCRYPTION_KEY`，妥善保存以便以后读取已配置的模型密钥。

   ```sh
   agent-service/.venv/bin/python -c 'from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())'
   ```

4. 准备 RocketMQ 5.3.2。仓库提供本机安装脚本，需要 `curl`、`unzip` 和 Java。脚本校验下载包并生成本机 MQ 凭据。然后启动 Agent：

   ```sh
   node scripts/setup-mq.mjs
   node scripts/start-mq.mjs
   node scripts/start-agent.mjs
   ```

5. 在另一个终端打包并启动 Java。构建脚本会把三端静态资源打进 JAR。也可用 `MAVEN_CMD` 指定 Maven 可执行文件路径。

   ```sh
   (cd frontend && npm ci)
   node scripts/build.mjs
   (cd backend && java -jar ../release/studio.jar)
   ```

6. 打开管理端的「见微助手 → 模型配置」，填入自己的 DeepSeek 和百炼凭据，分别选择语言模型与向量模型。三端共用管理端保存的配置。需要在开发时即时查看前端改动，可另外运行 `cd frontend && npm run dev`，通过 5173 访问；开发服务器将业务请求代理至 8080。

## 测试

- 前端：在 `frontend/` 执行 `node --experimental-vm-modules --test tests/*.test.mjs` 和 `npm run build`。
- Java：在 `backend/` 执行 `mvn verify`。要运行真实数据库流程测试，配置独立空测试库、测试用 Redis，再设置 `RUN_WORKFLOW_IT=true`；缓存测试设置 `RUN_CACHE_IT=true` 并准备 `redis-server`，或用 `REDIS_TEST_BIN` 指定路径。MQ 进度快照测试通过 `REDIS_IT_PORT` 选择本机测试 Redis。
- Agent：为测试创建独立的 `shiguang_agent_test` PostgreSQL 库并启用 pgvector。测试账号需有建 schema 权限。在 `agent-service/` 配置测试连接、内部令牌和 Fernet 密钥，保留空白的模型 API Key，执行 `.venv/bin/python -m pytest -q`。模型调用使用测试替身。
- `scripts/` 还包含浏览器业务验收和并发压测脚本，使用独立的可丢弃环境。运行浏览器脚本需安装 Playwright 与 Chromium，并提供该演示库初始化时设置的 `STUDIO_SEED_PASSWORD`。

## 许可

项目源码使用 [MIT 许可证](LICENSE)。第三方依赖遵循各自的许可证。

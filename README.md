# CampusLostHub

校园失物招领平台后端项目，覆盖物品发布、认领审核、站内沟通、向量检索与 AI 问答能力。

## 项目亮点

- 失物招领业务闭环：发布、检索、认领、审核、完结全链路。
- 自然语言找物：基于 text_embedding + pgvector 的文本相似度检索；以图搜物基于 image_embedding + CLIP（需启用 `lost-hub.clip.enabled`）。
- 实时消息系统：WebSocket(STOMP) 实时聊天 + REST 兜底发送。
- 可用性优化：Redis 缓存（列表/详情/空值）+ 缓存失效策略 + 权限校验。

## 技术栈

- Java 17, Spring Boot 3
- Spring Web / Validation / WebSocket / WebFlux
- MyBatis-Plus
- PostgreSQL + pgvector
- Redis
- JWT（用户与管理员双密钥）
- LangChain4j（OpenAI compatible）
- OSS（阿里云对象存储）
- OpenAPI/Swagger

## 主要模块

- 用户与权限：注册登录、JWT 鉴权、角色区分（用户/管理员）
- 物品管理：发布、更新、删除、完结、列表、详情、图片上传
- 向量搜索：自然语言文本检索（`text_embedding` 1024 维 / Qwen）；图片检索（`image_embedding` 512 维 / CLIP，见 `PYTHON_CLIP_SERVICE.md`）
- 认领流程：发起认领（仅未匹配物品）、管理员审核（通过后置为已匹配）
- 站内消息：会话列表、历史消息、已读状态、实时推送
- AI 对话：SSE 流式问答接口（`/ask/chat`）

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.9+
- PostgreSQL（含 pgvector 扩展；执行 `sql/V2_dual_embedding.sql`，`image_embedding` 为 512 维）
- Python CLIP 服务（**图片向量必需**；说明见 [`PYTHON_CLIP_SERVICE.md`](PYTHON_CLIP_SERVICE.md)）
- Redis

### 2. 配置文件

`application.yml` 已包含主配置，敏感配置通过 `application-secret.yml` 注入。  
项目会自动尝试加载：`optional:application-secret.yml`。

可按以下示例创建 `src/main/resources/application-secret.yml`：

```yaml
lost-hub:
  db:
    postgresql:
      username: your_db_username
      password: your_db_password
  jwt:
    user-secret: your_user_jwt_secret
    admin-secret: your_admin_jwt_secret
    expire-hours: 72
  ai:
    api-key: your_ai_api_key
  clip:
    enabled: true
    base-url: http://localhost:8000
    dimension: 512
  aliyun:
    access-key-id: your_oss_key
    access-key-secret: your_oss_secret
    end-point: oss-cn-hangzhou.aliyuncs.com
    region: cn-hangzhou
    bucket-name: your_bucket
```

> **CLIP 联调**：上传物品图片与图搜均要求 `lost-hub.clip.enabled=true`，且 Python 服务（默认 `http://localhost:8000`）可访问 OSS 公网 URL。默认 `application.yml` 中 `clip.enabled` 为 `false`，请在 secret 中开启。

### 3. 启动顺序建议

1. PostgreSQL + Redis
2. Python CLIP：`uvicorn main:app --host 0.0.0.0 --port 8000`（见 `PYTHON_CLIP_SERVICE.md`）
3. Java：`mvn spring-boot:run`

### 4. 启动项目

```bash
mvn spring-boot:run
```

默认端口：`8080`（Java）、`8000`（CLIP）

## 接口文档

- Swagger UI: [http://localhost:8080/api/doc.html](http://localhost:8080/api/doc.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- 详细接口说明：`API.md`

## 目录参考

- `src/main/java/com/hub/controller`：接口层
- `src/main/java/com/hub/service`：业务层
- `src/main/java/com/hub/mapper`：数据访问层
- `src/main/resources/application.yml`：主配置
- `API.md`：接口清单与请求示例
- [`PYTHON_CLIP_SERVICE.md`](PYTHON_CLIP_SERVICE.md)：Python CLIP 图片向量服务完整代码与联调说明
- [`CONCURRENCY.md`](CONCURRENCY.md)：并发、限流、验证码与 CLIP 保护方案（演进指南）

## 说明

- 本项目当前后端为主，前端可按 `API.md` 对接。
- 若用于面试展示，建议重点讲：向量检索、消息兜底链路、缓存一致性策略。

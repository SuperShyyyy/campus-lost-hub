# CampusLostHub — 校园失物招领平台

基于 Spring Boot 的全栈校园失物招领平台，支持 AI 语义匹配、图片向量检索、实时聊天与智能问答。

## 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3 |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL |
| 缓存 | Redis |
| 认证 | JWT（tokenVersion + sessionId + 封禁检查） |
| 存储 | 阿里云 OSS |
| AI | LangChain4j + DeepSeek V4 / Qwen 3.7（阿里云 MAAS 北京节点） |
| 向量检索 | pgvector |
| 图片嵌入 | DashScope qwen2.5-vl-embedding (512维) |
| 接口文档 | SpringDoc OpenAPI (Swagger) |
| 实时通信 | STOMP over WebSocket |
| 流式输出 | SSE (SseEmitter + Flux) |

## 快速开始

```bash
# 1. 配置环境变量
export ALIYUN_AK=xxx
export ALIYUN_SK=xxx
export REDIS_HOST=localhost
export DB_URL=jdbc:postgresql://localhost:5432/campus_lost_hub

# 2. 启动
mvn spring-boot:run -Dspring.profiles.active=dev

# 3. 查看接口文档
open http://localhost:8080/api/doc.html
```

## 项目结构

```
src/main/java/com/hub/
├── CampusLostHubApplication.java    # 启动类
├── cache/                           # Redis 缓存层
├── client/                          # 外部 API 客户端
├── common/                          # 通用类 (Result, 常量)
├── config/                          # 配置 (OSS, JWT, AI, 安全)
├── controller/                      # REST 控制器
├── domain/
│   ├── dto/request/                 # 请求 DTO
│   ├── dto/response/                # 响应 DTO
│   ├── po/                          # 数据库实体
│   ├── repository/                  # 向量存储
│   └── vo/                          # 视图对象
├── exception/                       # 全局异常处理
├── mapper/                          # MyBatis Mapper
├── security/                        # JWT 认证、限流、封禁检查
└── service/                         # 业务服务
    └── impl/
```

## 核心功能

- **AI 语义匹配** — 基于 text_embedding 余弦相似度的物品搜索，支持自然语言查询
- **图片向量检索** — CLIP 模型图片嵌入 + pgvector，以图搜物
- **AI 智能问答** — LangChain4j 多模型路由，SSE 流式输出
- **实时聊天** — STOMP over WebSocket，失主与拾主即时沟通
- **JWT 状态管理** — tokenVersion 失效机制 + sessionId 单设备登录互踢
- **限流保护** — Redis ZSET + Lua 滑动窗口，60s 窗口分级限流（搜索 10/5 次、聊天 20 次等）

## 接口概览

| 模块 | 路径 | 说明 |
|------|------|------|
| 用户 | `/api/user` | 注册、登录、资料、登出、改密 |
| 管理员 | `/api/admin` | 审核认领、封禁用户 |
| 物品 | `/api/item` | 发布、搜索、图片上传、列表 |
| 认领 | `/api/claim` | 发起认领、查看记录 |
| AI 问答 | `/api/ask` | SSE 流式对话、记忆管理 |
| 聊天 | `/api/chat` | 会话列表、消息、已读 |

详细接口文档见 [api_doc.md](文档/api_doc.md)

## Redis 键设计

| 键 | 说明 | TTL |
|----|------|-----|
| `losthub:user:ban:{userId}` | 用户封禁标记 | 与 JWT 一致 |
| `losthub:user:tokenVersion:{userId}` | Token 版本号（INCR，无 TTL） | 持久化 |
| `losthub:user:session:{userId}` | 当前活跃会话 ID（单设备控制） | 与 JWT 一致 |
| `losthub:user:me:{userId}` | 用户信息缓存 | 5min |
| `losthub:item:list:*` | 物品列表缓存 | 2min |
| `losthub:item:detail:{id}` | 物品详情缓存 | 5min |
| `losthub:rate:*` | 滑动窗口限流计数 (ZSET) | 60s+1s |

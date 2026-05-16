# CampusLostHub 接口文档

## 1. 基础信息

- **Base URL**: `http://localhost:8080`
- **Swagger**: `http://localhost:8080/api/doc.html`
- **接口前缀**:
  - 业务接口: `/api/**`
  - AI 对话接口: `/ask/**`
- **统一响应结构**:

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

## 2. 认证说明

- 用户登录、管理员登录成功后，返回 `token`
- 受保护接口请在请求头传:
  - `Authorization: Bearer <token>`
- 无需登录接口:
  - `POST /api/user/register`
  - `POST /api/user/login`
  - `POST /api/admin/login`
  - `GET /api/item/list`
  - `GET /api/item/{id}`
  - `POST /api/item/search`
  - `GET /ask/chat`（AI 对话）

## 3. 前端“我的”导航接口整理

- 你的前端导航栏「我的」建议只放 3 个入口，且分别调用不同 controller 的接口：
  - 个人信息：`GET /api/user/me`（`UserController`）
  - 我的认领记录（分页）：`GET /api/claim/my?page=1&size=10`（`ClaimController`）
  - 我的发布（分页）：`GET /api/item/my?page=1&size=10`（`ItemController`）
- 消息功能建议放单独导航（不放在“我的”里）：
  - 会话列表：`GET /api/chat/sessions`
  - 进入会话后拉取历史：`GET /api/chat/sessions/{sessionId}/messages`

## 4. 状态码与枚举

- `code` 常见值:
  - `200` 成功
  - `400` 参数错误
  - `401` 未授权
  - `403` 禁止访问
  - `404` 资源不存在
  - `500` 服务器异常
- `item.type`:
  - `0` 丢失（LOST）
  - `1` 拾到（FOUND）
- `item.status`:
  - `0` 未匹配
  - `1` 已匹配
  - `2` 已完成
- `claim.status`:
  - `0` 待审核
  - `1` 已通过
  - `2` 已拒绝
- `chat.msgType`:
  - `0` 文本消息
- `user.status`:
  - `0` 正常
  - `1` 已封禁

## 5. 用户接口（`/api/user`）

### 4.1 注册

- **URL**: `POST /api/user/register`
- **鉴权**: 否
- **请求体**:

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

- **说明**:
  - `username`: 必填，最大 50 字符
  - `password`: 必填，长度 6-100

### 4.2 登录

- **URL**: `POST /api/user/login`
- **鉴权**: 否
- **请求体**:

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

- **返回 data**:

```json
{
  "token": "jwt-token"
}
```

### 4.3 当前用户信息

- **URL**: `GET /api/user/me`
- **鉴权**: 用户 token
- **返回 data**:

```json
{
  "id": 1,
  "username": "zhangsan",
  "avatar": "https://example.com/a.png",
  "status": 0,
  "createdAt": "2026-04-20T10:00:00"
}
```

### 4.4 更新资料

- **URL**: `PUT /api/user/update`
- **鉴权**: 用户 token
- **请求体**:

```json
{
  "username": "new-name",
  "avatar": "https://example.com/new.png"
}
```

- **说明**:
  - `username` 可选，最大 50
  - `avatar` 可选，最大 255

## 6. 物品接口（`/api/item`）

### 5.1 发布物品

- **URL**: `POST /api/item`
- **鉴权**: 用户 token
- **Content-Type**: `application/json`
- **请求体**:

```json
{
  "type": 0,
  "title": "黑色雨伞",
  "description": "教学楼 A203 门口丢失",
  "location": "A203"
}
```

- **返回 data**: 新建物品 ID（`Long`）
- **说明**:
  - 该接口只负责创建物品记录，不直接上传图片
  - 前端拿到返回的 `itemId` 后，可继续调用“上传物品图片”接口补传单张图片

### 5.2 更新已发布物品

- **URL**: `PUT /api/item/{id}`
- **鉴权**: 用户 token（仅发布者可更新）
- **Content-Type**: `application/json`
- **路径参数**:
  - `id`: 物品 ID
- **请求体**: 与「发布物品」相同（`type`、`title`、`description`、`location` 字段含义与校验一致）

```json
{
  "type": 0,
  "title": "黑色雨伞",
  "description": "教学楼 A203 门口丢失",
  "location": "A203"
}
```

- **返回 data**: 无（`data` 为 `null` 或空对象，以实际 `Result` 包装为准）
- **说明**:
  - 用于修改已发布物品的文案与类型，服务端会重新计算并写入向量检索用 embedding
  - 不修改 `status`、不替换图片；换图请仍使用「上传物品图片」

### 5.3 上传物品图片

- **URL**: `POST /api/item/{id}/image`
- **鉴权**: 用户 token（仅发布者可上传或覆盖）
- **Content-Type**: `multipart/form-data`
- **路径参数**:
  - `id`: 物品 ID
- **表单参数**:
  - `file`: 图片文件，必填，仅支持图片类型
- **返回 data**: 图片 URL（`String`）
- **示例**:

```bash
curl -X POST "http://localhost:8080/api/item/1/image" \
  -H "Authorization: Bearer <token>" \
  -F "file=@C:/Users/zhao/Pictures/item.jpg"
```

- **完整响应示例**:

```json
{
  "code": 200,
  "msg": "success",
  "data": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/2026/04/xxxx-xxxx.jpg"
}
```

- **说明**:
  - 该接口用于“发布成功后再上传图片”
  - 单次只支持 1 张图片
  - 若同一物品重复上传，会直接覆盖数据库中的 `imageUrl`

### 5.4 删除物品

- **URL**: `DELETE /api/item/{id}`
- **鉴权**: 用户 token（仅发布者可删除）
- **路径参数**:
  - `id`: 物品 ID

### 5.5 完结物品

- **URL**: `PUT /api/item/{id}/complete`
- **鉴权**: 用户 token（仅发布者可操作）
- **路径参数**:
  - `id`: 物品 ID
- **返回 data**: 无（`data` 为 `null` 或空对象，以实际 `Result` 包装为准）
- **说明**:
  - 仅支持 `status=1(已匹配)` 的物品完结为 `status=2(已完成)`
  - 非发布者调用会返回 `403`
  - 状态不满足时返回 `400`

### 5.6 物品列表（分页）

- **URL**: `GET /api/item/list`
- **鉴权**: 否
- **查询参数**:
  - `type` 可选：`0/1`
  - `status` 可选：`0/1/2`
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `10`，范围 `1-100`
- **返回 data**:

```json
{
  "records": [
    {
      "id": 1,
      "userId": 2,
      "type": 0,
      "title": "黑色雨伞",
      "description": "教学楼 A203 门口丢失",
      "location": "A203",
      "imageUrl": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/2026/04/item.jpg",
      "status": 0,
      "createdAt": "2026-04-20T09:00:00",
      "updatedAt": "2026-04-20T09:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 5.7 物品向量搜索

- **URL**: `POST /api/item/search`
- **鉴权**: 否
- **Content-Type**: `application/json`
- **请求体**:

```json
{
  "query": "昨天下午在图书馆三楼丢了一个黑色书包，里面有校园卡",
  "page": 1,
  "size": 10,
  "minScore": 0.6
}
```

- **说明**:
  - `query`：必填，支持自然语言描述，建议前端直接传用户原始输入
  - `page`：默认 `1`，只支持 `1-2`
  - `size`：默认 `10`，最大 `10`
  - `minScore`：默认 `0.6`，范围 `0.5-0.8`，值越高匹配越严格
  - 最多返回前 2 页，共 20 条
  - 返回结果按相似度从高到低排序
  - 返回记录中会保留 `type`，前端可据此区分“丢失 / 招领”
- **请求体字段**:
  - `query: string`
  - `page?: number`
  - `size?: number`
  - `minScore?: number`
- **完整响应示例**:

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 2,
        "type": 0,
        "title": "黑色书包",
        "description": "昨天下午在图书馆三楼附近丢失",
        "location": "图书馆三楼",
        "imageUrl": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/2026/04/item.jpg",
        "status": 0,
        "createdAt": "2026-04-20T09:00:00",
        "updatedAt": "2026-04-20T09:00:00",
        "score": 0.78,
        "distance": 0.22
      }
    ],
    "total": 12,
    "page": 1,
    "size": 10
  }
}
```

- **响应字段说明**:
  - `data.records[].type`：`0` 表示丢失，`1` 表示招领
  - `data.records[].status`：`0` 未匹配，`1` 已匹配，`2` 已完成
  - `data.records[].imageUrl`：物品图片地址，未上传时可能为 `null`
  - `data.records[].score`：匹配度，范围通常在 `0-1`，越大越相似
  - `data.records[].distance`：向量距离，越小越相似
  - `data.total`：当前阈值条件下的命中总数，最大返回统计为 `20`
- **前端接入建议**:
  - search 页面首次搜索时可直接传 `page=1,size=10,minScore=0.6`
  - 点击下一页时传 `page=2`
  - 若结果过少，可尝试把 `minScore` 从 `0.6` 调低到 `0.5`
  - 若结果噪音较多，可尝试把 `minScore` 从 `0.6` 调高到 `0.7` 或 `0.8`

### 5.8 我的发布（分页）

- **URL**: `GET /api/item/my`
- **鉴权**: 用户 token
- **查询参数**:
  - `type` 可选：`0/1`
  - `status` 可选：`0/1/2`
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `10`，范围 `1-100`
- **返回 data**:

```json
{
  "records": [
    {
      "id": 101,
      "userId": 2,
      "type": 0,
      "title": "黑色水杯",
      "description": "图书馆二楼自习区遗失",
      "location": "图书馆二楼",
      "imageUrl": "https://example.com/item-101.jpg",
      "status": 0,
      "createdAt": "2026-04-22T10:00:00",
      "updatedAt": "2026-04-22T10:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 5.9 物品详情

- **URL**: `GET /api/item/{id}`
- **鉴权**: 否
- **路径参数**:
  - `id`: 物品 ID
- **返回 data 示例**:

```json
{
  "id": 1,
  "userId": 2,
  "type": 0,
  "title": "黑色雨伞",
  "description": "教学楼 A203 门口丢失",
  "location": "A203",
  "imageUrl": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/2026/04/item.jpg",
  "status": 0,
  "createdAt": "2026-04-20T09:00:00",
  "updatedAt": "2026-04-20T09:00:00"
}
```

## 7. 认领接口（`/api/claim`）

### 7.1 发起认领

- **URL**: `POST /api/claim`
- **鉴权**: 用户 token
- **请求体**:

```json
{
  "itemId": 1,
  "message": "请联系我，物品特征是..."
}
```

- **返回 data**: 认领记录 ID（`Long`）
- **说明**:
  - 仅允许对 `status=0(未匹配)` 的物品发起认领
  - `status=1/2`（已匹配/已完成）会返回 `400`

### 7.2 我的认领记录（分页）

- **URL**: `GET /api/claim/my`
- **鉴权**: 用户 token
- **查询参数**:
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `10`，范围 `1-100`
- **返回 data**:

```json
{
  "records": [
    {
      "id": 11,
      "itemId": 101,
      "userId": 9,
      "message": "请联系我，物品细节我可以提供",
      "status": 0,
      "createdAt": "2026-04-22T15:30:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 7.3 认领详情

- **URL**: `GET /api/claim/{id}`
- **鉴权**: 用户 token（仅认领人可查看）
- **路径参数**:
  - `id`: 认领记录 ID

## 8. 管理员接口（`/api/admin`）

### 8.1 管理员登录

- **URL**: `POST /api/admin/login`
- **鉴权**: 否
- **请求体**:

```json
{
  "username": "admin",
  "password": "123456"
}
```

- **返回 data**:

```json
{
  "token": "admin-jwt-token"
}
```

### 8.2 认领审核列表（分页）

- **URL**: `GET /api/admin/claims`
- **鉴权**: 管理员 token
- **查询参数**:
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `10`，范围 `1-100`
- **返回 data**: `PageResult<ClaimVo>`

### 8.3 审核认领

- **URL**: `PUT /api/admin/claim/audit/{id}`
- **鉴权**: 管理员 token
- **路径参数**:
  - `id`: 认领记录 ID
- **请求体**:

```json
{
  "status": 1
}
```

- **说明**:
  - `status=1` 通过
  - `status=2` 拒绝
  - 仅当目标物品处于 `status=0(未匹配)` 时允许审核通过；否则返回 `400`

### 8.4 封禁用户

- **URL**: `PUT /api/admin/user/ban/{userId}`
- **鉴权**: 管理员 token
- **路径参数**:
  - `userId`: 用户 ID

## 9. 消息接口（`/api/chat`）

### 9.1 会话列表（分页）

- **URL**: `GET /api/chat/sessions`
- **鉴权**: 用户 token
- **查询参数**:
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `10`，范围 `1-100`
- **返回 data**:

```json
{
  "records": [
    {
      "sessionId": 12,
      "itemId": 1001,
      "ownerUserId": 2,
      "contactUserId": 9,
      "peerUserId": 9,
      "lastMessagePreview": "你好，我想确认下是不是我的雨伞",
      "lastMessageAt": "2026-04-22T20:00:00",
      "unreadCount": 2
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

### 9.2 会话消息历史（分页）

- **URL**: `GET /api/chat/sessions/{sessionId}/messages`
- **鉴权**: 用户 token（仅会话参与者可访问）
- **路径参数**:
  - `sessionId`: 会话 ID
- **查询参数**:
  - `page` 默认 `1`，最小 `1`
  - `size` 默认 `20`，范围 `1-100`
- **返回 data**: `PageResult<ChatMessageVo>`

### 9.3 发送消息（REST 兜底）

- **URL**: `POST /api/chat/send`
- **鉴权**: 用户 token
- **Content-Type**: `application/json`
- **请求体**:

```json
{
  "itemId": 1001,
  "receiverUserId": 2,
  "content": "你好，我在图书馆看到你发布的信息，方便核对一下细节吗？"
}
```

- **返回 data**: `ChatMessageVo`
- **说明**:
  - 普通用户可向 `item` 发布者发起会话；若会话不存在会自动创建。
  - 发布者可对已联系过该 `item` 的联系人回复消息。
  - 消息会同步广播到该会话主题（见 WebSocket 章节）。

### 9.4 标记会话消息已读

- **URL**: `PUT /api/chat/sessions/{sessionId}/read`
- **鉴权**: 用户 token（仅会话参与者可操作）
- **路径参数**:
  - `sessionId`: 会话 ID
- **返回**: 成功返回 `code=200`

## 10. WebSocket 接入说明（STOMP）

### 10.1 连接信息

- **握手地址**: `ws://localhost:8080/ws`
- **协议**: STOMP
- **鉴权**: 在 `CONNECT` 帧携带请求头：
  - `Authorization: Bearer <token>`
- **应用前缀**: `/app`
- **订阅前缀**: `/topic`

### 10.2 发送消息

- **目的地**: `/app/chat.send`
- **消息体**（JSON）:

```json
{
  "itemId": 1001,
  "receiverUserId": 2,
  "content": "你好，我想认领这件物品"
}
```

### 10.3 订阅消息

- **订阅主题**: `/topic/chat.session.{sessionId}`
- **服务端广播体**: `ChatMessageVo`

### 10.4 敏感信息拦截（禁止传入）

- 以下内容会被拒绝发送（REST 与 WebSocket 一致）：
  - 密码/口令/token/api-key/secret/验证码等关键字
  - 手机号模式
  - 身份证号模式
  - 银行卡号模式
- 命中后返回：
  - HTTP 接口：`400`，`msg=消息包含敏感信息，发送失败`
  - WebSocket：返回错误帧，错误信息同上

## 10. AI 对话接口（`/ask`）

### 8.1 流式聊天

- **URL**: `GET /ask/chat`
- **鉴权**: 否
- **Content-Type**: `text/event-stream`
- **查询参数**:
  - `memoryId` 必填，建议使用用户 ID / 会话 ID 等唯一值
  - `message` 必填，用户问题
- **响应**:
  - 服务端流式返回 AI 生成文本片段（`Flux<String>`）
- **示例**:

```bash
curl -N "http://localhost:8080/ask/chat?memoryId=u1-s1&message=我丢了校园卡怎么办"
```

## 11. 备注

- AI 对话历史使用 Redis 存储，单个会话默认保留最近 20 条消息。
- 当前 AI 模型配置在 `application.yml` 的 `langchain4j.open-ai.*` 下。

# Level 3 - 核心业务链路分析

本文从代码执行路径出发，按时序说明系统“具体如何一步步运作”。  
内容仅描述当前实现逻辑，不引入推测性内容。

## 1. 场景 A：外部接口发送短信（`/sms/single_send`）

### 1.1 入口与预处理

入口类与方法：

1. `beacon-api` - `SmsController.singleSend`
2. 请求路径：`POST /sms/single_send`

主要处理动作：

1. 参数校验（`SingleSendForm`）。
2. 解析请求真实 IP（支持按 `headers` 配置顺序读取请求头）。
3. 组装 `StandardSubmit`。
4. 执行接入校验链：`CheckFilterContext.check(submit)`。
   - 执行顺序来自配置项 `filters`（默认值 `apikey,ip,sign,template`）。
   - 过滤器通过 Spring Bean 名称映射执行。
5. 生成 `sequenceId`（雪花算法）并写入 `sendTime`。
6. 投递到 `sms_pre_send_topic` 队列。

### 1.2 策略链执行

入口类与方法：

1. `beacon-strategy` - `PreSendListener.listen`
2. 监听队列：`sms_pre_send_topic`

执行机制：

1. 读取客户策略串：`client_business:{apikey}` 的 `clientFilters` 字段。
2. `StrategyFilterContext` 按配置顺序逐个执行策略过滤器。
3. 其中 `black` 会兼容映射到 `blackGlobal` + `blackClient` 两个过滤器。

当前可见过滤器实现包括：

1. 黑名单：`blackGlobal`、`blackClient`
2. 敏感词：`dirtyword`、`dfaDirtyWord`、`hutoolDfaDirtyWord`
3. 费用与限流：`fee`、`limitOneHour`
4. 号段与携号转网：`phase`、`transfer`
5. 路由：`route`

路由阶段（`RouteStrategyFilter`）关键动作：

1. 从 Redis 读取客户绑定通道集合：`client_channel:{clientId}`。
2. 按权重排序选择可用通道，并校验通道可用性与运营商匹配关系。
3. 补齐 `submit.channelId`、`submit.srcNumber`。
4. 动态声明队列 `sms_gateway_topic_{channelId}`。
5. 投递 `StandardSubmit` 到该通道队列。

### 1.3 网关下发（CMPP）

入口类与方法：

1. `beacon-smsgateway` - `SmsGatewayListener.consume`
2. 监听队列：`${gateway.sendtopic}`

处理步骤：

1. 从 `StandardSubmit` 读取 `srcNumber`、`mobile`、`text`。
2. 构造 `CmppSubmit` 报文（含 sequence）。
3. `CMPPSubmitRepoMapUtil.put(sequence, submit)` 暂存映射关系。
4. 通过 `NettyClient.submit(cmppSubmit)` 发送到 CMPP 连接。
5. RabbitMQ 消息 `ack`。

## 2. 场景 B：后台批量发送短信（`/sys/sms/save`）

入口链路：

1. `beacon-webmaster` - `SysSmsController.save/update`
2. `SmsManageServiceImpl.doSend`

批量处理步骤：

1. 校验 `clientId/mobile/content/state`。
2. 解析手机号列表（支持换行、逗号、分号、空白分隔），并去重。
3. 校验客户权限范围（普通角色仅可操作授权客户，ROOT 角色可全量）。
4. 构造内部发送请求 `ApiInternalSingleSendForm`：
   - `apikey` 来自客户配置
   - `uid` 由后台拼接生成
   - `realIp` 来自客户 IP 配置首项（默认 `127.0.0.1`）
5. 逐号码调用 `beacon-api` 内部接口：`POST /sms/internal/single_send`。

`/sms/internal/single_send` 当前逻辑：

1. 参数校验 + 可选 `X-Internal-Token` 校验。
2. 通过 cache 解析 `apikey -> clientId`。
3. 构造 `StandardSubmit` 并直接入 `sms_pre_send_topic`。
4. 后续链路与场景 A 相同（进入策略 -> 网关 -> 日志/回执/回调）。

## 3. 场景 C：提交响应、状态报告、日志与回调

### 3.1 CMPP 提交响应（`CmppSubmitResp`）分支

入口类与方法：

1. `beacon-smsgateway` - `CMPPHandler.channelRead0`
2. 消息类型：`CmppSubmitResp`

处理步骤：

1. 投递任务到线程池 `cmppSubmitPool`。
2. `SubmitRepoRunnable.run`：
   - 按 sequence 从 `CMPPSubmitRepoMapUtil` 取出原 `StandardSubmit`。
   - 若运营商提交结果失败：设置 `reportState=FAIL`、写入 `errorMsg`。
   - 若成功：复制为 `StandardReport`，以 `msgId` 存入 `CMPPDeliverMapUtil`。
   - 无论成功或失败，都会发送 `submit` 到 `sms_write_log_topic`。

### 3.2 CMPP 状态报告（`CmppDeliver`）分支

入口类与方法：

1. `beacon-smsgateway` - `CMPPHandler.channelRead0`
2. 消息类型：`CmppDeliver` 且 `registered_Delivery == 1`

处理步骤：

1. 投递任务到线程池 `cmppDeliverPool`。
2. `DeliverRunnable.run`：
   - 按 `msgId` 从 `CMPPDeliverMapUtil` 取出 `StandardReport`。
   - 依据 `stat` 设置最终 `reportState`（`DELIVRD` 为成功，否则失败并写错误信息）。
   - 查询客户 `isCallback` 与 `callbackUrl`：
     - 满足条件时发送到 `sms_push_report_topic`。
   - 同时发送到 `sms_gateway_normal_exchange`，进入日志状态更新链路。

### 3.3 搜索模块写入与状态更新

写入链路：

1. `SmsWriteLogListener` 监听 `sms_write_log_topic`。
2. 写入 ES 索引：`sms_submit_log_{yyyy}`，文档 ID=`sequenceId`。
3. 同步写入 `sendTimeMillis` 字段用于时间范围查询。

更新链路：

1. `SmsUpdateLogListener` 监听 `sms_gateway_dead_queue`。
2. 调用 `searchService.update(index, id, {reportState})`。
3. `ElasticsearchServiceImpl.update`：
   - 文档存在：执行更新。
   - 文档不存在且 `reUpdate=false`：设置 `reUpdate=true`，重新投递到 `sms_gateway_normal_queue`。
   - `sms_gateway_normal_queue` 配置了 `x-message-ttl=10000`，超时后进入死信队列再次消费。

### 3.4 推送模块客户回调

入口类与方法：

1. `PushReportListener.consume` 监听 `sms_push_report_topic`
2. `PushReportListener.delayedConsume` 监听 `push_delayed_queue`

处理逻辑：

1. `pushReport(report)` 用 `RestTemplate` 向 `http://{callbackUrl}` 发送 JSON。
2. 返回字符串等于 `SUCCESS` 视为成功。
3. 失败时执行重试逻辑：
   - `resendCount + 1`
   - 通过 `push_delayed_exchange` 投递延迟消息
   - 延迟数组：`[0, 15000, 30000, 60000, 300000]`

## 4. 场景 D：后台检索与统计

### 4.1 列表检索

调用链：

1. `beacon-webmaster` - `SearchController.list`（`GET /sys/search/list`）
2. `SearchClient.findSmsByParameters` -> `beacon-search` `POST /search/sms/list`
3. `ElasticsearchServiceImpl.findSmsByParameters`

检索条件（当前实现）：

1. `content`（全文匹配 + 高亮）
2. `mobile`（前缀匹配）
3. `starttime/stoptime`（基于 `sendTimeMillis`）
4. `clientID`（单值或列表）

### 4.2 状态统计

调用链：

1. `beacon-webmaster` - `EchartsController`（`/sys/echarts/pie|line|bar`）
2. `EchartsQueryService.queryStateCountWithPermission`
3. `SearchClient.countSmsState` -> `beacon-search` `POST /search/sms/countSmsState`
4. `ElasticsearchServiceImpl.countSmsState`（terms 聚合 `reportState`）

返回结构统一为：

1. `waiting`
2. `success`
3. `fail`

## 5. 一条短信的关键时序（简表）

| 序号 | 系统动作 | 载体 |
| --- | --- | --- |
| 1 | API 接收请求并组装提交体 | `StandardSubmit` |
| 2 | API 投递预发送队列 | `sms_pre_send_topic` |
| 3 | 策略模块消费并执行策略链 | `StandardSubmit` |
| 4 | 路由到通道队列 | `sms_gateway_topic_{channelId}` |
| 5 | 网关消费并下发 CMPP | `CmppSubmit` |
| 6 | 收到提交响应后写发送日志 | `sms_write_log_topic` |
| 7 | 收到状态报告后形成回执 | `StandardReport` |
| 8 | 回执进入状态更新交换机 | `sms_gateway_normal_exchange` |
| 9 | 搜索模块更新 ES 文档状态 | `reportState` |
| 10 | 如客户开启回调则进入推送队列 | `sms_push_report_topic` |
| 11 | 推送模块调用客户回调地址 | HTTP JSON |


# beacon-push 模块详细分析

更新时间：2026-02-26  
适用仓库：`beacon-cloud`

---

## 1. 模块定位

`beacon-push` 是短信平台的“状态回执推送模块”，负责把内部状态报告（`StandardReport`）回调给客户系统，并在失败时执行延迟重试。

在整体链路中的位置：

`beacon-strategy` / `beacon-smsgateway` -> `SMS_PUSH_REPORT` -> `beacon-push` -> `客户回调地址`  
`beacon-push`(失败重试) -> `push_delayed_exchange` -> `push_delayed_queue` -> `beacon-push`

---

## 2. 模块结构

源码目录：`beacon-push/src/main/java/com/cz/push`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `PushStarterApp` | SpringBoot 启动、服务注册 |
| MQ 配置层 | `RabbitMQConfig` | 延迟交换机/队列声明、消息 JSON 转换 |
| MQ 消费层 | `PushReportListener` | 监听实时推送队列与延迟重试队列 |
| HTTP 客户端层 | `RestTemplateConfig` | 回调请求客户端 |

---

## 3. 核心处理流程

## 3.1 实时推送入口（`SMS_PUSH_REPORT`）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`

流程：

1. 监听 `RabbitMQConstants.SMS_PUSH_REPORT`，接收 `StandardReport`。
2. 校验 `callbackUrl`，为空则记录日志并 Ack 结束。
3. 调用 `pushReport(report)` 执行 HTTP 回调。
4. 调用 `isResend(report, flag)`，失败时安排延迟重试。
5. 对原消息 `basicAck`。

## 3.2 延迟重试入口（`push_delayed_queue`）

同文件中的 `delayedConsume(...)`：

1. 监听 `RabbitMQConfig.DELAYED_QUEUE`。
2. 再次执行 `pushReport`。
3. 若失败则继续 `isResend`，直到达到重试上限。
4. `basicAck` 结束当前重试消息。

## 3.3 回调与重试策略

关键实现：

1. 请求方式：`RestTemplate.postForObject("http://" + callbackUrl, body, String.class)`。
2. 成功判定：响应字符串严格等于 `"SUCCESS"`。
3. 重试节奏：`[15s, 30s, 60s, 300s]`（首次实时 + 最多 4 次延迟重试）。
4. 重试计数：`report.resendCount` 递增，达到 `>=5` 停止。

---

## 4. 依赖与配置现状

## 4.1 依赖侧

文件：`beacon-push/pom.xml`

关键依赖：

1. `spring-boot-starter-amqp`：消费推送事件。
2. `spring-boot-starter-web`：提供 `RestTemplate` 及基础 Web 能力。
3. `beacon-common`：`StandardReport`、MQ 常量。
4. Nacos 发现/配置依赖。

## 4.2 配置侧

文件：

1. `beacon-push/src/main/resources/bootstrap.yml`
2. `beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java`

现状：

1. 基础服务名与 Nacos 地址在 `bootstrap.yml`。
2. 延迟交换机使用 `x-delayed-message` 插件机制。
3. 交换机参数为 `fanout` 延迟路由，延迟时间由消息属性 `setDelay(...)` 决定。

---

## 5. 关键机制分析

## 5.1 “实时 + 延迟队列”二段式重试

1. 首次回调由 `SMS_PUSH_REPORT` 触发，失败后转入延迟队列。
2. 延迟队列每次消费仍走同样推送逻辑，形成统一重试闭环。
3. 无需依赖 Rabbit 死信 TTL，重试节奏更可控。

## 5.2 推送成功语义严格

1. 仅接受响应体精确值 `"SUCCESS"`。
2. 客户侧若返回 JSON（例如 `{code:0}`）或大小写不一致，会被判失败。

---

## 6. 主要风险与技术债

## 6.1 回调异常吞掉细节（高风险）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`

1. `pushReport` 内 `catch (RestClientException e) {}` 为空。
2. 失败原因不可观测，不利于定位网络/证书/协议问题。

## 6.2 HTTP 客户端无超时配置（高风险）

文件：`beacon-push/src/main/java/com/cz/push/config/RestTemplateConfig.java`

1. 默认 `RestTemplate` 未设置连接和读取超时。
2. 客户端慢响应会占用消费线程，放大堆积风险。

## 6.3 回调 URL 组装方式脆弱（高风险）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`

1. 强制拼接 `"http://"`，若客户已配置协议头会生成非法 URL。
2. 缺少 URL 白名单/域名校验，存在被误配或滥用风险。

## 6.4 成功判定过于单一（中风险）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`

1. 只认 `"SUCCESS"` 文本，无法兼容更常见的 JSON 回执协议。
2. 易导致“实际上成功但被误判失败”。

## 6.5 延迟交换机持久化策略不完整（中风险）

文件：`beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java`

1. `CustomExchange(..., false, false, ...)` 将交换机声明为非持久化。
2. Broker 重启后可能丢失交换机定义，影响重试链路可用性。

## 6.6 重试日志语义偏差（中风险）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`

1. 成功日志固定输出“第一次推送成功”，无法反映真实重试次数。
2. 影响运维排障与统计准确性。

## 6.7 缺少回调安全机制（中风险）

1. 未看到签名、时间戳、防重放等机制。
2. 客户无法校验回调来源真实性。

## 6.8 自动化测试缺失（中风险）

1. 无 `src/test/java` 覆盖。
2. 重试时序、成功判定、异常路径缺少回归验证。

---

## 7. 与上下游契约关系

## 7.1 上游契约

1. 上游需向 `SMS_PUSH_REPORT` 投递完整 `StandardReport`。
2. `callbackUrl`、`resendCount` 等字段必须可用。

## 7.2 下游契约

1. 客户回调接口建议约定统一响应协议（例如 `{"code":0}` 或固定文本）。
2. 推送模块当前仅按字符串 `"SUCCESS"` 判断，需要与客户严格对齐。

---

## 8. 改造建议（按优先级）

## P0（优先）

1. 为 `RestTemplate` 配置连接/读取超时与连接池，避免线程被长时间阻塞。
2. 完整记录回调失败异常（状态码、超时、DNS、握手错误等）。
3. 回调 URL 规范化：允许 `http/https`，避免硬拼协议，增加白名单校验。
4. 调整重试交换机为持久化资源，确保重启后机制可用。

## P1（中期）

1. 抽象可配置的成功判定策略（文本/JSON/HTTP 状态码组合）。
2. 引入推送安全签名（HMAC + timestamp + nonce）。
3. 增加监控指标：推送成功率、重试次数分布、回调耗时分位。

## P2（长期）

1. 建立“推送事件状态机”与补偿队列，支持人工重放与审计。
2. 构建客户级别回调 SLA 报表，支持差异化重试策略。
3. 输出统一推送 SDK/协议文档，降低对接成本。

---

## 9. 推荐治理指标

1. `push.callback.success.rate`：回调成功率。
2. `push.callback.retry.count`：每条消息重试次数分布。
3. `push.callback.latency.p95/p99`：回调耗时。
4. `push.callback.invalid_url.count`：非法回调地址数量。
5. `push.delayed.queue.depth`：延迟重试队列堆积。

---

## 10. 结论

`beacon-push` 已具备可运行的状态推送与延迟重试能力，链路简洁、易理解。  
当前主要短板在“可靠性和可观测性”：超时控制、异常可见性、成功判定弹性、交换机持久化与安全校验。  
优先完成 P0 项后，该模块可明显降低误重试、线程阻塞和推送失真风险。


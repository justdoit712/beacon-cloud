# RabbitMQ 消息链路与死信队列说明

文档类型：专题说明  
适用对象：开发 / 排障 / 架构理解  
验证基线：代码静态核对 + 仓库现有 README  
关联模块：beacon-api / beacon-strategy / beacon-smsgateway / beacon-search / beacon-push / beacon-monitor / beacon-common  
最后核对日期：2026-04-02

---

## 1. RabbitMQ 在本项目里的总体作用

`beacon-cloud` 里的 RabbitMQ 不是单纯“传消息”的中间件，而是把整条短信链路拆成多个异步阶段的核心总线。

它主要承担 4 类职责：

1. **解耦发送主链路**
   - `beacon-api` 先受理请求，再异步投递给 `beacon-strategy`，避免接口线程同步串行走完整条链路。
2. **隔离不同业务阶段**
   - 策略、网关、搜索、回调分别由不同队列承接，模块之间不直接强耦合调用。
3. **承接异步后处理**
   - 发送日志写 ES、状态回调客户、号段同步等都走 MQ，而不是塞回主发送线程。
4. **实现“延迟后再处理”**
   - 网关状态更新链路使用 `TTL + 死信队列` 做固定延迟。
   - 客户回调重试链路使用 `x-delayed-message` 插件做多档位延迟重试。

---

## 2. 全局拓扑总览

按当前代码可见实现，RabbitMQ 主拓扑可以概括为：

```text
beacon-api
  -> sms_pre_send_topic
  -> beacon-strategy
       -> sms_gateway_topic_{channelId}
       -> sms_write_log_topic
       -> sms_push_report_topic
       -> mobile_area_operator_topic

beacon-smsgateway
  -> sms_write_log_topic
  -> sms_push_report_topic
  -> sms_gateway_normal_exchange
       -> sms_gateway_normal_queue (TTL 10s)
       -> dead-letter -> sms_gateway_dead_exchange
       -> sms_gateway_dead_queue
       -> beacon-search

beacon-push
  <- sms_push_report_topic
  -> push_delayed_exchange
  -> push_delayed_queue
  -> 再次消费并重试客户回调
```

---

## 3. 队列与交换机清单

| 名称 | 类型 | 生产者 | 消费者 | 作用 |
| --- | --- | --- | --- | --- |
| `sms_pre_send_topic` | 队列 | `beacon-api` | `beacon-strategy` | API 受理后的预发送入口 |
| `sms_gateway_topic_{channelId}` | 队列 | `beacon-strategy` | `beacon-smsgateway` | 按通道路由后的网关发送队列 |
| `sms_write_log_topic` | 队列 | `beacon-strategy`、`beacon-smsgateway` | `beacon-search` | 写短信提交日志到 ES |
| `sms_push_report_topic` | 队列 | `beacon-strategy`、`beacon-smsgateway` | `beacon-push` | 向客户系统推送状态报告 |
| `mobile_area_operator_topic` | 队列 | `beacon-strategy` | 当前仓库中未检索到消费者 | 号段补齐后异步同步手机号归属地/运营商信息 |
| `sms_gateway_normal_exchange` | `fanout` 交换机 | `beacon-smsgateway` | `sms_gateway_normal_queue` | 网关状态更新的延迟入口 |
| `sms_gateway_normal_queue` | 队列 | 由 `sms_gateway_normal_exchange` 路由进入，也可被 `beacon-search` 直接重投 | 无直接业务消费方 | TTL 10 秒后转入死信交换机 |
| `sms_gateway_dead_exchange` | `fanout` 交换机 | 由 normal queue 死信转入 | `sms_gateway_dead_queue` | 承接延迟后的状态更新消息 |
| `sms_gateway_dead_queue` | 队列 | `sms_gateway_dead_exchange` | `beacon-search` | 延迟后更新 ES 中短信最终状态 |
| `push_delayed_exchange` | `x-delayed-message` 交换机 | `beacon-push` | `push_delayed_queue` | 客户回调失败后的延迟重试入口 |
| `push_delayed_queue` | 队列 | `push_delayed_exchange` | `beacon-push` | 客户回调延迟重试消费队列 |

补充说明：

1. `sms_gateway_topic_{channelId}` 是运行期动态队列名，`RouteStrategyFilter` 会按通道 ID 声明队列并投递。
2. `beacon-smsgateway` 监听的具体队列来自外部配置 `gateway.sendtopic`。结合代码看，这个值应当对应某个 `sms_gateway_topic_{channelId}`。这是基于代码的**推断**，因为实际配置中心内容不在仓库内。
3. `mobile_area_operator_topic` 在当前仓库里能看到生产者和队列声明，但**未检索到 `@RabbitListener` 消费者**。这说明它可能由仓库外组件消费，或者当前实现尚未补齐。

---

## 4. 主链路里 RabbitMQ 是怎么工作的

## 4.1 API 受理后先入队，再进策略

入口在 `beacon-api/src/main/java/com/cz/api/controller/SmsController.java`。

流程：

1. API 接口组装 `StandardSubmit`。
2. 调用 `rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PRE_SEND, submit, new CorrelationData(...))`。
3. 消息进入 `sms_pre_send_topic`。
4. `beacon-strategy` 的 `PreSendListener` 消费该消息并执行策略链。

这里 RabbitMQ 的作用是：

1. 让接口返回“已受理”，不用同步等待后续所有策略和网关下发完成。
2. 把 API 模块和策略模块隔开，任何一个模块临时抖动不会直接卡住另一个模块的业务线程。

## 4.2 策略通过后按通道路由到网关

关键代码在 `beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java`。

流程：

1. 策略模块根据客户绑定通道、通道可用性、运营商匹配情况选择一个 `channelId`。
2. 拼出队列名：`sms_gateway_topic_{channelId}`。
3. 动态声明该队列。
4. 直接把 `StandardSubmit` 投递到这个队列。

这里 RabbitMQ 的作用是：

1. 把“策略判断”和“网关实际发送”分开。
2. 让每个通道可以拥有独立发送队列。
3. 为后续按通道做限速、隔离、扩缩容保留空间。

## 4.3 网关提交应答后先写发送日志

关键代码在 `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/SubmitRepoRunnable.java`。

流程：

1. 网关收到运营商提交应答 `CmppSubmitResp`。
2. 根据应答结果补全 `StandardSubmit` 的发送状态。
3. 不论提交成功还是失败，都把 `submit` 投递到 `sms_write_log_topic`。
4. `beacon-search` 的 `SmsWriteLogListener` 消费后写入 Elasticsearch。

这里 RabbitMQ 的作用是：

1. 把“网关协议交互”与“日志落 ES”分开。
2. 避免网关线程同步等待 ES 写入。

## 4.4 网关收到最终状态报告后分成两路

关键代码在 `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/DeliverRunnable.java`。

当运营商最终状态报告到达后，网关会做两件事：

1. **客户回调分支**
   - 如果客户开启回调，投递 `sms_push_report_topic`
   - 由 `beacon-push` 消费并调用客户 HTTP 回调地址

2. **搜索状态更新分支**
   - 把 `StandardReport` 投递到 `sms_gateway_normal_exchange`
   - 这条消息不会立刻更新 ES，而是先进入 normal queue，等待 TTL 到期后再进入 dead queue
   - 最后由 `beacon-search` 消费 dead queue 更新短信最终状态

---

## 5. 死信队列在这个项目里到底是干什么的

## 5.1 不是“消息处理失败垃圾桶”，而是“延迟状态更新队列”

很多项目里的死信队列用于承接“消费失败后被拒绝的消息”。  
但在 `beacon-cloud` 里，当前代码可见的这条死信链路更像：

**固定 10 秒延迟后的状态更新通道**

也就是：

1. `DeliverRunnable` 收到运营商状态报告后，不直接让 `beacon-search` 更新 ES。
2. 而是先把 `StandardReport` 发到 `sms_gateway_normal_exchange`。
3. `sms_gateway_normal_queue` 设置了 `x-message-ttl = 10000`，即 10 秒。
4. 消息在 normal queue 里放 10 秒后过期。
5. 过期消息被投递到 `sms_gateway_dead_exchange`。
6. `sms_gateway_dead_queue` 绑定到死信交换机。
7. `beacon-search` 监听死信队列，最终执行 ES 状态更新。

这条链路的本质是：

**利用 TTL + 死信机制，实现“延迟 10 秒后再更新短信最终状态”。**

## 5.2 为什么要延迟 10 秒

从当前代码看，发送日志和状态更新是两条异步支路：

1. `SubmitRepoRunnable` 会把 `StandardSubmit` 写到 `sms_write_log_topic`，由 `beacon-search` 写 ES 文档。
2. `DeliverRunnable` 会把 `StandardReport` 走 normal queue -> dead queue，最后让 `beacon-search` 更新同一条短信文档的 `reportState`。

如果状态更新来得太快，而 ES 里的提交文档还没写进去，就会出现：

1. 搜索模块要更新的文档还不存在。
2. 更新操作失败。

所以这里的 10 秒延迟，实际是在给“写日志入 ES”预留时间窗口。

## 5.3 搜索模块还做了二次重投

`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java` 里有进一步补偿：

1. 如果消费 dead queue 更新状态时，发现 ES 文档还不存在：
   - 且 `report.reUpdate == false`
   - 就把消息重新投递到 `sms_gateway_normal_queue`
2. 由于 normal queue 本身带 TTL + 死信配置，这条消息又会再延迟一次，再次进入 dead queue
3. 第二次如果还找不到文档，则只记录错误日志，不再无限重试

因此，当前代码下死信链路的实际意义是：

1. 第一次延迟状态更新
2. 更新失败时再做一次延迟重试

可以把它理解成：

**“先等一会儿再更新 ES，不行就再等一会儿再更新一次。”**

---

## 6. 延迟交换机和死信队列的区别

本项目同时用了两套“延迟后处理”的方案，它们用途不同。

## 6.1 网关状态更新：用 `TTL + 死信队列`

对应链路：

`sms_gateway_normal_exchange -> sms_gateway_normal_queue(TTL) -> sms_gateway_dead_exchange -> sms_gateway_dead_queue`

特点：

1. 延迟时间固定，当前代码是 10 秒。
2. 目的不是给客户回调重试，而是给 ES 更新腾出时间窗口。
3. 这是一个“系统内部状态同步延迟链路”。

## 6.2 客户回调重试：用 `x-delayed-message`

对应链路：

`push_delayed_exchange -> push_delayed_queue`

关键代码在 `beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`。

特点：

1. 延迟时间不是固定一档，而是多档重试：`0 / 15s / 30s / 60s / 300s`
2. 失败后由 `MessagePostProcessor` 给每条消息设置不同 delay
3. 这是一个“面向外部 HTTP 回调失败重试”的延迟机制

所以：

1. 死信队列解决的是**内部状态更新延迟**
2. 延迟交换机解决的是**客户回调失败重试**

---

## 7. 各模块中 RabbitMQ 的具体职责

## 7.1 `beacon-api`

职责：

1. 把短信请求投递到 `sms_pre_send_topic`
2. 使用 `CorrelationData(sequenceId)` 做发送确认关联

它不消费任何业务队列，只负责把发送请求异步化。

## 7.2 `beacon-strategy`

职责：

1. 消费 `sms_pre_send_topic`
2. 路由到 `sms_gateway_topic_{channelId}`
3. 失败时发送 `sms_write_log_topic`
4. 失败或需要回调时发送 `sms_push_report_topic`
5. 号段补齐成功时发送 `mobile_area_operator_topic`

它是 RabbitMQ 主链路里的“中枢转发站”。

## 7.3 `beacon-smsgateway`

职责：

1. 消费通道发送队列 `sms_gateway_topic_{channelId}`
2. 提交应答后发送 `sms_write_log_topic`
3. 最终状态报告后发送 `sms_push_report_topic`
4. 最终状态报告后发送 `sms_gateway_normal_exchange`，走 TTL + 死信链路更新 ES

它是 RabbitMQ 主链路里的“协议网关 + 状态分发器”。

## 7.4 `beacon-search`

职责：

1. 消费 `sms_write_log_topic`，写 ES 日志
2. 消费 `sms_gateway_dead_queue`，更新 ES 最终状态
3. 更新失败时，再次重投 `sms_gateway_normal_queue` 做二次延迟重试

它是 RabbitMQ 主链路里的“日志落库与状态收敛端”。

## 7.5 `beacon-push`

职责：

1. 消费 `sms_push_report_topic`
2. 向客户回调地址推送状态报告
3. 推送失败时投递 `push_delayed_exchange`
4. 再消费 `push_delayed_queue` 做延迟重试

它是 RabbitMQ 主链路里的“客户回调执行器”。

## 7.6 `beacon-monitor`

职责：

1. 不直接参与业务消息流转
2. 通过 `ConnectionFactory` 查询 `sms_pre_send_topic` 和通道发送队列的堆积情况
3. 积压过高时发邮件告警

它的作用是“监控 RabbitMQ 是否堆积”，不是消费业务消息。

---

## 8. 当前代码可见的几个关键观察

1. `sms_gateway_normal_exchange / normal_queue / dead_exchange / dead_queue` 在 `beacon-smsgateway` 和 `beacon-search` 两边都声明了一遍。
   - 这不是坏事。
   - 它的目的更像是降低启动顺序耦合，哪边先启动都能补齐 RabbitMQ 拓扑。

2. `sms_gateway_topic_{channelId}` 不是统一预先定义的固定队列，而是按通道动态声明。
   - 这让通道路由更灵活。
   - 也意味着 `gateway.sendtopic` 这种配置必须与实际通道队列名匹配。

3. `mobile_area_operator_topic` 当前仓库里能看到生产者，但没看到消费者。
   - 这说明它要么依赖仓库外组件，
   - 要么当前代码尚未把消费端纳入本仓库。

4. 网关状态更新链路里的死信队列，本质不是“异常消息回收站”，而是“延迟更新器”。
   - 这是理解本项目 MQ 拓扑时最容易误解的点。

---

## 9. 排障时应该先看什么

如果要排 RabbitMQ 相关问题，建议先按下面顺序看：

1. **接口请求进入但后续没动静**
   - 看 `sms_pre_send_topic` 是否堆积
   - 看 `beacon-strategy` 是否正常消费

2. **策略通过了但网关没发**
   - 看 `sms_gateway_topic_{channelId}` 是否有消息堆积
   - 看 `gateway.sendtopic` 是否配置到正确队列

3. **短信发了但搜索查不到状态**
   - 看 `sms_write_log_topic` 是否已被消费
   - 看 `sms_gateway_normal_queue` / `sms_gateway_dead_queue` 是否有堆积
   - 看 ES 文档是否先写入、后更新

4. **客户没收到回调**
   - 看 `sms_push_report_topic` 是否消费
   - 看 `push_delayed_queue` 是否不断重试
   - 看客户回调地址是否可达、是否返回 `SUCCESS`

5. **监控告警说 MQ 堆积**
   - 先看 `sms_pre_send_topic`
   - 再看具体 `sms_gateway_topic_{channelId}`
   - 最后再看 `sms_write_log_topic`、`sms_push_report_topic`、`sms_gateway_dead_queue`

---

## 10. 最简结论

如果只记 3 句话，可以记下面这些：

1. RabbitMQ 把短信发送拆成了“API 受理 -> 策略 -> 网关 -> 日志/状态/回调”几段异步链路。
2. 死信队列在这里主要不是处理消费失败，而是实现“延迟 10 秒后再更新 ES 状态”。
3. `beacon-push` 的延迟重试不是靠死信队列，而是靠 RabbitMQ 的 `x-delayed-message` 插件。

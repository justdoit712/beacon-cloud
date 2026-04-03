# RabbitMQ 在本项目中的实现问题与改进方案

文档类型：专题问题分析  
适用对象：开发 / 排障 / 架构理解 / 新同学  
验证基线：仓库代码静态核对  
关联模块：beacon-api / beacon-strategy / beacon-smsgateway / beacon-search / beacon-push / beacon-monitor / beacon-common  
最后核对日期：2026-04-03

---

## 1. 先给不了解 RabbitMQ 的读者一个最短背景

如果你只知道 RabbitMQ 是“消息中间件”，那先记住下面几件事就够了：

### 1.1 队列（Queue）

队列可以理解成“消息仓库”。

生产者把消息放进去，消费者从里面取出来处理。

### 1.2 交换机（Exchange）

交换机可以理解成“分发器”。

生产者有时不是把消息直接发到队列，而是先发给交换机，再由交换机按规则分发到一个或多个队列。

### 1.3 消费确认（ack）

消费者收到消息后，一般要告诉 RabbitMQ：

- 这条消息我处理成功了，你可以删掉了。

这个动作就叫 `ack`。

如果消息还没真正处理好就 `ack`，那消息就可能“丢了”。

### 1.4 死信队列（Dead Letter Queue）

死信队列通常用来接收那些“没能正常处理”的消息，例如：

- 消息过期了
- 消息被拒绝了
- 队列长度满了

但在不同项目里，死信队列也可能被拿来做“延迟处理”。

### 1.5 TTL

TTL 可以理解成“消息保质期”。

例如一条消息在某个队列里最多待 10 秒，10 秒一到就过期。  
如果这个队列配置了死信交换机，过期消息就会被转走。

### 1.6 延迟消息

延迟消息就是：

- 不是现在处理
- 而是过一段时间再处理

本项目里有两种延迟方式：

1. `TTL + 死信队列`
2. `x-delayed-message` 插件

---

## 2. 当前项目里 RabbitMQ 主要承担什么角色

在 `beacon-cloud` 中，RabbitMQ 不是辅助组件，而是短信主链路的一部分。

它当前主要承担 5 件事：

1. `beacon-api` 受理短信请求后，把消息异步交给 `beacon-strategy`
2. `beacon-strategy` 做完策略判断后，把消息路由给 `beacon-smsgateway`
3. `beacon-smsgateway` 和运营商交互后，把发送日志异步交给 `beacon-search`
4. `beacon-smsgateway` 或 `beacon-strategy` 需要回调客户时，把状态报告交给 `beacon-push`
5. 网关状态更新链路通过 `TTL + 死信队列` 延迟后再让 `beacon-search` 更新 ES

也就是说：

**RabbitMQ 把原本一条很长的同步链路，拆成了多个异步阶段。**

这有好处：

1. 接口线程不用等整条链路执行完
2. 模块之间可以解耦
3. 某一段慢，不一定马上拖死前一段

但也会带来新的复杂度：

1. 消息有没有真正送到？
2. 消费成功还是失败？
3. 失败要不要重试？
4. 哪个队列是谁负责声明？
5. 死信队列在这个项目里到底代表“失败”还是“延迟”？

本篇文档主要讨论的就是这些问题。

---

## 3. 当前项目中的 RabbitMQ 交互总览

根据当前仓库代码，主链路可以简化为：

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
       -> dead-letter
       -> sms_gateway_dead_queue
       -> beacon-search

beacon-push
  <- sms_push_report_topic
  -> push_delayed_exchange
  -> push_delayed_queue
  -> 延迟后再次消费
```

几个最重要的名字：

1. `sms_pre_send_topic`
   - API 发给策略模块的预发送队列
2. `sms_gateway_topic_{channelId}`
   - 策略模块发给网关模块的通道发送队列
3. `sms_write_log_topic`
   - 写发送日志到搜索模块的队列
4. `sms_push_report_topic`
   - 发给回调模块的状态报告队列
5. `sms_gateway_normal_exchange -> sms_gateway_normal_queue -> sms_gateway_dead_queue`
   - 网关状态更新延迟链路
6. `push_delayed_exchange -> push_delayed_queue`
   - 客户回调失败后的延迟重试链路

---

## 4. 当前实现中的问题总览

先给一个总表，后面再逐条展开。

| 编号 | 问题 | 影响模块 | 严重度 |
| --- | --- | --- | --- |
| 1 | 队列/交换机命名与真实语义不一致，容易误导理解 | 全局 | 中 |
| 2 | RabbitMQ 拓扑声明分散，没有单一事实源 | 全局 | 中 |
| 3 | 生产者 confirm / return 只有日志，没有补偿动作 | API / Strategy | 高 |
| 4 | 配了 return 回调但没看到 `mandatory=true`，路由失败感知可能不完整 | API / Strategy | 高 |
| 5 | 策略模块消费 `sms_pre_send_topic` 的异常边界不清晰 | Strategy | 高 |
| 6 | 网关模块消费后过早 `ack`，发送失败可能丢消息 | SmsGateway | 高 |
| 7 | 网关先保存关联状态再发送，失败时可能留下孤儿状态 | SmsGateway | 中 |
| 8 | 动态通道队列与固定 `gateway.sendtopic` 配置之间的关系不够清晰 | Strategy / SmsGateway | 中 |
| 9 | `mobile_area_operator_topic` 有生产者但当前仓库未见消费者 | Strategy | 中 |
| 10 | 死信队列被用作“延迟更新通道”，语义容易被误解 | SmsGateway / Search | 中 |
| 11 | normal/dead 拓扑在多个模块重复声明，存在配置漂移风险 | SmsGateway / Search | 中 |
| 12 | TTL、重试间隔、交换机耐久性等关键参数硬编码 | SmsGateway / Push | 中 |
| 13 | 搜索模块的消费确认和重试语义不够显式 | Search | 中 |
| 14 | 状态更新链路第一次发 exchange，重试时却直接发 queue，语义不一致 | Search | 中 |
| 15 | 推送模块的延迟交换机是非持久化的 | Push | 高 |
| 16 | 监控模块在“查看队列”时会主动声明队列，还不关闭连接 | Monitor | 中 |
| 17 | 没有看到统一的 Rabbit 监听容器策略（prefetch / retry / requeue / DLQ） | 多模块 | 中 |

---

## 5. 逐条问题说明

## 5.1 队列/交换机命名与真实语义不一致，容易误导理解

### 现象

例如常量里有：

- `sms_pre_send_topic`
- `sms_write_log_topic`
- `sms_push_report_topic`

但从代码看，它们当前是被当作“队列名”直接使用的，而不是 `topic exchange`。

相关位置：

- `beacon-common/src/main/java/com/cz/common/constant/RabbitMQConstants.java`
- `beacon-api/src/main/java/com/cz/api/config/RabbitMQConfig.java`
- `beacon-strategy/src/main/java/com/cz/strategy/config/RabbitMQConfig.java`

### 为什么这是问题

对懂 RabbitMQ 的人来说，`topic` 往往意味着“TopicExchange”。  
但这里的 `xxx_topic` 实际上是队列，容易让人误判拓扑结构。

对不懂 RabbitMQ 的新同学来说，更容易出现下面的误会：

1. 以为项目里用了 topic exchange，其实没有
2. 以为消息是按路由键分发，其实很多场景只是直接发队列或用默认交换机

### 可行方案

1. 统一命名规则
   - 队列名用 `*_queue`
   - 交换机名用 `*_exchange`
2. 如果短期不改代码，至少先在文档里明确：
   - `sms_pre_send_topic` 当前是队列，不是 topic exchange
3. 后续如果需要更强的路由表达，再把主链路迁移到显式交换机

---

## 5.2 RabbitMQ 拓扑声明分散，没有单一事实源

### 现象

当前 RabbitMQ 拓扑声明散落在多个模块中：

1. `beacon-api` 声明 `sms_pre_send_topic`
2. `beacon-strategy` 声明 `sms_pre_send_topic`、`sms_write_log_topic`、`sms_push_report_topic`
3. `beacon-smsgateway` 声明 normal/dead exchange 和 queue
4. `beacon-search` 又声明了一遍相同的 normal/dead exchange 和 queue
5. `beacon-push` 再声明自己的 delayed exchange 和 delayed queue

### 为什么这是问题

如果一个项目里“队列归谁声明”没有清晰边界，就会出现：

1. 配置分散
2. 启动顺序依赖隐蔽
3. 改一个拓扑参数时，不容易知道需要改几处
4. 两个模块可能把同一个队列声明成不同参数，导致启动冲突

虽然当前 normal/dead 拓扑在 `beacon-smsgateway` 和 `beacon-search` 中看起来是一致的，但这种重复声明本身就意味着未来有漂移风险。

### 可行方案

1. 为 RabbitMQ 拓扑建立单一事实源
   - 方案 A：专门的基础设施模块统一声明
   - 方案 B：按“消费者拥有队列”原则统一到消费方声明
2. 建立拓扑文档，列清楚：
   - 队列是谁声明
   - 谁生产
   - 谁消费
3. 把共享拓扑参数抽成公共常量或配置

---

## 5.3 生产者 confirm / return 只有日志，没有补偿动作

### 现象

`beacon-api` 和 `beacon-strategy` 都自定义了 `RabbitTemplate`，配置了：

1. `ConfirmCallback`
2. `ReturnCallback`

但当前处理逻辑基本只有“打错误日志”。

相关代码：

- `beacon-api/src/main/java/com/cz/api/config/RabbitTemplateConfig.java`
- `beacon-strategy/src/main/java/com/cz/strategy/config/RabbitTemplateConfig.java`

### 为什么这是问题

对于业务系统来说，只打日志通常不够。

比如：

1. API 已经返回“受理成功”
2. 但消息其实没有真正进入 RabbitMQ
3. 回调里只是打印一行日志
4. 没有重试、没有补偿、没有业务状态落库

结果就是：

**用户以为发出去了，实际上消息可能根本没进主链路。**

### 可行方案

1. 至少把发送失败上升为业务可观测事件
   - 例如记录发送失败表或失败缓存
2. 对关键链路增加补偿
   - API 入队失败时返回明确失败
   - 或进入重试队列
3. 指标化而不是只打日志
   - confirm 失败次数
   - return 次数
   - 失败消息关联的 `sequenceId`

---

## 5.4 配了 return 回调但没看到 `mandatory=true`，路由失败感知可能不完整

### 现象

在 `beacon-api` 和 `beacon-strategy` 的 `RabbitTemplateConfig` 中：

1. 配了 `ReturnCallback`
2. 但代码里没看到 `rabbitTemplate.setMandatory(true)`

### 为什么这是问题

RabbitMQ 的 return 回调不是“只要路由失败就一定触发”。  
如果 `mandatory` 没打开，消息可能在无法路由时被 Broker 直接丢弃，应用侧不一定能收到回调。

对不了解 RabbitMQ 的人来说，可以这样理解：

1. 你配置了“快递送不到时通知我”
2. 但你没打开“送不到必须退回”的开关
3. 结果有些快递可能直接丢了，你还以为自己能收到通知

### 可行方案

1. 明确设置 `rabbitTemplate.setMandatory(true)`
2. 对 return 回调里的失败消息做统一处理
3. 在测试里专门验证“无路由时是否真的触发回调”

---

## 5.5 策略模块消费 `sms_pre_send_topic` 的异常边界不清晰

### 现象

`PreSendListener` 当前逻辑是：

1. 调 `filterContext.strategy(submit)`
2. 正常时 `basicAck`
3. `StrategyException` 时也 `basicAck`
4. 对其他异常没有显式分类处理

代码位置：

- `beacon-strategy/src/main/java/com/cz/strategy/mq/PreSendListener.java`

### 为什么这是问题

RabbitMQ 消费异常通常至少要分两类：

1. **业务异常**
   - 例如黑名单命中、敏感词命中、余额不足
   - 这种一般不需要重试
2. **系统异常**
   - 例如 NPE、缓存调用异常、序列化异常
   - 这种通常需要明确决定是重试、死信还是告警

当前代码没有把这个边界写得很清楚，容易让后续维护者搞不明白：

1. 什么错误应该 `ack`
2. 什么错误应该 `nack`
3. 什么错误需要补偿消息已经成功发送后才能 `ack`

### 可行方案

1. 显式区分业务异常和系统异常
2. 引入统一消费容器策略
   - 明确 `requeue`、重试次数、死信策略
3. 给消费端增加成功/失败/重试指标

---

## 5.6 网关模块消费后过早 `ack`，发送失败可能丢消息

### 现象

在 `SmsGatewayListener` 中：

1. 先把 `submit` 保存到状态缓存
2. 再调用 `nettyClient.submit(cmppSubmit)`
3. 然后立刻 `basicAck`

代码位置：

- `beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java`

### 为什么这是问题

这里最关键的问题是：

**`ack` 发生得太早。**

对 RabbitMQ 来说，`ack` 的意思是：

“这条消息我已经处理完成，可以删掉了。”

但当前代码里，真正的“业务成功”其实取决于：

1. 网关是否真的把消息成功提交给 Netty 客户端
2. Netty 客户端是否真的把报文发出

如果 `nettyClient.submit(...)` 实际失败，但消息已经 `ack`，RabbitMQ 不会再把这条消息给你。

结果就是：

**消息可能没真正发出去，但队列里的原始消息已经没了。**

### 可行方案

1. 只有在 `submit` 调用结果明确成功时才 `ack`
2. 如果发送失败：
   - `basicNack(..., requeue=true)` 重新入队
   - 或进入明确的失败队列
3. 为“已 ack 但网关未真正下发”建立可观测指标

---

## 5.7 网关先保存关联状态再发送，失败时可能留下孤儿状态

### 现象

`SmsGatewayListener` 在真正发送前，会先调用：

```java
cmppStateStore.saveSubmit(sequence, submit);
```

然后才：

```java
nettyClient.submit(cmppSubmit);
```

### 为什么这是问题

如果后面发送失败了，就会出现：

1. RabbitMQ 消息已经被 `ack`
2. 发送没有成功
3. 关联状态却已经保存了

这样就可能留下：

- 永远等不到运营商应答的 submit 状态
- 后续排障时看到缓存里有状态，但业务实际没发出

这类“只写入、不收敛”的状态，一般叫孤儿状态。

### 可行方案

1. 调整顺序
   - 发送成功后再保存状态
   - 或保存时带短 TTL 与失败清理机制
2. 为状态缓存增加未回收监控
3. 在网关发送失败时立即删除已保存的状态

---

## 5.8 动态通道队列与固定 `gateway.sendtopic` 配置之间的关系不够清晰

### 现象

策略模块会按 `channelId` 动态声明：

```java
sms_gateway_topic_{channelId}
```

但网关模块监听的是：

```java
@RabbitListener(queues = "${gateway.sendtopic}")
```

也就是说，消费者监听哪个队列，不是代码里自动跟着 `channelId` 变化，而是依赖外部配置。

### 为什么这是问题

这会让新接手项目的人很容易困惑：

1. 策略模块可以路由到很多 `channelId`
2. 但单个网关实例实际上监听哪个队列，要看配置中心里的 `gateway.sendtopic`
3. 如果部署约定没说明白，就会出现：
   - 策略投递了某个队列
   - 网关却根本没人监听那个队列

### 可行方案

1. 在代码和文档中明确部署约定
   - 一个网关实例监听一个通道队列
   - 还是一个实例监听多个通道队列
2. 如果要支持多通道，考虑改为：
   - 统一交换机 + routing key 路由
   - 或配置多个监听队列
3. 启动时检查 `gateway.sendtopic` 是否与通道配置一致

---

## 5.9 `mobile_area_operator_topic` 有生产者，但当前仓库没有看到消费者

### 现象

在 `PhaseStrategyFilter` 中，号段补齐成功后会发送：

```java
rabbitTemplate.convertAndSend(RabbitMQConstants.MOBILE_AREA_OPERATOR, mqMap);
```

同时 `beacon-strategy` 也声明了 `mobile_area_operator_topic` 队列。  
但当前仓库代码中没有检索到对应的 `@RabbitListener` 消费者。

### 为什么这是问题

如果一个消息有生产者，但没有消费者，最常见的结果就是：

1. 队列一直堆积
2. 开发者误以为“后面会自动同步”
3. 实际上数据根本没被处理

### 可行方案

1. 如果消费端在仓库外，文档里明确写出归属系统
2. 如果消费端本应在本仓库，实现缺口要补齐
3. 监控中增加该队列堆积告警

---

## 5.10 死信队列被用作“延迟更新通道”，语义容易被误解

### 现象

当前项目中：

1. `sms_gateway_normal_queue` 配了 `x-message-ttl = 10000`
2. 消息过期后转入 `sms_gateway_dead_exchange`
3. 最终进入 `sms_gateway_dead_queue`
4. `beacon-search` 消费死信队列更新 ES 状态

### 为什么这是问题

这不是“错误”，但**非常容易被误解**。

因为很多人一听“死信队列”，第一反应是：

- 这里放的是失败消息

但本项目里它更多承担的是：

- 延迟 10 秒后再处理的消息

也就是说：

**死信队列在这里不是失败消息垃圾桶，而是延迟更新通道。**

如果没有文档说明，后面的人很容易：

1. 错误理解链路语义
2. 误把 dead queue 当异常分析入口
3. 在调整 TTL 或补偿逻辑时做出错误改动

### 可行方案

1. 在文档中明确说明当前死信用途
2. 如果后续允许重构，可改名为更贴近语义的链路
3. 如果要区分“延迟处理”和“真正失败消息”，建议拆成两条链路

---

## 5.11 normal/dead 拓扑在多个模块重复声明，存在配置漂移风险

### 现象

`beacon-smsgateway` 和 `beacon-search` 都声明了：

1. `sms_gateway_normal_exchange`
2. `sms_gateway_normal_queue`
3. `sms_gateway_dead_exchange`
4. `sms_gateway_dead_queue`

而且都写了 TTL、死信交换机、routing key。

### 为什么这是问题

一旦未来有人改了其中一边，例如：

1. TTL 改成 5 秒
2. routing key 改了
3. 队列参数补了别的属性

另一边如果忘记同步改，就会出现：

1. 启动冲突
2. 同名队列参数不一致
3. 拓扑语义漂移，但很难第一时间发现

### 可行方案

1. 让一个模块拥有这套拓扑的声明权
2. 其他模块只依赖，不重复声明
3. 至少把 TTL 和死信参数提到公共配置

---

## 5.12 TTL、重试间隔、交换机耐久性等关键参数硬编码

### 现象

当前代码里能直接看到多处硬编码：

1. `beacon-smsgateway` 和 `beacon-search`
   - `TTL = 10000`
2. `beacon-push`
   - `delayTime = {0,15000,30000,60000,300000}`
3. `beacon-push`
   - delayed exchange `durable = false`

### 为什么这是问题

硬编码的问题不在于“不能跑”，而在于：

1. 不同环境没法灵活调整
2. 出现性能或堆积问题时，调优需要改代码发版
3. 参数变更和业务规则耦合在一起，运维成本高

### 可行方案

1. 把 TTL、重试间隔、最大重试次数都放到配置中心
2. 给关键参数加启动日志输出
3. 重要拓扑默认用 durable

---

## 5.13 搜索模块的消费确认和重试语义不够显式

### 现象

`beacon-search` 的两个监听器：

1. `SmsWriteLogListener`
2. `SmsUpdateLogListener`

都是“业务逻辑执行完后手动 `basicAck`”。

但代码里没有看到统一的消费容器工厂去明确声明：

1. 异常时是否重试
2. 是否重新入队
3. 重试几次
4. 是否进入死信

### 为什么这是问题

这样会让 RabbitMQ 消费行为变得依赖框架默认值。  
框架默认值本身不一定错，但对于复杂系统来说：

**默认值 = 不透明。**

排障时最怕的就是：

1. 为什么这条消息重试了？
2. 为什么这条消息没重试？
3. 为什么这条消息积压了？

结果回答只能是：

- “可能是 Spring AMQP 默认行为”

### 可行方案

1. 增加统一的 `SimpleRabbitListenerContainerFactory`
2. 显式配置：
   - ack mode
   - prefetch
   - retry
   - requeue
   - dead-letter
3. 把这些策略写到文档里，而不是依赖默认行为

---

## 5.14 状态更新链路第一次发 exchange，重试时却直接发 queue，语义不一致

### 现象

第一次状态更新时，网关发的是：

```java
rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_GATEWAY_NORMAL_EXCHANGE, "", report);
```

而搜索模块更新失败重试时，发的是：

```java
rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_GATEWAY_NORMAL_QUEUE, report);
```

也就是说：

1. 第一次：走 exchange
2. 重试时：直接发 queue

### 为什么这是问题

这会让同一条业务链路的语义变得不统一：

1. 第一跳依赖交换机
2. 第二跳绕开交换机

如果后面有人改了 exchange 路由规则，很可能只改到第一跳，忽略第二跳。  
对新同学来说，这也会造成理解负担：

- “到底这条链路应该发 exchange，还是发 queue？”

### 可行方案

1. 同一条业务链路统一入口
   - 要么始终发 exchange
   - 要么明确设计成固定队列入口
2. 对“重试消息再次进入延迟链路”的路径做统一封装

---

## 5.15 推送模块的延迟交换机是非持久化的

### 现象

在 `beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java` 中：

```java
new CustomExchange(DELAYED_EXCHANGE, DELAYED_EXCHANGE_TYPE, false, false, args)
```

这里的第三个参数是 `durable=false`。

### 为什么这是问题

这意味着如果 RabbitMQ Broker 重启：

1. 交换机定义可能丢失
2. 延迟重试链路失效

而 `beacon-push` 的这条链路承接的是“客户回调失败后的重试”，属于关键可靠性链路，不适合用非持久交换机。

### 可行方案

1. 改成 durable 交换机
2. 启动时检测 delayed 插件是否可用
3. 监控里增加 delayed queue / exchange 异常告警

---

## 5.16 监控模块在“查看队列”时会主动声明队列，还不关闭连接

### 现象

`beacon-monitor` 里为了查看消息积压，会：

1. `connectionFactory.createConnection()`
2. `connection.createChannel(false)`
3. 调 `channel.queueDeclare(queueName, true, false, false, null)`
4. 再用 `channel.messageCount(queueName)` 查消息数

但代码里没有正常关闭 `Connection` 和 `Channel`。

相关代码：

- `beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

### 为什么这是问题

这有两个问题：

1. **监控带副作用**
   - 监控本来应该只读
   - 但这里会主动声明队列
   - 这意味着“看一眼监控”可能顺手改了 RabbitMQ 状态

2. **资源泄漏**
   - 连接和信道不关闭
   - 定时任务长期运行会累积资源

### 可行方案

1. 用被动查询而不是主动 `queueDeclare`
2. 用 `try-with-resources` 或 finally 显式关闭资源
3. 把“监控逻辑”和“修改 broker 状态”彻底分开

---

## 5.17 没有看到统一的 Rabbit 监听容器策略

### 现象

从当前仓库代码可见，没有检索到一个统一的 Rabbit 监听容器配置来收敛下面这些行为：

1. 并发消费者数
2. 预取数 `prefetch`
3. 重试次数
4. 重回队列还是进死信
5. 哪些异常需要立即失败

### 为什么这是问题

对于小项目，默认配置常常也能跑。  
但对这个项目来说，RabbitMQ 已经是核心主链路，继续完全依赖默认值会带来两个长期问题：

1. 行为不可见
2. 各模块处理方式不统一

久而久之，就会形成：

1. 有的监听器手动 ack
2. 有的监听器遇错靠默认回滚
3. 有的链路自己补偿
4. 有的链路没有补偿

这会让排障成本越来越高。

### 可行方案

1. 增加统一的 Rabbit 监听容器配置
2. 为不同类型队列定义不同消费策略
   - 主发送链路
   - 日志链路
   - 回调链路
   - 延迟链路
3. 把策略写成文档和代码约定，不靠“默认经验”

---

## 6. 建议的分阶段治理方案

## 第一阶段：先补关键可靠性问题

建议优先处理：

1. `mandatory=true` + return 回调真实性校验
2. API / Strategy 的发送失败补偿
3. 网关消费过早 `ack`
4. 推送延迟交换机改为 durable
5. 监控模块去掉 `queueDeclare` 副作用并关闭资源

这几个点最直接关系到“消息会不会丢、重启后还能不能重试、监控会不会误操作”。

## 第二阶段：统一 RabbitMQ 交互语义

建议处理：

1. 建立统一的 listener container 配置
2. 明确 ack / nack / requeue / dead-letter 策略
3. 统一状态更新链路是发 exchange 还是发 queue
4. 统一 normal/dead 拓扑声明归属

这一步的目标是：

**让项目里所有 RabbitMQ 交互行为有统一规则，而不是各模块各写一套。**

## 第三阶段：收敛拓扑与配置治理

建议处理：

1. 队列/交换机命名规范化
2. TTL / 重试间隔 / 通道监听配置外置化
3. 明确 `gateway.sendtopic` 的部署约定
4. 明确 `mobile_area_operator_topic` 的消费归属

这一步的目标是：

**让拓扑不再靠口口相传，而是代码、配置、文档三者一致。**

---

## 7. 如果只给新同学一个最简判断

可以直接这样理解：

1. 本项目 RabbitMQ 主链路是能跑通的，但现在更像“靠经验维持”的状态，而不是“规则清晰、边界明确”的状态。
2. 最大的问题不是某一个队列名写错，而是：
   - 发送失败怎么补偿
   - 消费失败怎么处理
   - 延迟和死信到底代表什么
   - 谁负责声明和拥有哪些拓扑
3. 如果后面继续演进，不先把 RabbitMQ 的语义和边界收拢，排障成本会越来越高。

---

## 8. 最简结论

如果你只记 5 句话，可以记下面这些：

1. RabbitMQ 是这个项目发送主链路的一部分，不是外围工具。
2. 当前实现里，最值得先关注的是“消息发送失败补偿”和“消费过早 ack”。
3. 死信队列在这里主要是做延迟状态更新，不是传统意义上的失败消息回收站。
4. 回调重试链路依赖 `x-delayed-message` 插件，而且当前交换机还是非持久化的。
5. 当前最大的工程问题不是功能不能跑，而是 RabbitMQ 的规则分散在多个模块里，缺少统一边界。

# beacon-search 模块 ES 作用与交互分析

文档类型：专题分析  
适用对象：开发 / 重构 / 排障 / 架构梳理  
验证基线：代码静态核对  
关联模块：`beacon-search` / `beacon-smsgateway` / `beacon-webmaster`  
最后核对日期：2026-04-02

---

## 1. 结论先行

当前项目里，Elasticsearch 主要承担的是**短信日志检索与状态统计的读侧索引**，不是短信发送链路的主事实来源。

它的职责可以概括为：

1. 存储短信提交日志，支撑后台分页检索。
2. 接收网关回执后的状态更新，支撑成功/失败/等待状态查询。
3. 提供聚合统计能力，支撑后台图表和运营查询。

从代码实现看，`beacon-search` 当前已经形成了“MQ 驱动写 ES，后台通过 HTTP 查 ES”的完整闭环，但 ES 交互仍存在一些典型问题：

1. 写入幂等定义不正确。
2. 更新链路依赖 `ThreadLocal` 传业务上下文。
3. 索引路由和查询范围都过度依赖“当前年份”。
4. 查询接口和查询参数大量使用弱类型 `Map`。
5. ES 客户端配置和超时治理较弱。
6. 写入、更新、重试、查询这些链路的边界和语义还不够清晰。

一句话判断：

**当前 ES 在项目中已经是核心读侧组件，但实现方式更偏“能跑”，离“稳定、可维护、跨年可用、可观测”还有一段距离。**

---

## 2. ES 在当前项目中的作用

## 2.1 ES 存的是什么

当前 ES 里存的是短信发送日志文档，核心索引前缀是：

```text
sms_submit_log_{year}
```

当前文档主键使用短信平台内部流水号 `sequenceId`。

写入时的数据来源是 `StandardSubmit`，并补充了一个便于范围查询的字段：

1. `sendTimeMillis`

更新时主要修改的字段是：

1. `reportState`
2. 失败场景下的 `errorMsg`（当前更新链路代码里主要显式更新的是 `reportState`，但状态对象本身包含更多扩展信息）

查询结果再被后台层转成列表、图表统计和展示字段。

## 2.2 ES 不是什么

当前 ES 不是：

1. 短信发送链路的主事实库
2. 客户、签名、模板、余额等配置数据的权威来源
3. 回执处理的主状态机

这些主事实仍然分布在其他链路和模块中。  
`beacon-search` 更像是一个**搜索投影层 / 检索索引层**。

---

## 3. 当前 ES 交互链路

## 3.1 写入链路：短信提交日志入 ES

链路如下：

1. `beacon-smsgateway` 在提交运营商请求后，发送 `SMS_WRITE_LOG` 消息。
2. `beacon-search` 的 `SmsWriteLogListener` 消费该消息。
3. 监听器把 `StandardSubmit` 转成 JSON 文档。
4. `ElasticsearchServiceImpl#index(...)` 调用 ES 写入索引。

对应代码：

1. `beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java`
2. `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

当前写入的关键特征：

1. 索引名由监听器按“当前年份”拼接。
2. 文档 ID 为 `submit.getSequenceId().toString()`。
3. 监听器会额外写一个 `sendTimeMillis` 字段，供时间范围检索使用。

## 3.2 更新链路：网关回执更新 ES 文档状态

链路如下：

1. `beacon-smsgateway` 把回执结果投递到 `SMS_GATEWAY_NORMAL_EXCHANGE`。
2. `beacon-search` 使用 TTL 队列 + 死信队列延迟消费。
3. `SmsUpdateLogListener` 消费 `SMS_GATEWAY_DEAD_QUEUE` 中的 `StandardReport`。
4. `ElasticsearchServiceImpl#update(...)` 更新对应文档的 `reportState`。

对应代码：

1. `beacon-search/src/main/java/com/cz/search/config/RabbitConfig.java`
2. `beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java`
3. `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

当前更新链路的关键特征：

1. 更新前会先 `exists(index, id)`。
2. 文档不存在时，会把 `StandardReport` 再次投递回普通队列重试一次。
3. 重试状态通过 `SearchUtils` 中的 `ThreadLocal<StandardReport>` 传递。

## 3.3 查询链路：后台检索与状态统计

链路如下：

1. `beacon-webmaster` 通过 `SearchClient` 调用 `beacon-search`。
2. `SmsSearchController` 接收 `Map<String, Object>` 查询参数。
3. `ElasticsearchServiceImpl#findSmsByParameters(...)` 执行列表查询。
4. `ElasticsearchServiceImpl#countSmsState(...)` 执行聚合统计。

对应代码：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/client/SearchClient.java`
2. `beacon-search/src/main/java/com/cz/search/controller/SmsSearchController.java`
3. `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

当前查询支持的主要条件：

1. 内容关键字 `content`
2. 手机号前缀 `mobile`
3. 时间范围 `starttime/stoptime`
4. 客户 ID `clientID`

当前查询结果还做了几件展示层适配：

1. `sendTime` 数组转 `sendTimeStr`
2. `text` 高亮
3. `corpname` 直接映射为 `sign`

---

## 4. 当前主要问题

以下问题按影响程度排序，但都围绕“ES 作为读侧索引层”的稳定性和边界清晰度展开。

## 4.1 P0：写入幂等语义错误

当前 `index(...)` 只接受 ES 返回 `CREATED`，否则直接抛异常。

这会带来一个现实问题：

1. 在 MQ 至少一次投递语义下，同一条消息可能重复到达。
2. 同一个文档 ID 再次 `index` 时，ES 有可能返回 `UPDATED`。
3. 这类幂等重试本应视为成功，但当前实现会视为失败。

结果是：

1. 重复消息无法自然幂等。
2. 异常路径被放大。
3. 搜索模块对 MQ 重试不够友好。

## 4.2 P0：更新链路依赖 `ThreadLocal` 传递上下文

当前 `SmsUpdateLogListener` 把 `StandardReport` 放进 `SearchUtils` 的 `ThreadLocal`，然后 `ElasticsearchServiceImpl#update(...)` 再去取。

这会带来几个问题：

1. 业务方法签名不完整，参数靠线程上下文“隐式传递”。
2. 线程复用场景下，如果清理不严谨，容易污染下次请求。
3. 读代码和排障时，不容易看出 `update(...)` 真正依赖什么。
4. 一旦改成异步线程池或链路扩展，`ThreadLocal` 更容易出问题。

本质上，这属于**跨层隐藏依赖**。

## 4.3 P0：索引路由和查询范围都依赖“当前年份”

当前写入、更新、查询都大量依赖：

```java
LocalDateTime.now().getYear()
```

这意味着：

1. 写入时按“消费发生的年份”选索引，而不是按“业务发送时间”选索引。
2. 更新时也按“更新发生的年份”选索引。
3. 查询默认只查当前年索引。

典型问题场景：

1. 12 月 31 日发出的短信，1 月 1 日回执才到，更新会落到错误年份索引。
2. 积压消息跨年消费时，写入和更新可能分裂到不同索引。
3. 后台查询跨年短信时，当前实现默认查不到历史年份索引。

这条问题是当前 ES 链路里最典型的**时间维度路由错误**。

## 4.4 P1：更新实现是“先 exists 再 update”，交互往返偏多且语义不够清晰

当前更新流程是：

1. 先发一次 `exists`
2. 存在再发一次 `update`
3. 不存在则走 MQ 重投

问题在于：

1. 每次更新至少两次 ES 往返。
2. “不存在”不一定真是失败，也可能是日志写入尚未完成。
3. 当前重试只做了一轮，且重试决策和业务上下文耦合在 `ThreadLocal`。

这让“日志未写入”和“文档真的丢失”两种情况混在了一起。

## 4.5 P1：查询接口和查询参数是弱类型 `Map`

当前控制器、Feign 和 service 查询入参都大量使用 `Map<String, Object>`。

带来的问题：

1. 前后端契约不直观。
2. 查询参数类型变化时容易出运行时问题。
3. `clientID` 既可能是单值，也可能是列表，service 内部要自己判断并转换。
4. 结果集也以 `Map` 形式暴露，字段语义容易漂移。

这使得 ES 查询层和展示层之间缺少清晰契约。

## 4.6 P1：ES 客户端配置偏弱

当前 `RestHighLevelClientConfig` 主要问题有：

1. `hostAndPorts` 只做简单 `split(":")`，没有配置格式校验。
2. 没有显式设置连接超时、读超时、请求超时。
3. 用户名、密码、地址等配置没有专门的强校验对象。

这类问题在平时不一定暴露，但一旦 ES 网络抖动、配置错误或连接异常，会导致：

1. 启动失败信息不友好
2. 连接阻塞时间不可控
3. 故障恢复和排障成本偏高

## 4.7 P1：查询结果中混入了展示层适配逻辑

当前 `ElasticsearchServiceImpl` 在 service 层做了：

1. `sendTime` 数组转字符串
2. `corpname = sign`
3. 文本高亮后的字段替换

这说明当前 search 模块不仅在“查 ES”，还在承担一部分展示层拼装职责。

问题是：

1. 检索层和展示层边界不清楚。
2. 如果别的调用方也复用该接口，可能并不需要这些展示字段。
3. `corpname` 直接等于 `sign` 这种语义会给后续维护造成误导。

## 4.8 P1：监听器异常策略隐含在默认行为里

`SmsWriteLogListener` 和 `SmsUpdateLogListener` 都是“执行成功后手动 `ack`”，但没有显式区分：

1. ES 短时不可用
2. 文档不存在
3. 参数异常
4. 不可恢复业务异常

当前异常后的重试/重回队列行为更多依赖容器默认机制，语义不够清晰。

这会影响：

1. 重试是否可控
2. 消息是否会积压
3. 排障时能否快速判断“是 ES 故障还是业务数据问题”

## 4.9 P2：ES 客户端版本老旧

当前使用的是：

1. `elasticsearch-rest-high-level-client 7.6.2`

这并不是当前最紧迫的问题，但中长期有明显技术债：

1. API 已经偏老
2. 升级 ES 时兼容成本高
3. 后续生态支持会越来越弱

---

## 5. 建议解决方案

## 5.1 先明确 ES 的系统定位

建议在设计和代码层面统一口径：

1. ES 是短信日志检索索引，不是主事实库。
2. `beacon-search` 是异步读侧投影模块。
3. 写 ES 和更新 ES 允许短暂延迟，但不允许语义混乱。

只有先把这个定位讲清楚，后续“是否允许重试、是否允许最终一致、是否需要补偿”才有统一判断标准。

## 5.2 修复写入幂等语义

建议：

1. `index(...)` 把 `CREATED` 和 `UPDATED` 都视为成功。
2. 对重复写入仅记录 `info/debug`，不要抛业务异常。

这样做的收益：

1. 与 MQ 至少一次投递语义对齐。
2. 重复消息不会被误判为失败。
3. 写入链路天然具备幂等性。

## 5.3 去掉 `ThreadLocal`，把更新上下文显式传参

建议把：

```java
update(String index, String id, Map<String, Object> doc)
```

改成：

```java
update(String index, String id, Map<String, Object> doc, StandardReport report)
```

同时：

1. `SmsUpdateLogListener` 直接把 `report` 作为参数传进去。
2. 删除 `SearchUtils` 中的 `ThreadLocal`。
3. 保留 `SearchUtils` 仅做索引名工具，或进一步拆出 `IndexNameResolver`。

这样做的收益：

1. 依赖关系更直白。
2. 不再依赖线程上下文。
3. 测试、排障、扩展都更容易。

## 5.4 统一索引路由规则：按业务发送时间而不是当前时间

建议：

1. 写入索引使用 `submit.sendTime` 的年份。
2. 更新索引使用 `report.sendTime` 的年份。
3. 查询按时间范围动态决定需要查哪些年份索引。
4. 如果查询时间范围为空，至少支持当前年 + 前一年，或统一查 `sms_submit_log_*`。

更稳妥的方式是新增一个独立组件：

1. `IndexNameResolver`

职责只做：

1. 按发送时间解析索引名
2. 按时间范围生成查询索引集合

这样跨年问题会从根上收敛。

## 5.5 将查询接口 DTO 化、返回 VO 化

建议新增：

1. `SmsSearchQueryDTO`
2. `SmsSearchListItemVO`
3. `SmsStateCountVO`

让 controller/service 不再用裸 `Map` 作为主要契约。

这样做的收益：

1. 参数和返回结构清晰
2. 校验可以前移到 controller
3. ES 查询层的逻辑不会被各种 `instanceof` 和强转打断

## 5.6 强化 ES 客户端配置

建议：

1. 改成 `@ConfigurationProperties + @Validated`
2. 对 `host:port` 做显式格式校验
3. 配置连接超时、socket 超时、请求超时
4. 在日志中打印脱敏后的 ES 连接配置摘要

收益：

1. 启动失败更早、更明确
2. 网络故障行为更可控
3. 运维排障成本更低

## 5.7 明确“文档不存在”时的重试策略

当前“先 exists 再 update，不存在就重投一轮”可以保留为过渡方案，但建议升级为更明确的策略：

1. 把“日志尚未写入”和“文档永久缺失”区分开。
2. 重试次数、重试间隔、最终失败日志要显式配置。
3. 增加“更新未命中率”监控。

如果短期不引入更完整的状态机，至少也要：

1. 去掉 `ThreadLocal`
2. 给重投次数加明确上限
3. 给最终失败增加结构化日志

## 5.8 让 service 层回归检索职责，展示层适配上移

建议：

1. `corpname = sign` 这种兼容字段尽量移到 `webmaster` 或专门的转换层。
2. ES service 主要负责查询、聚合和最少必要的数据转换。
3. 前端展示相关拼装不要沉到底层 ES service。

这样能避免 `beacon-search` 逐渐演变成“查询 + 展示 + 前端兼容”混合层。

---

## 6. 推荐实施顺序

## 阶段一：先修正确性

1. 修正 `index` 的幂等判定（`CREATED/UPDATED` 都成功）
2. 去掉更新链路 `ThreadLocal`
3. 把索引路由从“当前年份”改成“业务发送时间年份”

## 阶段二：再修契约和稳定性

1. DTO/VO 化查询接口
2. 补 ES 客户端配置校验和超时
3. 明确监听器异常和重试策略

## 阶段三：最后做工程化和演进

1. 增加 ES 写入/更新/未命中监控
2. 补跨年、幂等、重试等回归测试
3. 规划 ES Java Client 升级路径

---

## 7. 建议验证清单

1. 同一条 `SMS_WRITE_LOG` 消息重复投递时，ES 写入仍视为成功。
2. 年末发送、次年回执的短信，日志写入和状态更新落在同一逻辑索引中。
3. 查询跨年时间范围时，能够查到历史年份数据。
4. 更新链路不再依赖 `ThreadLocal`。
5. `beacon-webmaster` 查询接口入参和返回结构有明确契约。
6. ES 配置错误时，服务能在启动阶段明确失败。
7. ES 短时故障时，监听器重试和告警行为可预期。

---

## 8. 一句话结论

在当前项目里，ES 已经是短信日志检索和状态统计的核心读侧组件。  
`beacon-search` 的主问题不是“有没有接入 ES”，而是**ES 的索引路由、更新上下文传递、查询契约和稳定性治理都还不够成熟**。  
建议优先修“幂等 + 索引年份 + ThreadLocal”这三件事，再推进查询 DTO 化和客户端治理。

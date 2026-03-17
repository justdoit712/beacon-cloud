# beacon-search 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-search  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-search` 是短信平台的“日志检索与统计中心”，负责将发送链路中的日志事件写入 Elasticsearch，并对外提供分页检索与状态统计能力。

在整体链路中的位置：

`beacon-strategy` / `beacon-smsgateway` -> `SMS_WRITE_LOG` -> `beacon-search(index)`  
`beacon-smsgateway` -> `sms_gateway_dead_queue` -> `beacon-search(update)`  
`webmaster` -> `beacon-search` -> `Elasticsearch`

---

## 2. 模块结构

源码目录：`beacon-search/src/main/java/com/cz/search`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `SearchStarterApp` | 服务启动与注册发现 |
| 配置层 | `RestHighLevelClientConfig`, `RabbitConfig` | ES 客户端与 MQ 消息转换器配置 |
| MQ 消费层 | `SmsWriteLogListener`, `SmsUpdateLogListener` | 新增日志写入、状态更新消费 |
| HTTP 接口层 | `SmsSearchController` | 列表查询与状态聚合接口 |
| 服务抽象层 | `SearchService` | 检索与写更新能力接口 |
| 服务实现层 | `ElasticsearchServiceImpl` | ES 写入、更新、查询、聚合核心逻辑 |
| 工具层 | `SearchUtils` | 年份索引、ThreadLocal 暂存报告对象 |

---

## 3. 核心处理流程

## 3.1 写日志流程（SMS_WRITE_LOG）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java`

流程：

1. 监听 `RabbitMQConstants.SMS_WRITE_LOG`，接收 `StandardSubmit`。
2. 把对象转为 `Map`，并补充 `sendTimeMillis`（毫秒时间戳）。
3. 按当前年份索引写入：`sms_submit_log_{yyyy}`。
4. 文档主键使用 `sequenceId`，写入后 `basicAck`。

特征：

1. 写入采用“消息驱动异步落库”，吞吐较高。
2. 依赖 `sequenceId` 全局唯一，避免重复文档。

## 3.2 状态更新流程（sms_gateway_dead_queue）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java`

流程：

1. 监听 `RabbitMQConstants.SMS_GATEWAY_DEAD_QUEUE`，接收 `StandardReport`。
2. 先把 `report` 放入 `SearchUtils` 的 `ThreadLocal`。
3. 调用 `searchService.update(...)` 更新文档 `reportState`。
4. 更新成功后 `basicAck`。

补偿策略（在 service 内）：

1. 若文档不存在且 `reUpdate=false`，将消息再次投递 `SMS_GATEWAY_NORMAL_QUEUE` 做二次延迟重试。
2. 若文档仍不存在且 `reUpdate=true`，仅记录错误日志并结束处理。

## 3.3 查询与统计流程

文件：

1. `beacon-search/src/main/java/com/cz/search/controller/SmsSearchController.java`
2. `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

能力：

1. `POST /search/sms/list`：多条件分页查询 + 关键字高亮。
2. `POST /search/sms/countSmsState`：按 `reportState` 做 terms 聚合，输出 waiting/success/fail 计数。

---

## 4. 查询语义与索引策略

## 4.1 条件构建

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

当前支持：

1. `content`：`matchQuery(text)`，可返回高亮。
2. `mobile`：`prefixQuery(mobile)`。
3. `starttime/stoptime`：基于 `sendTimeMillis` 的范围查询。
4. `clientID`：单值或列表形式的 `termsQuery(clientId)`。

## 4.2 时间索引路由

文件：

1. `beacon-search/src/main/java/com/cz/search/utils/SearchUtils.java`
2. `beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java`

现状：

1. 写入索引使用“当前系统年份”。
2. 查询索引也固定为“当前年份”。
3. 默认不跨年聚合历史索引。

结论：

1. 当管理端需要查询跨年数据时，当前模型会出现结果缺失。

---

## 5. ES 写入/更新实现分析

## 5.1 `index` 行为

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. 使用 `IndexRequest` 直接写入文档。
2. 仅当 `result == created` 视为成功，其他结果抛异常。

风险含义：

1. 对“重复消息导致覆盖更新（`updated`）”场景容忍度低，可能触发不必要重试。

## 5.2 `update` 行为

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. 先 `exists` 检查，再执行 `UpdateRequest`。
2. 文档不存在时依赖 `ThreadLocal` 中的 `report` 做重投判断。
3. 更新结果接受 `UPDATED` 与 `NOOP`，其余抛异常。

风险含义：

1. 更新流程与 `ThreadLocal` 强耦合，接口语义不纯（同一个 `update` 依赖外部隐式上下文）。

---

## 6. 依赖与配置现状

## 6.1 依赖侧

文件：`beacon-search/pom.xml`

关键依赖：

1. `elasticsearch-rest-high-level-client:7.6.2`。
2. `elasticsearch:7.6.2`。
3. `spring-boot-starter-amqp`（MQ 消费）。
4. `beacon-common`（消息模型与常量）。

## 6.2 配置侧

文件：

1. `beacon-search/src/main/resources/bootstrap.yml`
2. `beacon-search/src/main/java/com/cz/search/config/RestHighLevelClientConfig.java`

现状：

1. ES 节点、用户名、密码从配置注入。
2. 客户端配置了账号密码，但未显式设置超时、连接池、重试策略。
3. MQ 侧仅配置消息转换器，队列声明依赖上游模块先行创建。

---

## 7. 主要风险与技术债

## 7.1 ThreadLocal 清理不完整（高风险）

文件：

1. `beacon-search/src/main/java/com/cz/search/utils/SearchUtils.java`
2. `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. `SearchUtils.remove()` 只在“文档不存在”分支执行。
2. 更新成功路径未清理，线程复用下可能残留上下文，造成内存与串数据风险。

## 7.2 查询范围仅当前年份（高风险）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. 查询与统计均固定 `SearchUtils.getCurrYearIndex()`。
2. 历史年份数据无法直接检索，影响报表准确性。

## 7.3 更新重试策略存在盲区（高风险）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. 文档不存在时最多二次重试，第二次仍失败则仅记录日志。
2. 无人工补偿队列、无告警闭环，可能长期停留为 waiting 状态。

## 7.4 `index` 成功判定过于严格（中风险）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. 仅接受 `created`，不接受 `updated`。
2. 在重复消费/幂等场景下会放大异常噪声并触发额外重试。

## 7.5 参数类型转换脆弱（中风险）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`

1. `mobile` 直接强转 `String`。
2. `clientID` 列表按原始类型强转。
3. 非预期参数类型可能引发 `ClassCastException` 或 `NumberFormatException`。

## 7.6 索引命名依赖本地时间（中风险）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java`

1. 以处理时当前年写索引，而非基于消息业务时间。
2. 在跨年积压场景下可能写入到错误年份索引。

## 7.7 队列声明外置导致部署耦合（中风险）

文件：`beacon-search/src/main/java/com/cz/search/config/RabbitConfig.java`

1. 本模块不声明消费队列，依赖其他模块先行创建。
2. 对部署顺序与环境一致性较敏感。

## 7.8 自动化测试仍然偏弱（中风险）

1. 现有测试更接近简单连通性 / 手工触发型验证。
2. 核心检索、更新重试、跨年索引和参数边界仍缺少有效断言。

---

## 8. 与上下游模块的契约关系

## 8.1 上游契约

1. `SMS_WRITE_LOG` 消息体应为 `StandardSubmit`，且包含可用 `sequenceId`。
2. `SMS_GATEWAY_DEAD_QUEUE` 消息体应为 `StandardReport`，且 `sequenceId/reportState` 完整。

## 8.2 下游契约

1. 对外查询接口返回结构：`{ total, rows }`。
2. 统计接口返回结构：`{ waiting, success, fail }`。
3. `rows` 中会补充 `sendTimeStr` 与高亮后的 `text` 字段。

---

## 9. 改造建议（按优先级）

## P0（优先）

1. 在 `SmsUpdateLogListener` 使用 `try-finally` 清理 `ThreadLocal`，彻底消除泄漏风险。
2. 查询接口改为“多索引范围查询”（按时间区间自动路由年索引）。
3. 为“二次重试仍失败”引入补偿队列 + 告警（而非仅日志）。
4. 明确 `index` 的幂等策略：允许 `updated` 或使用 `opType=create` + 业务去重。

## P1（中期）

1. 统一参数校验模型（DTO + Bean Validation），消除 Map 强转风险。
2. ES 客户端补充超时、连接池、失败重试和熔断配置。
3. 增加索引模板/别名治理，降低按年索引切换成本。

## P2（长期）

1. 建立冷热分层与生命周期管理（ILM），控制历史索引成本。
2. 建设检索 SLA 监控：查询耗时分位、更新延迟、重试成功率。
3. 推进查询 DSL 抽象层，支持更复杂报表和多维分析。

---

## 10. 结论

`beacon-search` 具备“写日志 + 延迟更新 + 检索统计”的完整闭环，是运营可见性与管理报表的核心。  
当前风险主要集中在“可持续性与一致性”：ThreadLocal 生命周期、跨年索引语义、重试闭环、参数健壮性。  
优先落地 P0 后，模块将从“可用”升级为“可长期稳定运营”。

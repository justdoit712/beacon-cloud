# beacon-strategy 模块详细分析

更新时间：2026-02-26  
适用仓库：`beacon-cloud`

---

## 1. 模块定位

`beacon-strategy` 是短信平台的策略执行中枢，负责在“API 受理”之后、网关下发之前完成所有业务决策。  
它不直接对外暴露业务 HTTP 接口，核心是消费 MQ 消息并执行策略链。

在整体链路中的位置：

`beacon-api` -> `SMS_PRE_SEND` -> `beacon-strategy` -> `SMS_GATEWAY_*` / `SMS_WRITE_LOG` / `SMS_PUSH_REPORT`

---

## 2. 模块结构

源码目录：`beacon-strategy/src/main/java/com/cz/strategy`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `StrategyStarterApp` | 注册发现、Feign、RestTemplate |
| MQ 消费层 | `PreSendListener` | 监听预发送队列并触发策略链 |
| 策略编排层 | `StrategyFilterContext` | 按客户配置动态执行过滤器 |
| 策略实现层 | `filter/impl` 下 10 个过滤器 | 黑名单、敏感词、扣费、限流、路由等 |
| 远程依赖层 | `BeaconCacheClient` | 调用缓存服务读取客户/路由/风控数据 |
| 消息配置层 | `RabbitMQConfig`, `RabbitTemplateConfig` | 队列声明、转换器、confirm/return |
| 辅助工具层 | `ErrorSendMsgUtil`, `MobileOperatorUtil`, `DFAUtil` 等 | 失败处理、号段查询、敏感词匹配 |

---

## 3. 核心处理流程

## 3.1 消费入口

文件：`beacon-strategy/src/main/java/com/cz/strategy/mq/PreSendListener.java`

流程：

1. 监听 `RabbitMQConstants.SMS_PRE_SEND`。
2. 读取 `StandardSubmit`。
3. 调用 `filterContext.strategy(submit)`。
4. 成功与 `StrategyException` 均 `basicAck`。

注意点：

1. 仅捕获 `StrategyException`。其他运行时异常未显式处理，可能触发容器默认重试/重入行为。

## 3.2 策略链编排

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/StrategyFilterContext.java`

机制：

1. 从缓存 `client_business:{apikey}` 读取 `clientFilters`。
2. 按逗号分割顺序执行对应 `StrategyFilter` Bean。
3. 对历史配置 `black` 做兼容映射：自动执行 `blackGlobal` + `blackClient`。

当前可用策略 Bean 名称：

1. `blackGlobal`
2. `blackClient`
3. `dirtyword`
4. `dfaDirtyWord`
5. `hutoolDFADirtyWord`
6. `fee`
7. `limitOneHour`
8. `phase`
9. `route`
10. `transfer`

---

## 4. 策略实现详细分析

## 4.1 黑名单策略

文件：

1. `BlackGlobalStrategyFilter`
2. `BlackClientStrategyFilter`

行为：

1. 全局黑名单：`black:{mobile}`。
2. 客户黑名单：`black:{clientId}:{mobile}`。
3. 命中后：
   - 写失败日志（`SMS_WRITE_LOG`）。
   - 发送状态报告（`SMS_PUSH_REPORT`，若客户开启回调）。
   - 抛 `StrategyException` 中断后续策略。

## 4.2 敏感词策略

文件：

1. `DirtyWordStrategyFilter`（IK 分词 + 集合交集）
2. `DirtyWordDFAStrategyFilter`（自研 DFA）
3. `DirtyWordHutoolDFAStrategyFilter`（Hutool DFA）

现状差异：

1. `dirtyword` 与 `dfaDirtyWord` 命中后仅记录日志，不抛异常，默认不会拦截发送。
2. `hutoolDFADirtyWord` 命中后会写日志、推回执并抛异常，具备真正阻断行为。

结论：

1. 三个同类策略行为不一致，存在明显语义风险。

## 4.3 扣费策略

文件：`FeeStrategyFilter`

行为：

1. 读取 `submit.fee`。
2. `hIncrBy` 扣减余额。
3. 余额低于阈值时回滚并拒绝。

风险点：

1. 依赖上游保证 `submit.fee` 一定有值，空值防御不足。
2. 欠费阈值来自 `ClientBalanceUtil`，目前硬编码 `-10000L`。

## 4.4 限流策略

文件：`LimitOneHourStrategyFilter`

行为：

1. 仅对验证码短信（`state == CODE_TYPE`）生效。
2. 分别做 1 分钟/1 小时/1 天三层限流。
3. 基于 Redis ZSet 计数，超限则拒绝并回写失败日志与回执。

参数现状：

1. 时区硬编码 `+8`。
2. 阈值硬编码：1 分钟 1 条、1 小时 3 条、1 天 10 条。

## 4.5 号段补齐策略

文件：`PhaseStrategyFilter`

行为：

1. 先查缓存 `phase:{mobile7}`。
2. 未命中调用第三方 `360` 号段接口。
3. 回填归属地和运营商到 `submit`。
4. 查到结果后投递 `MOBILE_AREA_OPERATOR` 供异步回写。

风险点：

1. 强依赖外部接口稳定性。
2. 结果格式解析依赖字符串拼接协议，容错空间有限。

## 4.6 携号转网策略

文件：`TransferStrategyFilter`

行为：

1. 查 `transfer:{mobile}`。
2. 若存在值则覆盖 `operatorId` 并标记 `isTransfer=true`。

## 4.7 路由策略

文件：`RouteStrategyFilter`

行为：

1. 从 `client_channel:{clientId}` 读取客户通道路由集合。
2. 按权重排序选路。
3. 校验绑定关系可用、通道可用、运营商匹配。
4. 封装 `channelId/srcNumber`。
5. 动态声明目标队列并投递网关消息。

风险点：

1. 使用 `TreeSet + compare(weight)`，同权重通道会被去重。
2. `transferChannel` 变量已计算但未参与后续封装，属于无效扩展点。

---

## 5. 失败处理与消息扇出

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/ErrorSendMsgUtil.java`

统一失败动作：

1. `sendWriteLog`：把失败 `submit` 投递到 `SMS_WRITE_LOG`。
2. `sendPushReport`：按客户配置决定是否投递 `SMS_PUSH_REPORT`。

当前风险：

1. `Integer isCallback = ...; if (isCallback == 1)` 存在空值拆箱风险。

---

## 6. 配置与依赖现状

## 6.1 依赖侧

文件：`beacon-strategy/pom.xml`

特征：

1. 依赖 `beacon-common`、Feign、RabbitMQ、IKAnalyzer、Hutool DFA。
2. `groupId` 为 `org.example`，与多数模块 `com.cz` 不一致。

## 6.2 队列配置侧

文件：`beacon-strategy/src/main/java/com/cz/strategy/config/RabbitMQConfig.java`

现状：

1. 监听器消费的是 `SMS_PRE_SEND`。
2. `preSendQueue()` 却声明成 `MOBILE_AREA_OPERATOR` 队列。
3. 本模块对预发送队列声明与消费不一致。

---

## 7. 主要风险与技术债

## 7.1 队列声明不一致（高风险）

文件：`RabbitMQConfig`

1. 声明队列与监听队列不一致，依赖外部服务先行声明，部署顺序敏感。

## 7.2 敏感词策略行为不一致（高风险）

文件：`DirtyWordStrategyFilter`, `DirtyWordDFAStrategyFilter`, `DirtyWordHutoolDFAStrategyFilter`

1. 同类策略有的“仅记录”，有的“直接拦截”，运维和产品认知容易偏差。

## 7.3 路由排序去重缺陷（高风险）

文件：`RouteStrategyFilter`

1. 同权重不同通道会被 `TreeSet` 误判重复。
2. 造成可用通道丢失，影响容灾和分流。

## 7.4 扣费空值防御不足（高风险）

文件：`FeeStrategyFilter`

1. `submit.fee` 为空时可能异常。
2. 与 API internal 入口“可跳过校验链”联动时风险更高。

## 7.5 回调开关空值判断不安全（中风险）

文件：`ErrorSendMsgUtil`

1. `isCallback == 1` 对 null 不安全。

## 7.6 配置硬编码较多（中风险）

文件：

1. `LimitOneHourStrategyFilter`（时区与阈值）
2. `ClientBalanceUtil`（欠费阈值）

影响：

1. 难以按客户或环境精细化运营。

## 7.7 DFA 静态初始化与配置刷新能力弱（中风险）

文件：`DFAUtil`, `HutoolDFAUtil`, `SpringUtil`

1. 敏感词树在静态块初始化时从缓存加载。
2. 运行时敏感词更新后，不会自动刷新该结构。

## 7.8 测试覆盖缺失（中风险）

1. 模块无 `src/test/java`。
2. 策略链变更几乎完全依赖联调验证。

---

## 8. 与已识别风险文档的关系

已有文档：`docs/08_strategy_module_risk_explanation.md`

关系说明：

1. 本文是“模块全景分析”。
2. 该文档是“重点风险深挖”。
3. 两者可并行维护：一个用于整体认知，一个用于整改跟踪。

---

## 9. 改造建议（按优先级）

## P0（优先）

1. 修正 `SMS_PRE_SEND` 队列声明不一致问题。
2. 修复路由 `TreeSet` 去重问题，改为 List 排序并保留同权重元素。
3. 给扣费与回调流程补齐空值防御（`fee`、`isCallback`）。
4. 统一敏感词策略语义（要么都拦截，要么都审计，并明确命名）。

## P1（中期）

1. 限流阈值、时区、欠费阈值配置化（支持全局默认 + 客户覆盖）。
2. 给策略执行增加指标：
   - 每个 filter 的命中率
   - 拒绝率
   - 耗时
3. 明确异常分类：业务拒绝 vs 系统异常，避免重试策略混淆。

## P2（长期）

1. 敏感词 DFA 结构支持热更新。
2. 为策略链建立回归测试矩阵（按 filter 组合 + 配置组合）。
3. 推进策略编排可视化，避免仅靠字符串配置维护链路。

---

## 10. 结论

`beacon-strategy` 是平台稳定性的核心模块，当前功能覆盖全面，且策略链已具备动态配置能力。  
主要问题集中在“一致性与可运营性”：队列契约一致性、同类策略行为一致性、关键参数配置化。  
优先处理 P0 项后，系统可显著降低线上不可预测风险，并为后续策略扩展打下更稳定基础。

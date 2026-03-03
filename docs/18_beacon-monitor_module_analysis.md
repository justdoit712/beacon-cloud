# beacon-monitor 模块详细分析

更新时间：2026-02-26  
适用仓库：`beacon-cloud`

---

## 1. 模块定位

`beacon-monitor` 是平台的“运维监控与告警模块”，通过 XXL-Job 定时任务巡检关键运行状态（队列积压、客户余额等），并通过邮件发出告警。

在整体链路中的位置：

`XXL-Job 调度中心` -> `beacon-monitor` -> `RabbitMQ/Redis(beacon-cache)` -> `邮件告警`

---

## 2. 模块结构

源码目录：`beacon-monitor/src/main/java/com/cz/monitor`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `MonitorStarterApp` | SpringBoot 启动、服务注册、Feign 启用 |
| 调度配置层 | `XxlJobConfig` | XXL-Job 执行器配置 |
| 任务层 | `MonitorQueueMessageCountTask`, `MonitorClientBalanceTask`, `TestTask` | 队列堆积监控、余额监控、示例任务 |
| 外部依赖层 | `CacheClient` | 读取缓存系统中的业务键值 |
| 告警能力层 | `MailUtil` | 邮件发送封装 |

---

## 3. 核心处理流程

## 3.1 队列堆积监控任务

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

调度入口：`@XxlJob("monitorQueueMessageCountTask")`

流程：

1. 调用 `cacheClient.keys("channel:*")` 获取通道键集合。
2. 创建 RabbitMQ `Connection` 与 `Channel`。
3. 先检查 `SMS_PRE_SEND` 队列。
4. 按通道键拼接队列名：`sms_gateway_topic_{channelId}` 并逐个检查。
5. 对每个队列读取消息数，超过阈值 `10000` 则发送告警邮件。

## 3.2 客户余额监控任务

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`

调度入口：`@XxlJob("monitorClientBalanceTask")`

流程：

1. 调用 `cacheClient.keys("client_balance:*")` 获取客户余额键。
2. 逐个 `hGetAll` 读取 `balance` 和 `extend1(email)` 字段。
3. 余额低于阈值 `500000` 时发送低余额告警邮件。

## 3.3 调度与邮件基础设施

文件：

1. `beacon-monitor/src/main/java/com/cz/monitor/config/XxlJobConfig.java`
2. `beacon-monitor/src/main/java/com/cz/monitor/util/MailUtil.java`

说明：

1. XXL-Job 执行器参数由配置注入（admin、appname、ip、port、token 等）。
2. 邮件工具支持默认收件人列表和单独收件人发送两种方式。

---

## 4. 依赖与配置现状

## 4.1 依赖侧

文件：`beacon-monitor/pom.xml`

关键依赖：

1. `xxl-job-core`：任务调度执行器。
2. `spring-boot-starter-amqp`：访问 RabbitMQ 统计队列消息。
3. `spring-cloud-starter-openfeign`：调用缓存服务。
4. `spring-boot-starter-mail`：邮件告警。
5. `beacon-common`：常量与公共能力。

## 4.2 配置侧

文件：`beacon-monitor/src/main/resources/bootstrap.yml`

现状：

1. 服务名、Nacos 地址、环境 profile 已配置。
2. 邮件账号与授权码在配置文件中明文出现。
3. 默认告警收件人列表（`spring.mail.tos`）固定在配置中。

---

## 5. 关键机制分析

## 5.1 监控数据源选择

1. 队列监控：通过 Rabbit Channel 直接读取消息数，实时性较高。
2. 业务对象列表：通过 `beacon-cache` 的 `keys(pattern)` 获取监控对象集合，扩展方便，但对 Redis `KEYS` 性能敏感。

## 5.2 告警模型

1. 目前为“阈值触发即发邮件”的简单模型。
2. 未看到去重、抑制、升级策略，短时间内可能重复告警。

---

## 6. 主要风险与技术债

## 6.1 邮件凭证明文配置（高风险）

文件：`beacon-monitor/src/main/resources/bootstrap.yml`

1. 邮箱账号、授权码、收件人明文存在于仓库配置。
2. 具有明显安全与合规风险。

## 6.2 Rabbit 连接资源未关闭（高风险）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

1. `Connection` 与 `Channel` 创建后未显式关闭。
2. 定时任务长期运行会累积连接资源，存在泄漏风险。

## 6.3 监控任务带副作用（高风险）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

1. 使用 `queueDeclare(...)` 读取前先声明队列。
2. 监控动作会创建不存在的队列或触发参数不一致异常，不符合“只读巡检”原则。

## 6.4 Redis `KEYS` 扫描扩展性风险（中风险）

文件：

1. `beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`
2. `beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`

1. `keys(pattern)` 在大键空间下可能阻塞 Redis。
2. 客户/通道规模增长时监控任务耗时和抖动会显著上升。

## 6.5 阈值与单位语义不清（中风险）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`

1. 注释与常量数值（`500000`）的金额语义不一致。
2. 邮件展示使用 `balance/1000`，单位转换约定不明确。

## 6.6 任务健壮性不足（中风险）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`

1. `balance`、`email` 缺失时无空值防御。
2. 任一客户数据异常可能中断整批任务。

## 6.7 告警风暴风险（中风险）

1. 同一队列/客户每次执行都可能重复发送邮件。
2. 缺少冷却窗口、去重键和升级策略。

## 6.8 XXL-Job 配置项未完全使用（低风险）

文件：`beacon-monitor/src/main/java/com/cz/monitor/config/XxlJobConfig.java`

1. `address` 已注入但未设置到执行器。
2. 配置一致性较弱，可能造成环境排障困难。

## 6.9 自动化测试缺失（中风险）

1. 无任务逻辑测试与集成测试。
2. 关键告警路径（边界值、异常值、重复发送）缺少回归保障。

---

## 7. 与上下游契约关系

## 7.1 上游契约

1. XXL-Job 必须正确下发任务触发。
2. `beacon-cache` 需提供 `keys` 与 `hGetAll` 的可用性与数据结构稳定性。

## 7.2 下游契约

1. RabbitMQ 提供队列消息统计能力。
2. 邮件服务器（SMTP）需稳定可达，且授权码有效。

---

## 8. 改造建议（按优先级）

## P0（优先）

1. 邮件凭证迁移到安全配置（环境变量/密钥中心），移除仓库明文敏感信息。
2. 任务中使用 `try-finally` 或 `try-with-resources` 关闭 Rabbit `Connection/Channel`。
3. 队列巡检改为“无副作用”模式（`queueDeclarePassive` 或管理 API）。
4. 对余额和邮箱字段增加空值与格式校验，避免单条脏数据影响整批巡检。

## P1（中期）

1. 告警策略增加冷却窗口和去重键（例如 30 分钟内同对象只告警一次）。
2. 阈值与单位配置化，明确金额单位（分/厘）并统一展示换算。
3. 将 `KEYS` 查询替换为可分页扫描机制（SCAN 或索引集合）。

## P2（长期）

1. 构建统一告警中心（邮件/IM/短信多通道，支持等级与升级）。
2. 接入可观测平台，输出任务执行耗时、失败率、告警命中率指标。
3. 对巡检任务建立回归测试与压测基线，提升长期可运维性。

---

## 9. 推荐治理指标

1. `monitor.task.duration`：各任务执行耗时。
2. `monitor.queue.backlog.count`：队列堆积数量分布。
3. `monitor.low.balance.client.count`：低余额客户数趋势。
4. `monitor.alert.sent.count` 与 `monitor.alert.dedup.hit`：告警发送与去重命中。
5. `monitor.task.error.count`：任务异常次数。

---

## 10. 结论

`beacon-monitor` 已具备基础巡检与告警能力，能够覆盖平台关键运行风险点。  
当前主要问题在“监控工程化成熟度”：安全配置、资源管理、无副作用巡检、告警治理与数据规模适配。  
优先完成 P0 后，模块可从“可用告警”升级为“可持续运维告警”。


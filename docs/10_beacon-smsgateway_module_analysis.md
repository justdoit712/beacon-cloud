# beacon-smsgateway 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-smsgateway  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-smsgateway` 是短信平台中“策略层与运营商通道之间”的协议网关模块，主要职责是把平台内部统一消息（`StandardSubmit`）转换成 CMPP 协议报文并与运营商交互，再把运营商回执反向转换成平台内部状态流。

在整体链路中的位置：

`beacon-strategy` -> `SMS_GATEWAY_*` -> `beacon-smsgateway` -> `CMPP运营商`  
`CMPP运营商回执` -> `beacon-smsgateway` -> `SMS_WRITE_LOG` / `sms_gateway_normal_exchange` -> `beacon-search`

---

## 2. 模块结构

源码目录：`beacon-smsgateway/src/main/java/com/cz/smsgateway`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `SmsGatewayStarterApp` | SpringBoot 启动、Nacos 注册、Feign 启用、Hippo4j 动态线程池启用 |
| MQ 接入层 | `SmsGatewayListener` | 监听网关发送队列，封装 CMPP 提交报文并下发 |
| CMPP 连接层 | `NettyStartCMPP`, `NettyClient` | 建立 TCP 长连接、登录、重连、写报文 |
| 协议编解码层 | `CMPPEncoder`, `CMPPDecoder`, `Command`, `MsgUtils` | CMPP 报文编码、解码与命令字识别 |
| 协议业务处理层 | `CMPPHandler`, `HeartHandler` | 处理提交响应、状态报告、链路心跳 |
| 回执异步处理层 | `SubmitRepoRunnable`, `DeliverRunnable` | 首次响应/最终状态处理与 MQ 扇出 |
| 线程池配置层 | `ThreadPoolConfig` | `cmpp-submit` 与 `cmpp-deliver` 动态线程池 |
| 消息基础设施层 | `RabbitMQConfig` | 正常队列 + TTL + 死信交换机/队列 |
| 远程依赖层 | `BeaconCacheClient` | 回查客户回调配置（`beacon-cache`） |
| 辅助工具层 | `SpringUtil` | 非 Spring 管理类中获取 Bean 的桥接工具 |

---

## 3. 核心处理流程

## 3.1 下行发送入口（MQ -> CMPP_SUBMIT）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java`

流程：

1. 监听 `${gateway.sendtopic}` 指定队列，接收 `StandardSubmit`。
2. 生成 CMPP 序列号 `sequence`（`MsgUtils.getSequence()`）。
3. 构建 `CmppSubmit` 报文并投递到 `NettyClient.submit(...)`。
4. 在 `CMPPSubmitRepoMapUtil` 中临时缓存 `sequence -> submit`，用于后续首次响应关联。
5. 对 RabbitMQ 消息执行 `basicAck`。

要点：

1. 模块以“异步受理”为主，不在消费侧同步等待运营商最终送达。
2. 首次响应与最终状态分别由不同回执路径处理。

## 3.2 Netty 建链与心跳

文件：

1. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java`
3. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClientInitializer.java`
4. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/HeartHandler.java`

流程：

1. Spring 初始化时创建 `NettyClient` 并调用 `start()` 建链。
2. 建链后发送 `CmppConnect` 登录运营商。
3. Pipeline 中包含帧解码、心跳检测、CMPP 编码/解码、业务处理器。
4. 连接空闲时触发 `CmppActiveTest` 心跳包。
5. 连接断开时触发重连逻辑。

## 3.3 首次响应处理（CMPP_SUBMIT_RESP）

文件：

1. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPHandler.java`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/SubmitRepoRunnable.java`

流程：

1. `CMPPHandler` 收到 `CmppSubmitResp`。
2. 投递到 `cmppSubmitPool` 执行 `SubmitRepoRunnable`。
3. 从 `CMPPSubmitRepoMapUtil` 取出原始 `submit`。
4. 若运营商受理失败：填充 `reportState=FAIL` 与错误信息。
5. 若运营商受理成功：组装 `StandardReport` 并写入 `CMPPDeliverMapUtil(msgId -> report)`，等待最终状态。
6. 统一投递 `SMS_WRITE_LOG`，由搜索模块写日志。

## 3.4 最终状态处理（CMPP_DELIVER）

文件：

1. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPHandler.java`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/DeliverRunnable.java`
3. `beacon-smsgateway/src/main/java/com/cz/smsgateway/config/RabbitMQConfig.java`

流程：

1. 收到运营商状态报告（`registered_Delivery == 1`）后，提交 `DeliverRunnable`。
2. 根据 `msgId` 从 `CMPPDeliverMapUtil` 取出报告对象。
3. 把运营商状态码转换为平台状态（成功/失败+原因）。
4. 若客户开启回调：发送 `SMS_PUSH_REPORT`。
5. 同时发送到 `sms_gateway_normal_exchange`，经 normal queue 的 TTL 延时后进入 dead queue，触发搜索模块更新日志状态。

---

## 4. 关键机制与实现特征

## 4.1 双阶段状态模型

1. 第一阶段：运营商“接单结果”（`CmppSubmitResp`）。
2. 第二阶段：运营商“最终送达结果”（`CmppDeliver`）。
3. 通过两张临时 Map（`sequence -> submit`、`msgId -> report`）完成跨阶段关联。

## 4.2 死信延时更新机制

1. 正常队列 `SMS_GATEWAY_NORMAL_QUEUE` 配置 TTL（10 秒）。
2. 到期后自动进入 `SMS_GATEWAY_DEAD_QUEUE`。
3. 搜索模块监听死信队列并更新原日志状态，实现“延迟二次更新”。

## 4.3 动态线程池

1. `SubmitRepoRunnable` 与 `DeliverRunnable` 分别隔离线程池。
2. 线程池可通过 Hippo4j 动态调优，具备在线扩缩容能力。

---

## 5. 依赖与配置现状

## 5.1 依赖侧

文件：`beacon-smsgateway/pom.xml`

关键依赖：

1. `spring-boot-starter-amqp`（MQ）。
2. `netty-all 4.1.69.Final`（协议连接）。
3. `spring-cloud-starter-openfeign`（回查缓存服务）。
4. `hippo4j-spring-boot-starter`（动态线程池）。
5. `beacon-common`（模型、常量、工具）。

## 5.2 配置侧

文件：

1. `beacon-smsgateway/src/main/resources/bootstrap.yml`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java`

现状：

1. 服务注册与配置中心地址在 `bootstrap.yml`。
2. CMPP 关键参数（host/port/serviceId/pwd）仍硬编码在 Java 类中。
3. 消费队列依赖外部配置 `${gateway.sendtopic}`，模块自身不声明该业务队列。

---

## 6. 主要风险与技术债

## 6.1 MQ Ack 与发送结果解耦（高风险）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java`

1. `nettyClient.submit(...)` 的返回值未参与 Ack 决策。
2. 当连接不可写时仍可能 Ack，导致消息已出队但未实际下发。
3. 下发失败时 `CMPPSubmitRepoMapUtil` 中残留数据，形成内存累积风险。

## 6.2 临时状态缓存仍有进程内一致性风险（中风险）

文件：

1. `beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java`
2. `beacon-common/src/main/java/com/cz/common/util/CMPPDeliverMapUtil.java`

1. 当前通过本地缓存承载提交/回执关联关系。
2. 进程重启后上下文仍会丢失。
3. 多实例部署下状态仍不能共享。

## 6.3 Runnable 空指针防御不足（高风险）

文件：

1. `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/SubmitRepoRunnable.java`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/DeliverRunnable.java`

1. `remove(...)` 后直接使用对象字段，缺少空值判断。
2. 关联关系缺失时会触发 NPE，导致状态链路中断。

## 6.4 Netty 重连资源泄露风险（高风险）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java`

1. `doConnect()` 每次创建新的 `NioEventLoopGroup`，无显式 `shutdownGracefully()`。
2. 多次重连可能导致线程与内存泄露。

## 6.5 重连逻辑阻塞风险（中风险）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/HeartHandler.java`

1. `channelInactive` 直接调用 `client.reConnect(10)`，内部包含 `sleep` 循环。
2. 若在 I/O 线程执行，可能阻塞 Netty 事件处理。

## 6.6 CMPP 配置硬编码（中风险）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java`

1. 账号与密码硬编码，存在安全与运维变更风险。
2. 环境切换需改代码重新发布，不符合配置中心治理模型。

## 6.7 测试接口暴露（中风险）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/controller/TestController.java`

1. 保留 `/test` HTTP 入口，生产环境容易成为无意暴露面。

## 6.8 自动化测试缺失（中风险）

1. 模块缺少 `src/test/java` 覆盖。
2. 核心链路（重连、回执、死信延迟）缺乏回归保障。

---

## 7. 与上下游模块的契约关系

## 7.1 上游契约（strategy -> smsgateway）

1. 上游应保证 `StandardSubmit` 中 `srcNumber/mobile/text/sequenceId/apikey` 等字段完整。
2. 队列名称来自 `${gateway.sendtopic}`，需在配置中心与策略模块路由保持一致。

## 7.2 下游契约（smsgateway -> search/api）

1. 向 `SMS_WRITE_LOG` 写入日志基础信息。
2. 向 `SMS_GATEWAY_NORMAL_EXCHANGE` 写入延迟更新事件（最终状态刷新）。
3. 向 `SMS_PUSH_REPORT` 发送客户回调事件（由 API/其他模块消费）。

---

## 8. 改造建议（按优先级）

## P0（优先）

1. 把 Ack 与下发结果绑定：`submit()` 失败时 `basicNack/requeue` 或进入补偿队列。
2. 给 `SubmitRepoRunnable`/`DeliverRunnable` 增加空值保护与降级日志，避免 NPE 中断链路。
3. 继续把临时关联存储从“本地可过期缓存”演进到“可共享、可恢复的状态存储”（如 Redis）。
4. 修复 Netty 连接生命周期：复用 `EventLoopGroup`，并在重连或停机时显式关闭。

## P1（中期）

1. CMPP 连接参数与凭证全部迁移到 Nacos/密钥系统，禁用硬编码敏感信息。
2. 增加发送链路可观测性指标：连接状态、提交成功率、回执延迟、Map 占用量。
3. 清理或保护测试接口（按环境开关、鉴权或移除）。

## P2（长期）

1. 引入“可靠消息+状态机”模型替代双 Map 临时态（可审计、可重放、可恢复）。
2. 构建网关回归测试基线：协议编解码、断线重连、延时更新、异常补偿。
3. 预留多通道抽象（CMPP/HTTP/厂商SDK）以支撑通道扩展。

---

## 9. 推荐治理指标

1. `gateway.submit.accept.rate`：运营商首次受理成功率。
2. `gateway.deliver.success.rate`：最终送达成功率。
3. `gateway.submitrepo.map.size` 与 `gateway.deliver.map.size`：临时态占用趋势。
4. `gateway.reconnect.count`：重连频次与失败次数。
5. `gateway.deadqueue.delay.seconds`：从下发到状态落库的端到端延迟。

---

## 10. 结论

`beacon-smsgateway` 已实现 CMPP 协议交互、双阶段回执处理与延迟状态更新，是平台“可发出去”的关键模块。  
当前主要问题不是功能缺失，而是可靠性边界尚不收敛：Ack 策略、临时态治理、重连资源管理、配置安全。  
先完成 P0 后，网关链路的可恢复性与一致性会明显提升，并显著降低“消息丢失/状态错乱”的线上风险。

# beacon-smsgateway 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-smsgateway  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `10_beacon-smsgateway_module_analysis.md`
2. `08_beacon-smsgateway_refactor_guide.md`

## 1. 模块定位

`beacon-smsgateway` 是平台的运营商网关适配层，核心职责：

1. 消费策略模块投递的下发消息（`sms_gateway_topic_{channelId}`）。
2. 将 `StandardSubmit` 转换为 CMPP 报文并通过 Netty 长连接发送给运营商。
3. 处理运营商返回的两阶段响应（提交应答 + 状态报告）。
4. 发送日志消息、回调消息和延迟更新消息到 MQ。

核心入口文件：

1. 启动类：`beacon-smsgateway/src/main/java/com/cz/smsgateway/SmsGatewayStarterApp.java`
2. 消费入口：`beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java`
3. Netty 客户端：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java`
4. 协议编解码：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPDecoder.java`、`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPEncoder.java`
5. 运营商应答处理：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPHandler.java`
6. 异步任务：`beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/SubmitRepoRunnable.java`、`beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/DeliverRunnable.java`
7. MQ 配置：`beacon-smsgateway/src/main/java/com/cz/smsgateway/config/RabbitMQConfig.java`

---

## 2. 当前实现概览

## 2.1 主业务链路

1. `SmsGatewayListener.consume(...)` 接收 `StandardSubmit`。
2. 构造 `CmppSubmit`，并将 `submit` 暂存到 `CMPPSubmitRepoMapUtil`。
3. 通过 `NettyClient.submit(...)` 发送给运营商。
4. `CMPPHandler` 收到 `CmppSubmitResp` 后执行 `SubmitRepoRunnable`。
5. `CMPPHandler` 收到 `CmppDeliver`（状态报告）后执行 `DeliverRunnable`。
6. `DeliverRunnable` 发送 `SMS_PUSH_REPORT` 和延迟更新消息。

## 2.2 当前模块特征

1. 与运营商交互协议在本模块手写实现（高复杂、低容错）。
2. 上下文关联当前依赖本地 Caffeine 临时缓存（有过期和容量上限，但仍非持久、非共享）。
3. 并发由 Hippo4j 动态线程池管理，但线程池参数未在代码层显式约束。

---

## 3. 需要重构的代码、原因与改造方案

## 3.1 P0：运营商连接参数硬编码在代码中（安全与运维风险）

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java:11`

```java
public static String host = "127.0.0.1";
public static int port = 7890;
public static String serviceId = "laozheng";
public static String pwd = "JavaLaoZheng123!";
```

### 原因

1. 账号密码进入代码仓库，存在泄漏风险。
2. 环境切换（dev/test/prod）需要改代码，发布成本高。
3. 不支持多通道动态配置与热更新。

### 如何重构

1. 用 `@ConfigurationProperties(prefix = "cmpp")` 承接配置。
2. 敏感字段放 Nacos 密文或环境变量，不落仓库。
3. 允许按 `channelId` 维护多套连接配置（后续支持多通道网关）。

### 目标代码（建议）

```java
@Bean(initMethod = "start", destroyMethod = "shutdown")
public NettyClient nettyClient(CmppProperties props) {
    return new NettyClient(props.getHost(), props.getPort(), props.getServiceId(), props.getPassword());
}
```

---

## 3.2 P0：MQ 消费确认时机过早，发送失败可能丢消息

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java:44`

```java
CMPPSubmitRepoMapUtil.put(sequence,submit);
nettyClient.submit(cmppSubmit);
channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
```

### 原因

1. 未检查 `nettyClient.submit(...)` 返回值（`false` 时也 ack）。
2. 没有 `try/catch` 包裹提交流程，异常场景依赖容器默认行为。
3. 先 `put` 再发送，发送失败会残留临时缓存，造成内存堆积。

### 如何重构

1. 提交失败时 `basicNack` 或进入重试队列，而不是直接 ack。
2. 将“暂存 -> 发送 -> ack”设计为可观测状态流（记录 sid + sequence）。
3. 对 `submit` 返回值和异常分别处理。

### 目标代码（建议）

```java
boolean sent = nettyClient.submit(cmppSubmit);
if (!sent) {
    channel.basicNack(tag, false, true);
    return;
}
channel.basicAck(tag, false);
```

---

## 3.3 P0：应答处理缺少空值防御，存在 NPE 中断风险

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/SubmitRepoRunnable.java:36`

```java
StandardSubmit submit = CMPPSubmitRepoMapUtil.remove(submitResp.getSequenceId());
submit.setReportState(...);
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/runnable/DeliverRunnable.java:38`

```java
StandardReport report = CMPPDeliverMapUtil.remove(msgId + "");
report.setReportState(...);
Integer isCallback = cacheClient.hgetInteger(... + report.getApiKey(), "isCallback");
```

### 原因

1. 当运营商回执超时、重复、乱序或缓存丢失时，`remove(...)` 可能返回 `null`。
2. 当前代码直接解引用，线程池任务会抛 NPE，导致后续逻辑中断。

### 如何重构

1. `submit/report` 判空后走“异常兜底日志 + 补偿队列”。
2. 为回执处理加入统一错误捕获与计数监控。
3. 记录关联键（`sequence`、`msgId`、`sid`）便于排障。

### 目标代码（建议）

```java
if (submit == null) {
    log.warn("submit not found, sequence={}", submitResp.getSequenceId());
    return;
}
```

---

## 3.4 P0：本地关联缓存仍存在一致性上限

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java:14`

```java
private static final Cache<Integer, StandardSubmit> CACHE = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(500_000)
        .build();
```

文件：`beacon-common/src/main/java/com/cz/common/util/CMPPDeliverMapUtil.java:14`

```java
private static final Cache<String, StandardReport> CACHE = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(500_000)
        .build();
```

### 原因

1. 进程重启后上下文丢失、回执无法关联。
2. 多实例部署下本地缓存仍无法共享。
3. 关联状态仍缺乏可恢复、可审计的持久化能力。

### 如何重构

1. 长期方案建议迁移到 Redis 或状态表，解决多实例与重启恢复问题。
2. 增加“提交后超时清理”与“回执未匹配率”监控。

---

## 3.5 P0：Netty 重连机制阻塞 I/O 线程且泄漏 EventLoopGroup

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/HeartHandler.java:43`

```java
client.reConnect(10);
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java:81`

```java
Thread.sleep(10 * 1000);
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java:108`

```java
EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
```

### 原因

1. `channelInactive` 里直接重连 + `sleep`，会阻塞 Netty 事件线程。
2. 每次 `doConnect` 都 new 新 `NioEventLoopGroup`，无统一 shutdown，线程泄漏风险高。
3. 重连逻辑耦合在业务线程中，恢复策略不可控。

### 如何重构

1. `EventLoopGroup` 提升为客户端级单例字段，应用关闭时优雅释放。
2. 使用 `eventLoop.schedule(...)` 做非阻塞重连。
3. 增加指数退避、最大重试、断线告警。

### 目标代码（建议）

```java
ctx.channel().eventLoop().schedule(() -> client.start(), 10, TimeUnit.SECONDS);
```

---

## 3.6 P0：CMPP Connect 版本字节不一致，存在协议兼容风险

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppConnect.java:18`

```java
super(Command.CMPP_CONNECT, Command.CMPP2_VERSION);
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppConnect.java:38`

```java
buf.writeByte(1);
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/utils/Command.java:138`

```java
public static final byte CMPP2_VERSION = (byte) 32;
```

### 原因

1. 构造时声明 CMPP2.0，但编码时写入版本 `1`，协议值不一致。
2. 可能导致部分运营商网关鉴权失败或连接不稳定。

### 如何重构

1. 统一使用 `Command.CMPP2_VERSION`（或可配置版本）。
2. 在连接建立日志中输出实际 version 值。
3. 增加协议握手集成测试。

---

## 3.7 P1：`CmppSubmit` 报文字段封装存在硬编码与遗漏

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppSubmit.java:106`

```java
this.srcId = srcId + "1630";
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppSubmit.java:133`

```java
buf.writeBytes(MsgUtils.getLenBytes(serviceId, 10));
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppSubmit.java:140`

```java
buf.writeBytes(MsgUtils.getLenBytes(serviceId, 6));
```

### 原因

1. `serviceId` 字段声明后未在构造器赋值，可能写入空值。
2. `srcId + "1630"` 硬编码后缀不可配置，影响不同通道规范。
3. 报文字段语义与业务模型耦合过强，维护难度高。

### 如何重构

1. 定义 `CmppSubmitBuilder`，显式校验必填字段。
2. 将 `srcId` 拼接规则配置化（按通道模板拼接）。
3. 对关键字段长度、编码做前置校验，失败拒绝下发。

---

## 3.8 P1：Cache Feign 契约弱类型且同路径多返回类型

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/client/BeaconCacheClient.java:14`

```java
@GetMapping("/cache/hget/{key}/{field}")
Integer hgetInteger(...);

@GetMapping("/cache/hget/{key}/{field}")
String hget(...);
```

### 原因

1. 同一路径映射不同返回类型，反序列化不稳定。
2. 出错时排查成本高，跨模块接口演进困难。

### 如何重构

1. 联动 `beacon-cache` 提供 typed V2 接口。
2. 网关模块引入 `CacheFacade`，统一空值/类型转换。

---

## 3.9 P1：日志与异常处理不规范（大量 `System.out`/`printStackTrace`）

### 现状代码（需要重构）

文件：

1. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/CMPPDecoder.java:41`
2. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/HeartHandler.java:23`
3. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyClient.java:42`
4. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/entity/CmppDeliver.java:100`
5. `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/utils/MsgUtils.java:55`

### 原因

1. stdout 不利于结构化日志聚合和告警。
2. `printStackTrace` 破坏日志上下文，不能统一检索 traceId/sid。

### 如何重构

1. 统一替换为 `Slf4j`。
2. 日志字段统一：`sid`、`sequence`、`msgId`、`channelId`、`apiKey`。
3. 关键异常使用 error 级别 + 指标埋点。

---

## 3.10 P1：生产路径暴露测试接口

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/controller/TestController.java:19`

```java
@GetMapping("/test")
public String test(){ ... }
```

### 原因

1. `/test` 在生产包可访问，存在误触发风险。
2. 方法内部仍使用 `System.out.println`。

### 如何重构

1. 删除该控制器，或迁移到 `test` profile。
2. 线程池验证放到单元/集成测试，不暴露公网接口。

---

## 3.11 P2：RabbitMQ 参数与序列化策略建议配置化

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/config/RabbitMQConfig.java:19`

```java
private final int TTL = 10000;
```

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/config/RabbitMQConfig.java:59`

```java
return new Jackson2JsonMessageConverter();
```

### 原因

1. TTL 固定写死，无法按环境或业务阶段调优。
2. 序列化策略未显式声明时间字段行为，跨模块兼容性依赖默认配置。

### 如何重构

1. TTL、交换机参数配置化。
2. 统一 ObjectMapper（与其他模块一致）并固定时间序列化策略。

---

## 3.12 P2：线程池参数未显式声明，容量边界不清晰

### 现状代码（需要重构）

文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/config/ThreadPoolConfig.java:21`

```java
ThreadPoolBuilder.builder()
    .threadPoolId(threadPoolId)
    .dynamicPool()
    .build();
```

### 原因

1. 未显式设置 core/max/queue/rejection，容量与降级行为不透明。
2. 高峰期容易出现堆积或拒绝策略不可预期。

### 如何重构

1. 在配置中心明确线程池参数（默认值 + 环境覆盖）。
2. 增加线程池指标告警（活跃线程、队列长度、拒绝次数）。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，优先保稳定与正确）

1. 去除硬编码连接参数，改为配置中心密文。
2. 修复 MQ ack 时机与 Netty 提交失败处理。
3. 补齐 `SubmitRepoRunnable`/`DeliverRunnable` 的判空防御。
4. 重构 Netty 重连机制（非阻塞 + 统一 eventLoop 生命周期）。
5. 修正 `CmppConnect` 版本字节一致性。

## 阶段二（P1，提升契约与可维护性）

1. 修复 `CmppSubmit` 字段封装问题（serviceId/sourceAddr 规则）。
2. 迁移 cache 客户端到 typed Feign 契约。
3. 替换所有 `System.out`/`printStackTrace` 为结构化日志。
4. 下线 `TestController`。

## 阶段三（P2，治理与优化）

1. MQ TTL/序列化配置化。
2. 线程池参数与监控体系完善。
3. 本地 map 过渡到带 TTL 的缓存或 Redis。

---

## 5. 建议测试清单

## 5.1 单元测试

1. `CmppConnect`：版本、鉴权字段、时间戳格式。
2. `CmppSubmit`：字段长度、编码、报文总长计算。
3. `SubmitRepoRunnable`/`DeliverRunnable`：空上下文、失败路径、回调开关路径。

## 5.2 集成测试

1. MQ 消费失败时 ack/nack 行为验证。
2. Netty 断连重连与心跳行为验证。
3. 运营商提交应答 + 状态报告完整链路回归。

## 5.3 压测与故障演练

1. 高频下发场景下线程池与 map 容量压测。
2. 运营商连接抖动场景重连稳定性演练。
3. 回执乱序/重复/丢失场景容错验证。

---

## 6. 跨模块联动建议

1. 与 `beacon-strategy`：明确 `sid/sequence/msgId` 三段关联规范，降低回执错配率。
2. 与 `beacon-cache`：上线 typed cache API，统一回调配置读取契约。
3. 与 `beacon-search`：统一延迟更新语义与幂等键，避免日志重复写入。
4. 与 `beacon-common`：将 `CMPP*MapUtil` 升级为带过期和容量控制的组件。

建议按“先控制风险、再统一契约、最后做治理优化”的节奏推进，避免一次性重写 Netty/CMPP 导致生产不稳定。


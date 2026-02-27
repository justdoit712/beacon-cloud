# beacon-cloud 短信业务扩展与架构演进建议（技术驱动型业务架构师版）

## 文档目标

基于当前代码库现状（单条短信发送为核心业务），给出面向未来 12-18 个月可持续扩展的业务推演与架构规划，重点围绕：
- 支撑“千万级群发”
- 支撑“定时发送/分时发送”
- 保持现有链路（API -> 策略 -> 网关 -> 搜索/回调）可渐进演进，而不是推倒重来

---

## 一、业务扩展路线图（Business Expansion Ideas）

### 1.1 建议优先扩展方向（4 个）

| 方向 | 业务价值 | 典型场景 | 对现有底座复用度 | 实现难度 |
|---|---|---|---|---|
| A. 千万级群发（批次任务） | 直接放大营收规模，进入营销与活动通知主战场 | 会员营销、活动触达、账单通知批量下发 | 中（可复用策略/网关/回调） | 高 |
| B. 定时/分时发送（Schedule + Window） | 提升到达率并降低投诉；支持夜间避发、地域时区控制 | 次日 9:00 批量发送、按时区滚动发送 | 中高（已有 Quartz 基础） | 中 |
| C. 动态变量模板短信（模板+参数） | 大幅提升内容合规与运营效率；便于 A/B 实验 | 验证码、订单状态、营销变量化文案 | 高（已有签名/模板校验逻辑） | 中 |
| D. 上行短信回复（MO）与会话闭环 | 从“单向通知”升级为“双向交互平台”，提升客户留存与自动化服务能力 | 用户回复退订、关键词回复、工单/投票 | 低中（网关具备 Deliver 处理基础） | 中高 |

### 1.2 每个方向的业务推演

#### A. 千万级群发（批次任务）

业务价值：
- 从“单请求单号码”扩展到“单任务多号码”，使运营活动可规模化。
- 可引入“按客户计费 + 按通道成本优化 + 发送节流服务”，业务模型更完整。

推荐能力包：
- 批次创建（上传文件/人群包引用）
- 批次拆分（分片、并发窗口）
- 发送限速（按客户、按通道、按模板）
- 进度追踪（批次总量、成功/失败、实时吞吐）

实现难度评估：高
- 现有链路是单条对象流（`StandardSubmit`）并且管理端逐手机号同步调用，架构上缺少“大任务异步拆分层”。

#### B. 定时/分时发送（Schedule + Window）

业务价值：
- 在法规/运营要求下控制发送时段，减少夜间扰民和投诉。
- 按时区/业务窗口发送，提升打开率和转化。

推荐能力包：
- 定时发送（单次）
- 周期发送（Cron/日历）
- 发送窗口（例如 09:00-21:00）
- 过期策略（过时取消/立即补发）

实现难度评估：中
- 已有 Quartz 调度体系（`beacon-webmaster`），但需从“通用反射任务”演进为“短信域任务模型 + 可靠投递”。

#### C. 动态变量模板短信

业务价值：
- 降低内容拼接错误和审核风险，提升短信合规性。
- 支持同模板多变量批量下发，减少人工配置成本。

推荐能力包：
- 模板版本管理（草稿/生效/下线）
- 参数强校验（必填、长度、类型、敏感词）
- 预渲染与审计留痕（模板 + 参数快照）

实现难度评估：中
- API 层已有签名/模板校验链，可扩展成“模板中心 + 参数渲染中心”。

#### D. 上行短信回复（MO）与会话闭环

业务价值：
- 从通知平台升级为交互平台，支持关键词自动回复、退订、问答等。
- 为后续自动化营销（订阅、二次触达）提供闭环数据。

推荐能力包：
- MO 消息接入与归档
- 关键词路由（退订、人工、机器人）
- 上行与下行关联（会话视图）

实现难度评估：中高
- 需要新增 MO 数据模型、规则引擎、回调安全与幂等处理。

---

## 二、核心代码瓶颈与重构点（Gap Analysis & Refactoring）

> 目标场景：升级到“高并发大批量群发 + 定时发送”能力。

### 2.1 性能与并发瓶颈

### 2.1.1 入口链路瓶颈（管理端）

现状证据：
- 管理端批量发送上限固定 500（`MAX_BATCH_SIZE=500`）：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:30`
- 对手机号逐条 `for` 循环、逐条调用内部接口：
  - `for (String mobile : mobiles)`：`:125`
  - `apiSmsClient.singleSend(...)`：`:138`

问题：
- 这是“同步 N 次远程调用”模型，吞吐线性受限于 HTTP 往返与线程池。
- 无法承载万级以上批次，且容易放大 API 层瞬时压力。

结论：
- 缺少“批次异步受理层 + 拆分缓冲层”。

### 2.1.2 API 层瓶颈与行为不一致

现状证据：
- 仅提供单条接口：`/single_send` 与 `/internal/single_send`（`SmsController.java:62,79`）
- 外部接口执行校验链：`checkFilterContext.check(submit)`（`:75`）
- 内部接口直接入队，不走校验链（`:97-99`）
- 默认校验过滤器不包含 `fee`：`filters:apikey,ip,sign,template`（`CheckFilterContext.java:26`）

问题：
- 内部链路字段完备性依赖调用方，易出现 `fee` 等字段缺失。
- 策略扣费逻辑直接读取 `submit.getFee()` 并参与数值运算（`FeeStrategyFilter.java:31,34`），在大批量场景下可能触发异常或逻辑偏差。

结论：
- 需要统一“受理层字段标准化”与“扣费前置校验”。

### 2.1.3 策略与 MQ 瓶颈

现状证据：
- 策略路由时每条消息动态 `declareQueue`（`RouteStrategyFilter.java:108`）
- 然后按 `sms_gateway_topic_{channelId}` 投递（`:111`）
- `PreSendListener` 对 `StrategyException` 也直接 ack（`PreSendListener.java:34,36`）

问题：
- 高频动态声明队列会带来 Broker 元数据操作开销。
- 失败即 ack，缺少可配置重试/补偿策略，极端情况下影响可靠性与排障能力。

结论：
- 需要稳定化队列拓扑（预建队列）+ 标准重试死信策略。

### 2.1.4 网关并发与状态关联瓶颈

现状证据：
- 网关监听固定配置队列 `${gateway.sendtopic}`（`SmsGatewayListener.java:31`）
- 外发序列号为进程内自增 int（`MsgUtils.getSequence`，`MsgUtils.java:25-30`）
- Submit/Deliver 关联使用本地 Caffeine 10 分钟缓存：
  - `CMPPSubmitRepoMapUtil`（`expireAfterWrite 10m`）
  - `CMPPDeliverMapUtil`（`expireAfterWrite 10m`）
- CMPP 连接参数硬编码在代码中（`NettyStartCMPP.java:11-17`）

问题：
- 网关扩容后会出现“本地内存状态分散”，重启会丢失关联上下文。
- 大批量回执下，进程内缓存命中与时序窗口风险上升。
- 配置硬编码不利于多通道、多环境和密钥治理。

结论：
- 需要将“提交-回执关联状态”外部化（Redis/DB）并引入网关实例可水平扩展模型。

### 2.1.5 搜索与回调链路瓶颈

现状证据：
- `SmsWriteLogListener` 每条消息 `new ObjectMapper()`（`SmsWriteLogListener.java:33`）
- ES 写入只接受 `CREATED`，否则抛异常（`ElasticsearchServiceImpl.java:46,69,72`）
- 回调推送固定 5 次延迟重试（`PushReportListener.java:35,136`）

问题：
- 高频下会产生不必要对象创建与 CPU/GC 消耗。
- 幂等重入时写入策略不够友好（应支持 upsert 语义）。

结论：
- 需要完善幂等与性能优化，避免“批量业务上线后非核心模块先成为瓶颈”。

### 2.2 当前是否缺乏必要缓冲机制（削峰填谷）

答案：是，且是升级群发/定时能力前必须补齐的关键缺口。

当前状态：
- 有 MQ，但缺少“批次受理 -> 拆分 -> 分发”的多级缓冲模型。
- 入口仍是同步逐条调用，不具备天然削峰。

建议目标模型：
1. 受理队列（Ingress Queue）：只做快速接收与持久化确认。
2. 拆分队列（Split Queue）：批次切片为单条或小批任务。
3. 分发队列（Dispatch Queue）：按客户/通道限速推送策略链。 
4. 回执与回调队列（Report/Callback Queue）：异步更新和重试。

这样可以把“流量突刺”在中间层消化，避免直接压垮 API/策略/网关。

### 2.3 核心 Class 演进建议（支持群发 + 定时）

> 关键原则：
> - 不建议把“超大手机号列表”直接塞进现有 `StandardSubmit` 作为通道对象。
> - 应拆分为“批次受理对象（Batch）”和“单条分发对象（Dispatch）”。

#### 2.3.1 批次受理对象（新增）

```java
package com.cz.common.model.v2;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SmsBatchSubmitCommand implements Serializable {

    // 业务标识
    private Long batchId;                 // 批次ID
    private Long campaignId;              // 活动ID（可选）
    private String bizType;               // 验证码/通知/营销

    // 租户与鉴权
    private Long clientId;
    private String apiKey;
    private String operator;

    // 发送目标
    private List<String> mobileList;      // 小批次可直接带
    private String mobileFileUrl;         // 大批次建议文件/对象存储引用
    private Integer totalCount;

    // 内容模型
    private Long templateId;
    private Map<String, Object> templateParams;
    private String sign;
    private Long signId;
    private String text;                  // 非模板模式下使用

    // 定时模型
    private LocalDateTime scheduleAt;     // 计划触发时间
    private String timezone;              // 例如 Asia/Shanghai
    private LocalDateTime expireAt;       // 过期时间

    // 流控与幂等
    private Integer priority;             // 优先级
    private Integer maxQps;               // 批次级限速
    private String idempotencyKey;        // 幂等键

    // 回调
    private Boolean callbackEnabled;
    private String callbackUrl;
}
```

#### 2.3.2 单条分发对象（由批次拆分得到，替代/升级现有 StandardSubmit）

```java
package com.cz.common.model.v2;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SmsDispatchTask implements Serializable {

    // 单条唯一标识
    private Long messageId;               // 单短信ID（替代 sequenceId 的业务维度）
    private Long sequenceId;              // 通道流水仍可保留

    // 批次上下文
    private Long batchId;
    private Integer batchIndex;           // 当前在批次中的位置

    // 租户与路由
    private Long clientId;
    private String apiKey;
    private String mobile;
    private Integer operatorId;
    private Long channelId;
    private String srcNumber;

    // 内容
    private Long templateId;
    private String text;
    private String sign;
    private Long signId;

    // 时间语义
    private LocalDateTime acceptTime;
    private LocalDateTime scheduleAt;
    private LocalDateTime dispatchAt;

    // 成本与状态
    private Long fee;
    private Integer reportState;          // 0待发 1成功 2失败
    private String errorMsg;

    // 幂等与可观测
    private String idempotencyKey;
    private String traceId;
}
```

#### 2.3.3 为什么不建议“仅在旧 StandardSubmit 上加 mobileList + scheduleAt”

风险：
- 旧链路所有消费者默认单条语义；直接加集合字段会导致策略/网关/回执层语义混乱。
- MQ 消息体膨胀，单条消息过大影响吞吐和重试成本。

建议：
- 保留现有 `StandardSubmit` 作为“单条分发语义”或新增 `SmsDispatchTask` 平滑替代。
- 批次由新对象承载，拆分后再进入策略和网关。

---

## 三、架构师落地建议（Architectural Roadmap）

### 3.1 中间件选型与插入位置

### 3.1.1 MQ（RabbitMQ/Kafka）建议

结论：必须加重使用 MQ，并将其从“单点异步”升级为“多级流水线”。

建议拓扑（基于现有 RabbitMQ 可落地）：
1. `sms_batch_ingress_queue`
- 位置：API 受理之后
- 作用：快速返回受理成功，避免入口同步压力

2. `sms_batch_split_queue`
- 位置：批次拆分服务
- 作用：把批次拆成单条任务（或小批分片）

3. `sms_dispatch_ready_queue`
- 位置：策略前
- 作用：承载待发送任务并做节流

4. `sms_gateway_queue_{channelId}`
- 位置：策略后到网关前
- 作用：保持通道路由隔离

5. `sms_report_queue` / `sms_callback_queue`
- 位置：网关回执后
- 作用：状态更新与回调解耦

RabbitMQ 与 Kafka 的阶段选择：
- 第一阶段（快速演进）：继续 RabbitMQ，补齐多级队列与死信重试。
- 第二阶段（千万级稳定运营）：若单日体量很大、需要长时间堆积与高吞吐回放，可评估 Kafka 作为“任务主总线”，Rabbit 保留延迟与回调场景。

### 3.1.2 Redis 角色升级建议

现状已用于黑名单、余额、限流、号段等，建议新增：
- 定时任务时间轮：`ZSET(scheduleAt -> taskId)`
- 批次进度计数：`batch:{id}:success/fail/sending`
- 幂等去重：`SETNX(idempotencyKey)`
- 客户级/通道级漏桶限流状态

插入环节：
- `submit-service`：受理时幂等
- `schedule-service`：拉取到期任务
- `dispatch-service`：实时限速与配额控制

### 3.1.3 存储建议（批次级）

建议新增“短信任务域数据表”：
- `sms_batch`（批次主表）
- `sms_task`（单条任务表）
- `sms_schedule_trigger`（调度触发表，可选）

必要原因：
- 群发与定时需要可恢复、可追踪、可审计的任务状态机，不能只依赖临时缓存和 MQ。

### 3.2 微服务拆分/新增建议

### 3.2.1 推荐目标模块划分

1. `sms-submit-service`（可由现 beacon-api 演进）
- 职责：统一受理、参数校验、幂等控制、快速入队

2. `sms-batch-service`（新增）
- 职责：批次管理、文件解析、任务拆分、进度聚合

3. `sms-schedule-service`（新增）
- 职责：定时触发、窗口控制、过期处理
- 说明：可先复用现 Quartz 能力，再逐步域化独立

4. `sms-template-service`（新增）
- 职责：模板版本、参数规则、渲染审计

5. `sms-dispatch-service`（可由 beacon-strategy 演进）
- 职责：风控、路由、扣费、限速

6. `sms-gateway-service`（现 beacon-smsgateway）
- 职责：协议适配、通道连接池、回执解析

7. `sms-report-service`（可由 beacon-search + beacon-push 拆分整合）
- 职责：状态落库、报表、回调重试与签名

### 3.2.2 为什么建议拆分

- 群发与定时是“任务域”问题，不是“单接口参数扩展”问题。
- 若不拆分，`beacon-api` 和 `beacon-strategy` 会同时承担受理、编排、调度、状态、回调，耦合度过高。

### 3.3 分阶段落地路线（建议）

### Phase 1（2-4 周）：低风险增量

目标：先把架构变成“可扩展形态”。

改造项：
1. 新增批次受理接口（异步返回 `batchId`），避免逐条同步调用。
2. 新增批次拆分消费者，将任务拆成单条后沿用现策略链。
3. 修复关键一致性隐患：
   - 统一 `apiKey/apikey` 字段命名与映射
   - 补齐 fee 字段生成和空值保护
4. 预创建通道队列，移除发送时高频 `declareQueue`。

### Phase 2（4-8 周）：定时能力与限流体系

目标：把“定时发送”变成稳定能力。

改造项：
1. 引入 `sms_schedule_service`（可先在现有模块实现，再独立部署）。
2. 建立调度状态表与 Redis ZSET 触发器。
3. 实现窗口发送 + 客户级/通道级限速。

### Phase 3（8-12 周）：千万级运营能力

目标：支撑大体量营销场景与可观测治理。

改造项：
1. 批次任务全链路状态机（待拆分/待发送/发送中/完成/失败）。
2. 回执与回调幂等化（messageId + attemptNo）。
3. 完善观测：吞吐、积压、失败码分布、通道 SLA、客户维度成本。

---

## 四、关键重构清单（可直接进入需求池）

### 4.1 P0（必须先做）

1. 引入批次受理与异步拆分，替代逐手机号同步内部调用。
2. 修正字段映射与空值风险：`apiKey/apikey`、`fee` 生成一致性。
3. 将网关配置（host/port/serviceId/pwd）迁移到配置中心并加密管理。
4. 固化 MQ 队列拓扑与重试策略，减少运行时动态声明。

### 4.2 P1（高价值）

1. 新增短信域任务表（batch/task）和任务状态机。
2. 新增定时触发服务（Quartz -> 域化调度）。
3. 新增模板中心与参数渲染服务。

### 4.3 P2（规模化）

1. 评估 Kafka 作为高吞吐任务总线。
2. 网关状态关联外部化（Redis/DB），支持多实例水平扩展。
3. 引入更精细的多租户配额和成本路由策略。

---

## 五、对你当前项目的务实建议（结论）

如果你准备从“单条发送”进入“群发 + 定时”阶段，最优路径不是先堆功能，而是先补“任务化底座”：

- 第一步：把发送从“请求驱动”改成“任务驱动”（批次受理 -> 拆分 -> 分发）。
- 第二步：把时间语义从“立即发送”升级为“可调度发送”（scheduleAt + 窗口 + 过期）。
- 第三步：把对象语义从“单条 StandardSubmit”拆成“批次命令 + 单条任务”，再渐进替换现链路。

这条路径可以最大化复用现有 `strategy/gateway/search/push` 能力，同时避免一次性重构风险。

---

## 附：当前代码库中的关键证据定位

- 管理端逐条内部调用：
  - `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:125`
  - `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:138`
- API 仅单条入口：
  - `beacon-api/src/main/java/com/cz/api/controller/SmsController.java:62`
  - `beacon-api/src/main/java/com/cz/api/controller/SmsController.java:79`
- 内外链路校验差异：
  - `beacon-api/src/main/java/com/cz/api/controller/SmsController.java:75`
  - `beacon-api/src/main/java/com/cz/api/filter/CheckFilterContext.java:26`
- 扣费依赖 fee 字段：
  - `beacon-strategy/src/main/java/com/cz/strategy/filter/impl/FeeStrategyFilter.java:31`
  - `beacon-strategy/src/main/java/com/cz/strategy/filter/impl/FeeStrategyFilter.java:34`
- 路由时动态声明队列：
  - `beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java:108`
- 网关监听与本地序列状态：
  - `beacon-smsgateway/src/main/java/com/cz/smsgateway/mq/SmsGatewayListener.java:31`
  - `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/utils/MsgUtils.java:25`
  - `beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java:17`
- 网关配置硬编码：
  - `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java:11`
- 搜索写入性能点与幂等语义：
  - `beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java:33`
  - `beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:69`
- 定时底座（可复用资产）：
  - `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/ScheduleJobServiceImpl.java:34`
  - `beacon-webmaster/src/main/java/com/cz/webmaster/schedule/QuartzUtils.java:31`


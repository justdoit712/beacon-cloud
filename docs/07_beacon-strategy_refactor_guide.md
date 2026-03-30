# beacon-strategy 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-strategy  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `07_beacon-strategy_module_analysis.md`
2. `08_strategy_module_risk_explanation.md`
3. `07_beacon-strategy_refactor_guide.md`

## 1. 模块定位

`beacon-strategy` 是短信平台的“策略执行中枢”，主要职责：

1. 消费 `beacon-api` 投递的预发送消息（`SMS_PRE_SEND`）。
2. 读取客户策略配置并按顺序执行（黑名单、敏感词、限流、扣费、路由、携号转网、号段补齐等）。
3. 失败场景发送写日志消息与状态报告消息。
4. 成功场景完成通道路由并投递到网关队列（`SMS_GATEWAY_{channelId}`）。

核心入口文件：

1. 启动类：`beacon-strategy/src/main/java/com/cz/strategy/StrategyStarterApp.java`
2. MQ 监听：`beacon-strategy/src/main/java/com/cz/strategy/mq/PreSendListener.java`
3. 策略上下文：`beacon-strategy/src/main/java/com/cz/strategy/filter/StrategyFilterContext.java`
4. 策略实现：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/*.java`
5. 缓存客户端：`beacon-strategy/src/main/java/com/cz/strategy/client/BeaconCacheClient.java`
6. 错误消息封装：`beacon-strategy/src/main/java/com/cz/strategy/util/ErrorSendMsgUtil.java`

---

## 2. 当前实现概览

## 2.1 消息处理主链路

1. `PreSendListener.listen(...)` 收到 `StandardSubmit`。
2. 调用 `StrategyFilterContext.strategy(submit)` 按 Redis 配置链执行。
3. 过滤器可能抛 `StrategyException`，并在过滤器内部调用 `ErrorSendMsgUtil`。
4. `RouteStrategyFilter` 选择通道后，声明并投递到网关队列。

## 2.2 当前策略链来源

策略链并非本地配置，而是从缓存读取：

```java
String filters = cacheClient.hget(CacheKeyConstants.CLIENT_BUSINESS + submit.getApiKey(), "clientFilters");
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/StrategyFilterContext.java:31`

说明：这是灵活点，也是高风险点。配置错误、数据异常会直接影响业务放行或拦截。

---

## 3. 需要重构的代码、原因与重构方案

## 3.1 P0：策略链“失败即放行”（fail-open）风险

### 现状代码（仍需重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/StrategyFilterContext.java:31`

```java
String filters = cacheClient.hget(CacheKeyConstants.CLIENT_BUSINESS + submit.getApiKey(), CLIENT_FILTERS);
if (filters == null) {
    return;
}
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/StrategyFilterContext.java:58`

```java
StrategyFilter strategyFilter = stringStrategyFilterMap.get(filterName);
if (strategyFilter == null) {
    log.warn("【策略模块】未找到过滤器定义 filterName = {}", filterName);
    return;
}
```

### 原因

1. Redis 未命中或异常返回 `null` 时，策略链整体跳过，短信直接放行。
2. 配置拼写错误（找不到过滤器）只打日志，不阻断，也会造成安全策略绕过。
3. 这是策略中心，不应采用默认放行。

### 如何重构

1. 对 `filters` 空值采用默认安全链，或直接失败（按业务选择）。
2. 未找到过滤器时抛配置异常并进入失败处理。
3. 启动时校验策略 key 与 Spring Bean 一一对应，提前 fail-fast。

### 目标代码（建议）

```java
if (!StringUtils.hasText(filters)) {
    throw new StrategyException("clientFilters missing", ExceptionEnums.UNKNOWN_ERROR.getCode());
}

StrategyFilter strategyFilter = stringStrategyFilterMap.get(filterName);
if (strategyFilter == null) {
    throw new StrategyException("unknown strategy filter: " + filterName, ExceptionEnums.UNKNOWN_ERROR.getCode());
}
```

---

## 3.2 P0：MQ 消费异常处理不完整，可靠性边界不清晰

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/mq/PreSendListener.java:27`

```java
public void listen(StandardSubmit submit, Message message, Channel channel) throws IOException {
    try {
        filterContext.strategy(submit);
        channel.basicAck(..., false);
    } catch (StrategyException e) {
        channel.basicAck(..., false);
    }
}
```

### 原因

1. 仅捕获 `StrategyException`，其他运行时异常会逃逸，消费行为依赖容器默认策略，可能出现重复投递或堆积。
2. 成功/失败都 `ack`，但没有统一“是否已完成补偿消息发送”的状态判断。
3. 可靠性语义不透明，排障成本高。

### 如何重构

1. 明确消费语义：业务失败是否必须入失败队列后再 ack。
2. 增加兜底 `catch (Exception ex)`，并统一错误上报。
3. 配置 `SimpleRabbitListenerContainerFactory` 的 ack、requeue、重试与死信策略。

### 目标代码（建议）

```java
try {
    filterContext.strategy(submit);
    channel.basicAck(tag, false);
} catch (StrategyException ex) {
    // 业务可预期失败：记录并ack
    channel.basicAck(tag, false);
} catch (Exception ex) {
    // 非预期异常：统一上报后按策略 nack/requeue
    channel.basicNack(tag, false, false);
}
```

---

## 3.3 P0：敏感词策略实现不一致，存在“检测到了但不拦截”

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/DirtyWordDFAStrategyFilter.java:30`

```java
if (dirtyWords != null && dirtyWords.size() > 0) {
    log.info("... 包含敏感词 ...");
    // 还需要做其他处理
}
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/DirtyWordStrategyFilter.java:51`

```java
if(dirtyWords != null && dirtyWords.size() > 0){
    log.info("... 包含敏感词 ...");
    // 还需要做其他处理
}
```

对比：

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/DirtyWordHutoolDFAStrategyFilter.java:47`

```java
throw new StrategyException(ExceptionEnums.ERROR_DIRTY_WORD);
```

### 原因

1. 三个敏感词过滤器行为不一致，配置不同 filter key 时结果完全不同。
2. 前两个实现检测命中后仅记录日志，业务仍会继续下发。
3. 该问题属于业务规则错误，不是单纯代码风格问题。

### 如何重构

1. 收敛为单一敏感词策略实现（建议保留 Hutool 版本）。
2. 命中后统一：写日志 + 推报告 + 抛 `StrategyException`。
3. 给策略链增加“互斥策略校验”，避免多个 dirtyword 过滤器并存。

---

## 3.4 P0：敏感词工具类使用静态初始化读取 Spring Bean，启动阶段不稳定

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/DFAUtil.java:20`

```java
static {
    BeaconCacheClient cacheClient = (BeaconCacheClient) SpringUtil.getBeanByClass(BeaconCacheClient.class);
    Set<String> dirtyWords = cacheClient.smember(CacheKeyConstants.DIRTY_WORD);
    create(dirtyWords);
}
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/HutoolDFAUtil.java:17`

```java
static {
    BeaconCacheClient cacheClient = (BeaconCacheClient) SpringUtil.getBeanByClass(BeaconCacheClient.class);
    Set<String> dirtyWords = cacheClient.smember(CacheKeyConstants.DIRTY_WORD);
    wordTree.addWords(dirtyWords);
}
```

### 原因

1. 类加载早于 Spring 上下文准备时，`SpringUtil` 可能返回空导致初始化失败。
2. 敏感词只加载一次，运行期词库更新无法生效。
3. 工具类与 Spring 容器强耦合，不易测试。

### 如何重构

1. 改成 `@Component` + `@PostConstruct` 初始化。
2. 提供词库刷新机制（定时刷新/配置变更事件/手动刷新接口）。
3. 过滤器依赖 `DirtyWordMatcher` 接口，而非静态工具类。

---

## 3.5 P1：扣费策略依赖硬编码透支阈值，且回滚逻辑脆弱

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/FeeStrategyFilter.java:37`

```java
Long amountLimit = ClientBalanceUtil.getClientAmountLimit(submit.getClientId());
if(amount < amountLimit) {
    cacheClient.hIncrBy(..., fee);
    throw new StrategyException(...);
}
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/ClientBalanceUtil.java:12`

```java
return -10000L;
```

### 原因

1. 透支阈值写死，无法按客户/产品配置。
2. “扣减后判定再回滚”在高并发下易产生边界问题。
3. 该规则应是策略配置，不应固化在工具类。

### 如何重构

1. 阈值改为客户配置化（缓存读取 + 本地短期缓存）。
2. 扣费与阈值校验收敛为缓存侧原子脚本（Lua）或单接口事务语义。
3. 失败回滚写入审计事件，避免“静默回滚”。

---

## 3.6 P1：限流策略硬编码严重，缺少可运营化能力

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/LimitOneHourStrategyFilter.java:35`

```java
private static final int LIMIT_MINUTE = 1;
private static final int LIMIT_HOUR = 3;
private static final int LIMIT_DAY = 10;
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/LimitOneHourStrategyFilter.java:115`

```java
member = System.currentTimeMillis() + retry + 1;
```

### 原因

1. 阈值不可配置，无法按客户等级差异化限流。
2. 重试逻辑依赖本地时间戳拼接，语义不直观，排障困难。
3. 限流逻辑分散在 Java 代码中，可维护性一般。

### 如何重构

1. 阈值与窗口参数放入配置中心，可按客户覆盖。
2. Redis 侧用 Lua 封装“写入+计数+回滚”原子过程。
3. 增加限流命中埋点（分钟/小时/天分维度）。

---

## 3.7 P1：号段补齐策略存在重复实现与兜底格式问题

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/PhaseStrategyFilter.java:41`

```java
/*@Override
   ... 老实现整段注释 ...
*/
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/PhaseStrategyFilter.java:26`

```java
private final String UNKNOWN = "未知 未知，未知";
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/PhaseStrategyFilter.java:97`

```java
String[] areaAndOperator = mobileInfo.split(SEPARATE);
```

### 原因

1. 同文件保留大段旧实现注释，降低可读性。
2. `UNKNOWN` 使用中文逗号，`split(",")` 无法按预期拆分，兜底路径解析失败。
3. 外部 360 接口失败时兜底不够清晰，`operatorId` 可能无法稳定赋值。

### 如何重构

1. 删除注释掉的历史实现，保留当前版本并补齐单测。
2. 统一 `mobileInfo` 数据协议（DTO），禁止字符串拼接协议。
3. 外部调用增加超时/重试/熔断与降级默认值。

---

## 3.8 P1：RabbitMQ 回调配置仍有缺口

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/config/RabbitTemplateConfig.java:45`

```java
rabbitTemplate.setReturnCallback(...);
```

### 原因

1. `RabbitTemplate` 侧仍未设置 `mandatory=true`，return 回调可能不触发。
2. 仍在使用旧式回调 API，升级 Spring AMQP 时兼容风险较高。

### 如何重构

1. 补充 `rabbitTemplate.setMandatory(true)`。
2. 升级到新式 `ReturnsCallback`/`ConfirmCallback` 用法并加集成测试。

## 3.9 P2：测试覆盖仍有缺口

### 现状

`beacon-strategy/src/test` 已新增一批聚焦测试，当前已覆盖：

1. `StrategyFilterContext`
2. `RouteStrategyFilter`
3. `PreSendListener`
4. `ErrorSendMsgUtil`
5. `FeeStrategyFilter`
6. `LimitOneHourStrategyFilter`
7. `DirtyWordHutoolDFAStrategyFilter`

但 MQ 异常分支、strategy/cache 契约一致性，以及多过滤器组合回归仍缺少自动化保护。

### 风险

1. 策略行为高度依赖配置和缓存结构，仅覆盖部分链路仍容易引发回归事故。
2. MQ 异常路径、Feign 契约和跨过滤器组合仍缺少自动化回归。

### 如何重构

1. 在现有 listener 测试基础上，继续补足 MQ 异常分支与失败回传校验。
2. 增加 strategy 与 cache 的 Feign 契约一致性测试。
3. 增加多过滤器组合链路回归，例如扣费、限流、路由串联场景。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，先保正确性）

1. 策略链 fail-open 改造：空链路、未知 filter 一律可控失败。
2. 敏感词策略收敛：统一为一个实现，命中即拦截。
3. MQ 消费异常兜底：补全 `Exception` 处理路径和 ack/nack 策略。

## 阶段二（P1，做稳定性治理）

1. 扣费/限流配置化与原子化。
2. 号段补齐策略 DTO 化，接入外部接口容错。
3. RabbitMQ 配置语义与回调机制修正。

## 阶段三（P2，做工程化）

1. 完整补齐测试体系。
2. 逐步移除旧兼容实现。

---

## 5. 建议测试清单

## 5.1 单元测试

1. `PhaseStrategyFilter`：外部号段接口异常、兜底格式与 `operatorId` 推导。
2. `RabbitTemplateConfig`：`mandatory` 与 return/confirm 回调行为。

## 5.2 集成测试

1. 失败场景是否发送 `SMS_WRITE_LOG` 与 `SMS_PUSH_REPORT`。
2. 路由成功后是否投递 `SMS_GATEWAY_{channelId}`。

## 5.3 回归验证

1. 客户策略链配置变更后是否实时生效。
2. 压测下扣费、限流、路由的吞吐与错误率。
3. 故障演练：cache 异常、外部号段接口异常、RabbitMQ 异常。

---

## 6. 跨模块联动建议

1. 与 `beacon-cache`：先发 V2 typed 接口，再迁移 strategy Feign 客户端。
2. 与 `beacon-common`：统一异常码与错误语义，减少模块侧重复判定。
3. 与 `beacon-monitor`：新增策略层监控指标（策略失败率、路由失败率、扣费回滚率）。

建议采用“新增兼容 -> 灰度切流 -> 清理旧逻辑”的三步推进，避免一次性大改导致生产风险。


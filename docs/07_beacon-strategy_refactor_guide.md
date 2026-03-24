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

## 3.3 P0：路由策略存在排序与类型安全问题

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java:49`

```java
TreeSet<Map> clientWeightChannels = new TreeSet<>(new Comparator<Map>() {
    public int compare(Map o1, Map o2) {
        int o1Weight = Integer.parseInt(o1.get("clientChannelWeight") + "");
        int o2Weight = Integer.parseInt(o2.get("clientChannelWeight") + "");
        return o2Weight - o1Weight;
    }
});
clientWeightChannels.addAll(clientChannels);
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java:65`

```java
if((int)(clientWeightChannel.get("isAvailable")) != 0){ ... }
```

### 原因

1. `TreeSet` 比较器只按权重比较；权重相等时返回 `0`，后插入元素会被“去重丢失”。
2. `clientChannels` 可能为 `null`，`addAll` 直接 NPE。
3. 多处 `Map` 强转 `int/Integer`，缓存类型漂移时容易 `ClassCastException`。
4. `submit.getOperatorId()` 为空时参与比较有空指针风险。

### 如何重构

1. 引入强类型 DTO（`ClientChannelBinding`、`ChannelInfo`）。
2. 排序改为 `List + Comparator`，并用 `channelId` 做二级排序防重复。
3. 所有缓存字段统一类型转换工具（`toInt/toLong`），禁止直接强转。
4. 路由前显式校验 `operatorId`。

### 目标代码（建议）

```java
bindings.sort(Comparator
    .comparingInt(ClientChannelBinding::getWeight).reversed()
    .thenComparingLong(ClientChannelBinding::getChannelId));
```

---

## 3.4 P0：敏感词策略实现不一致，存在“检测到了但不拦截”

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

## 3.5 P0：敏感词工具类使用静态初始化读取 Spring Bean，启动阶段不稳定

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

## 3.6 P1：`BeaconCacheClient` 弱类型 + 同路径多返回类型

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/client/BeaconCacheClient.java:12`

```java
@GetMapping("/cache/hget/{key}/{field}")
String hget(...);

@GetMapping("/cache/hget/{key}/{field}")
Integer hgetInteger(...);
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/client/BeaconCacheClient.java:24`

```java
@GetMapping("/cache/smember/{key}")
Set smember(...);

@GetMapping("/cache/smember/{key}")
Set<Map> smemberMap(...);
```

### 原因

1. 同一路径多返回类型，反序列化依赖运行时行为，稳定性差。
2. 原始类型 `Set/Map` 扩散到业务层，强转点太多。
3. 与 `beacon-cache` 契约不清晰，升级困难。

### 如何重构

1. 联动 `beacon-cache` 推出 V2 typed API。
2. strategy 侧引入 `CacheFacade`，屏蔽 V1/V2 差异。
3. 所有策略过滤器只依赖强类型 DTO。

---

## 3.7 P1：扣费策略依赖硬编码透支阈值，且回滚逻辑脆弱

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

## 3.8 P1：限流策略硬编码严重，缺少可运营化能力

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

## 3.9 P1：号段补齐策略存在重复实现与兜底格式问题

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

## 3.10 P1：错误回传工具存在空指针与重复查询问题

### 现状代码（需要重构）

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/ErrorSendMsgUtil.java:37`

```java
Integer isCallback = cacheClient.hgetInteger(..., "isCallback");
if(isCallback == 1){
    String callbackUrl = cacheClient.hget(..., "callbackUrl");
    ...
}
```

### 原因

1. 当前代码里 `isCallback == 1` 这项风险仍然存在，文档判断保持有效。
2. 每次失败都查询两次缓存，热点场景下放大依赖压力。
3. 未体现幂等约束，重复失败上报可能重复推报告。

### 如何重构

1. 判空改为 `Integer.valueOf(1).equals(isCallback)`。
2. 一次性获取客户配置快照 DTO，减少重复调用。
3. 引入 `sid` 去重键控制重复推报告。

---

## 3.11 P1：RabbitMQ 回调配置仍有缺口

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

---

## 3.12 P2：工程规范与可维护性问题

### 现状代码（需要重构）

文件：`beacon-strategy/pom.xml:12`

```xml
<groupId>org.example</groupId>
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/StrategyStarterApp.java:21`

```java
System.out.println("StrategyStarterApp  mission complete");
```

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/SpringUtil.java:10`

```java
import static com.google.common.collect.ConcurrentHashMultiset.create;
```

### 原因

1. `groupId` 与项目其他模块风格不一致（`com.cz`）。
2. `System.out.println` 不利于生产日志治理。
3. 无用 import 增加噪音。

### 如何重构

1. 统一 Maven 坐标命名规范。
2. 全量替换为结构化日志。
3. 清理无用 import 与历史残留代码。

---

## 3.13 P2：测试体系缺失

### 现状

`beacon-strategy/src/test` 当前为空。

### 风险

1. 策略行为高度依赖配置和缓存结构，缺少测试容易引发回归事故。
2. 路由、限流、扣费、失败回传均属关键链路，必须具备自动化回归。

### 如何重构

1. 单元测试：每个过滤器最少覆盖成功/失败两条路径。
2. 集成测试：MQ 消费、cache mock、失败回传。
3. 合同测试：strategy 与 cache 的 Feign 契约一致性。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，先保正确性）

1. 策略链 fail-open 改造：空链路、未知 filter 一律可控失败。
2. 敏感词策略收敛：统一为一个实现，命中即拦截。
3. 路由过滤器重构：去掉 `TreeSet<Map>` + 强转逻辑，修复权重相等丢通道问题。
4. MQ 消费异常兜底：补全 `Exception` 处理路径和 ack/nack 策略。

## 阶段二（P1，做稳定性治理）

1. 缓存契约 typed 化：联动 `beacon-cache` 发布 V2 接口。
2. 扣费/限流配置化与原子化。
3. 号段补齐策略 DTO 化，接入外部接口容错。
4. `ErrorSendMsgUtil` 空值防御与幂等增强。
5. RabbitMQ 配置语义与回调机制修正。

## 阶段三（P2，做工程化）

1. 清理工程规范问题（坐标、日志、无用代码）。
2. 完整补齐测试体系。
3. 逐步移除旧兼容实现。

---

## 5. 建议测试清单

## 5.1 单元测试

1. `StrategyFilterContext`：空 filters、未知 filter、黑名单兼容 key（`black`）路径。
2. `RouteStrategyFilter`：权重相同、多通道、通道不可用、运营商不匹配路径。
3. `FeeStrategyFilter`：扣费成功、阈值不足回滚、缓存异常路径。
4. `LimitOneHourStrategyFilter`：分钟/小时/天限流命中路径。
5. `DirtyWord*StrategyFilter`：命中敏感词后统一异常行为。

## 5.2 集成测试

1. `PreSendListener` 消费成功与失败的 ack/nack 行为。
2. 失败场景是否发送 `SMS_WRITE_LOG` 与 `SMS_PUSH_REPORT`。
3. 路由成功后是否投递 `SMS_GATEWAY_{channelId}`。

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


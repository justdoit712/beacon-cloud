# 策略模块风险详细说明（除黑名单命名问题）

更新时间：2026-02-26  
适用仓库：`beacon-cloud`

---

## 0. 前置说明（你已确认的点）

你提到的问题是正确的：

- 原先默认 `clientFilters` 使用 `black`，而策略 Bean 名是 `blackGlobal`、`blackClient`，会导致黑名单策略未执行。
- 仅从配置层规避，确实可以在数据库 `client_business.client_filters` 里配置：
  - `blackGlobal,blackClient,...`

这份文档重点解释其余风险：问题代码在哪里、为什么有风险、怎么修。

---

## 1. 风险一：默认 `dirtyword` 策略命中后不拦截，只打印日志

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/DirtyWordStrategyFilter.java`

```java
if(dirtyWords != null && dirtyWords.size() > 0){
    log.info("【策略模块-敏感词校验】   短信内容包含敏感词信息， dirtyWords = {}",dirtyWords);
    // 还需要做其他处理
}
```

### 问题在哪里

- 命中敏感词后，没有 `throw new StrategyException(...)`，也没有写失败日志/状态报告。
- 结果是后续策略继续执行，短信仍可能被路由发送出去。

### 影响

- 策略名称看起来是“敏感词校验”，实际行为是“仅记录日志”。
- 容易产生误判：运营端以为“开了敏感词策略就会拦截”，事实上没有。

### 如何解决

可选方案二选一：

1. 业务要求必须拦截：直接在这个过滤器内补齐失败处理并抛异常。
2. 业务要求只审计不拦截：重命名策略（例如 `dirtywordAudit`），避免语义误导。

### 设计思路

- 策略命名必须和行为一致。
- “校验类策略”通常应该 fail-fast，命中即中断链路并给出失败原因。

---

## 2. 风险二：`internal/single_send` 绕过 API 校验链，可能导致费用字段为空

### 问题代码 A（内部发送未走 `checkFilterContext`）

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

```java
@PostMapping(value = "/internal/single_send", produces = "application/json;charset=utf-8")
public ResultVO internalSingleSend(...) {
    ...
    StandardSubmit submit = buildSubmit(...);
    return enqueue(submit);
}
```

### 对照代码 B（公开接口会走 `checkFilterContext`）

同文件：

```java
@PostMapping(value = "/single_send", produces = "application/json;charset=utf-8")
public ResultVO singleSend(...) {
    ...
    checkFilterContext.check(submit);
    return enqueue(submit);
}
```

### 关联代码 C（策略层扣费直接使用 `submit.getFee()`）

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/FeeStrategyFilter.java`

```java
Long fee = submit.getFee();
Long amount = cacheClient.hIncrBy(CacheConstant.CLIENT_BALANCE + clientId, BALANCE, -fee);
```

### 问题在哪里

- `fee` 是在 API 侧 `FeeCheckFilter` 中计算并写入 `submit` 的。
- 内部接口不走 check 链时，`fee` 可能为空。
- 策略扣费阶段对空值没有防御。

### 影响

- 运行时异常（NPE 或参数错误）。
- 消息消费异常后是否重试取决于容器行为，可能引发重复处理或积压。

### 如何解决

推荐方案：

1. 内部接口也走同一套 `checkFilterContext.check(submit)`。
2. 同时在 `FeeStrategyFilter` 做兜底校验：
   - `fee == null` 时直接抛业务异常并记录失败原因，不做扣费。

### 设计思路

- 入口分流可以不同，但消息模型必备字段要统一保证。
- 关键账务逻辑必须双重防御：上游保证 + 下游兜底。

---

## 3. 风险三：路由排序用 `TreeSet + Comparator`，同权重通道会被去重丢失

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java`

```java
TreeSet<Map> clientWeightChannels = new TreeSet<>(new Comparator<Map>() {
    @Override
    public int compare(Map o1, Map o2) {
        int o1Weight = Integer.parseInt(o1.get("clientChannelWeight") + "");
        int o2Weight = Integer.parseInt(o2.get("clientChannelWeight") + "");
        return o2Weight - o1Weight;
    }
});
clientWeightChannels.addAll(clientChannels);
```

### 问题在哪里

- `TreeSet` 以 `compare(...) == 0` 判定“同一个元素”。
- 现在比较器只按权重比较。
- 两条不同通道但权重相同，会被当成重复数据吞掉一条。

### 影响

- 可用通道集合被错误缩减。
- 容灾与分流能力下降，路由选择不稳定。

### 如何解决

改成“排序不去重”：

1. `List<Map> channels = new ArrayList<>(clientChannels);`
2. `channels.sort(...)` 按权重降序。
3. 同权重时再加次级排序键（如 `channelId`）保证确定性。

### 设计思路

- 排序和去重是两件事，不应混在同一数据结构里隐式处理。

---

## 4. 风险四：通道转换结果变量 `transferChannel` 计算后未使用

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/RouteStrategyFilter.java`

```java
Map transferChannel = ChannelTransferUtil.transfer(submit, channel);
...
submit.setChannelId(Long.parseLong(channel.get("id") + ""));
submit.setSrcNumber("" + channel.get("channelNumber") + clientChannel.get("clientChannelNumber"));
```

### 问题在哪里

- `transferChannel` 只创建不用。
- 真正封装 `submit` 仍使用原始 `channel`。

### 影响

- 后续扩展（携号转网、通道转换、临时替换）无法生效。
- 代码可读性误导，维护者以为“已经支持转换”。

### 如何解决

- 明确选择其一：
  - 不支持转换：删除 `transferChannel` 相关代码。
  - 支持转换：后续封装字段全部改为用 `transferChannel`。

### 设计思路

- 对“预留扩展点”要么 stub 明确，要么实现完整，避免半状态。

---

## 5. 风险五：策略模块声明的预发送队列常量与实际监听队列不一致

### 问题代码 A（策略模块队列声明）

文件：`beacon-strategy/src/main/java/com/cz/strategy/config/RabbitMQConfig.java`

```java
@Bean
public Queue preSendQueue(){
    return QueueBuilder.durable(RabbitMQConstants.MOBILE_AREA_OPERATOR).build();
}
```

### 问题代码 B（策略模块监听）

文件：`beacon-strategy/src/main/java/com/cz/strategy/mq/PreSendListener.java`

```java
@RabbitListener(queues = RabbitMQConstants.SMS_PRE_SEND)
public void listen(StandardSubmit submit, Message message, Channel channel) ...
```

### 问题在哪里

- 声明的是 `MOBILE_AREA_OPERATOR`，监听的是 `SMS_PRE_SEND`。
- 本模块无法保证自己把监听队列建好，依赖外部服务先声明。

### 影响

- 不同环境启动顺序变化时，可能出现消费端先起但队列不存在。

### 如何解决

- `preSendQueue()` 改为声明 `RabbitMQConstants.SMS_PRE_SEND`。
- 每个服务应声明自己消费的队列，减少跨服务耦合。

### 设计思路

- 队列契约要“生产/消费/声明”一致，避免隐式依赖。

---

## 6. 风险六：失败回调逻辑存在空值拆箱风险

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/ErrorSendMsgUtil.java`

```java
Integer isCallback = cacheClient.hgetInteger(..., "isCallback");
if(isCallback == 1){
    ...
}
```

### 问题在哪里

- `hgetInteger` 可能返回 `null`。
- `if (isCallback == 1)` 在拆箱时会触发 NPE。

### 影响

- 原本只是策略拒绝短信，结果失败通知流程又抛异常。

### 如何解决

安全判定：

```java
if (Integer.valueOf(1).equals(isCallback)) {
    ...
}
```

### 设计思路

- 任何来自缓存/外部的数据都按“可空”处理。

---

## 7. 风险七：管理端表单不支持 `clientFilters`，策略链不可视不可改

### 问题代码 A（表单缺字段）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/dto/ClientBusinessForm.java`

```java
public class ClientBusinessForm {
    ...
    private String money;
}
```

### 问题代码 B（转换器也不映射）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/converter/ClientBusinessConverter.java`

```java
cb.setExtend4(form.getMoney());
// 没有 cb.setClientFilters(...)
```

### 问题在哪里

- 后台页面保存/更新时无法传入 `clientFilters`。
- 业务策略配置只能靠默认值或直接改数据库/缓存。

### 影响

- 可运维性差，策略变更不可控。

### 如何解决

1. `ClientBusinessForm` 增加 `clientFilters` 字段。
2. `ClientBusinessConverter` 双向映射该字段。
3. 前端页面新增输入并做合法值校验（白名单）。

### 设计思路

- “配置驱动策略”的前提是配置项可被正确管理和回显。

---

## 8. 风险八：欠费阈值是硬编码桩实现

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/util/ClientBalanceUtil.java`

```java
public static Long getClientAmountLimit(Long clientId) {
    return -10000L;
}
```

### 问题在哪里

- 不同客户无法设置不同透支额度。
- 规则变更必须发版改代码。

### 影响

- 财务策略不可运营化，风险控制粗糙。

### 如何解决

1. 透支阈值放到客户配置（DB/缓存）中。
2. 加默认值和兜底值。
3. 在日志里打印命中的阈值来源，便于审计。

### 设计思路

- 财务规则应配置化，不应写死在工具类。

---

## 9. 风险九：策略配置页面目前是内存存储，不持久化

### 问题代码

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/LegacyCrudServiceImpl.java`

```java
private final ConcurrentMap<String, ConcurrentMap<Long, Map<String, Object>>> store = new ConcurrentHashMap<>();
...
familyStore(family).put(id, row);
...
Map<String, Object> data = familyStore(family).get(id);
```

### 问题在哪里

- 数据在 JVM 内存，服务重启后丢失。
- 与 `client_business.client_filters` 及 redis 实际生效配置未建立同步关系。

### 影响

- 页面上“看起来改了策略”，但策略服务不一定按这个配置执行。

### 如何解决

1. 策略配置改为持久化（DB）。
2. 统一缓存同步流程（写库后刷新缓存）。
3. 增加“生效配置查看”接口，直接读策略实际来源（redis）。

### 设计思路

- 控制面（管理页）与数据面（策略执行）必须同源，否则会产生配置幻觉。

---

## 10. 风险十：频控阈值和时区硬编码，缺少环境/客户维度配置

### 问题代码

文件：`beacon-strategy/src/main/java/com/cz/strategy/filter/impl/LimitOneHourStrategyFilter.java`

```java
private static final String UTC = "+8";
private static final int LIMIT_MINUTE = 1;
private static final int LIMIT_HOUR = 3;
private static final int LIMIT_DAY = 10;
...
long sendTimeMilli = sendTime.toInstant(ZoneOffset.of(UTC)).toEpochMilli();
```

### 问题在哪里

- 时区写死 `+8`，多地区部署会有误差风险。
- 限流阈值写死，无法按客户/业务类型动态调整。

### 影响

- 国际化场景、分区部署场景下行为不可预期。

### 如何解决

1. 时区走系统配置或统一使用 UTC 存储后再显示换算。
2. 限流阈值配置化（支持客户级覆盖 + 默认值）。

### 设计思路

- 限流策略属于运营策略，天然需要可配置和分层覆盖能力。

---

## 建议的落地顺序（按风险收益比）

1. 先做“不会改行为但能防事故”的修复：
   - 风险 5（队列声明一致）
   - 风险 6（空指针防御）
   - 风险 3（路由排序去重问题）
2. 再做“行为一致性修复”：
   - 风险 1（敏感词策略语义统一）
   - 风险 2（internal 入口和 fee 字段一致）
3. 最后做“平台化能力完善”：
   - 风险 7（管理端可配置）
   - 风险 8（阈值配置化）
   - 风险 9（配置持久化与缓存同步）
   - 风险 10（时区与限流参数配置化）

---

## 结论

你说的黑名单命名问题，确实可以先通过数据库配置规避。  
但剩余风险里，优先级最高的是“策略行为与名称不一致（敏感词）”和“入口字段一致性（internal fee）”，这两项最容易造成线上认知偏差和运行时异常。


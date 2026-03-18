# beacon-api 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-api  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `05_beacon-api_module_analysis.md`
2. `06_beacon-api_refactor_guide.md`

## 1. 模块定位

`beacon-api` 是短信平台对外统一接入层，核心职责是：

1. 接收外部/内部发短信请求（HTTP）
2. 做基础校验与业务校验链编排（apikey、IP、签名、模板、余额等）
3. 组装 `StandardSubmit` 并投递到 MQ 预发送队列
4. 向调用方返回受理结果（`uid`、`sid`）

核心代码入口：

1. 启动类：`beacon-api/src/main/java/com/cz/api/ApiStarterApp.java`
2. 控制器：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`
3. 校验上下文：`beacon-api/src/main/java/com/cz/api/filter/CheckFilterContext.java`
4. 校验实现：`beacon-api/src/main/java/com/cz/api/filter/impl/*.java`
5. 缓存客户端：`beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`
6. 异常处理：`beacon-api/src/main/java/com/cz/api/advice/ApiExceptionHandler.java`
7. 响应封装：`beacon-api/src/main/java/com/cz/api/utils/R.java`、`beacon-api/src/main/java/com/cz/api/vo/ResultVO.java`

---

## 2. 当前实现概览

## 2.1 对外接口

`SmsController` 当前公开接口：

1. `POST /sms/single_send`：外部调用发送
2. `POST /sms/internal/single_send`：内部调用发送

关键处理链路：

1. 参数校验（`@Validated` + `BindingResult`）
2. 组装 `StandardSubmit`
3. `checkFilterContext.check(submit)` 执行过滤器链
4. `rabbitTemplate.convertAndSend(...)` 投递 MQ
5. 返回 `ResultVO`

## 2.2 与其他模块耦合关系

1. `beacon-cache`：通过 Feign 获取客户/签名/模板/余额缓存
2. `beacon-common`：共享常量、异常、模型（`StandardSubmit`）
3. `beacon-strategy`：消费 API 投递的预发送消息

---

## 3. 需要重构的代码、原因与重构方案

以下按优先级从高到低给出。

## 3.1 P0：`pom.xml` 依赖治理（重复依赖 + 模块职责漂移）

### 现状代码（需要重构）

文件：`beacon-api/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

...

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.1.0</version>
    <scope>compile</scope>
</dependency>
```

### 原因

1. OpenFeign 重复声明，增加维护成本和依赖歧义风险。
2. API 模块本身不直接访问 MySQL，数据库驱动放在 API 层会扩大攻击面和镜像体积。
3. 依赖边界不清晰，后续拆分与升级困难。

### 如何重构

1. 删除重复 `spring-cloud-starter-openfeign`。
2. 评估并移除 `mysql-connector-j`（若确实无 JDBC 使用）。
3. 统一由父 BOM 管理版本，子模块尽量不写版本号。

### 目标代码（建议）

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

---

## 3.2 P0：删除生产包中的测试入口与 `System.out.println`

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/controller/TestController.java`

```java
@GetMapping("/api/test")
public void test(){
    System.out.println("====================================");
    checkFilterContext.check(null);
}
```

文件：`beacon-api/src/main/java/com/cz/api/ApiStarterApp.java`

```java
System.out.println("ApiStarterApp  mission complete");
```

### 原因

1. `/api/test` 暴露在主代码路径中，存在误调用和安全风险。
2. `check(null)` 会触发非预期 NPE 路径，不应在运行环境中保留。
3. `System.out.println` 不利于日志聚合、检索和告警。

### 如何重构

1. 删除 `TestController`，或至少加 `@Profile("local")` 并迁移至测试包。
2. 统一改为 `Slf4j` 日志输出。
3. 启动完成日志包含关键元数据（服务名、端口、profile、版本号）。

### 目标代码（建议）

```java
@Slf4j
public class ApiStarterApp {
    public static void main(String[] args) {
        SpringApplication.run(ApiStarterApp.class, args);
        log.info("beacon-api started");
    }
}
```

---

## 3.3 P0：过滤器链上下文缺少空值防御，配置错误会直接 NPE

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/filter/CheckFilterContext.java`

```java
for (String filter : filterArray) {
    CheckFilter checkFilter = checkFiltersMap.get(filter);
    checkFilter.check(submit);
}
```

### 原因

1. 当 `filters` 配置拼写错误或存在空白项时，`checkFilter` 可能为 `null`。
2. 抛出 NPE 不可观测，问题定位成本高。
3. 配置与实现不一致时缺少 fail-fast 机制。

### 如何重构

1. 对 `filter` 做 `trim` 和空串过滤。
2. 找不到过滤器时抛出带上下文信息的业务异常（或配置异常）。
3. 增加启动时校验：配置的 filter key 必须都能在 IoC 中找到。

### 目标代码（建议）

```java
for (String raw : filters.split(",")) {
    String filter = raw == null ? "" : raw.trim();
    if (filter.isEmpty()) {
        continue;
    }
    CheckFilter checkFilter = checkFiltersMap.get(filter);
    if (checkFilter == null) {
        throw new IllegalStateException("unknown filter: " + filter);
    }
    checkFilter.check(submit);
}
```

---

## 3.4 P0：Feign 客户端弱类型，且同一路径定义多种返回类型

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`

```java
@GetMapping("/cache/hget/{key}/{field}")
Object hget(...);

@GetMapping("/cache/hget/{key}/{field}")
String hgetString(...);
```

### 原因

1. 同一路径在客户端映射为不同返回类型，依赖运行时反序列化行为，易出现类型漂移。
2. `Map`、`Set<Map>`、`Object` 大量原始类型，调用端充满强转。
3. 现有实现在 `FeeCheckFilter`、`IPCheckFilter` 中已经出现强制类型转换。

### 如何重构

1. 与 `beacon-cache` 联动新增 V2 typed 接口（如 string/long/list）。
2. API 模块 Feign 只保留强类型方法，逐步废弃 V1 `Object` 接口。
3. 在 API 侧新增 `CacheFacade` 做兼容转换，避免业务层分散强转逻辑。

### 目标代码（建议）

```java
@GetMapping("/v2/cache/hash/{key}/string/{field}")
String hgetString(@PathVariable("key") String key, @PathVariable("field") String field);

@GetMapping("/v2/cache/hash/{key}/long/{field}")
Long hgetLong(@PathVariable("key") String key, @PathVariable("field") String field);
```

---

## 3.5 P0：参数校验与默认值策略冲突（`state`）

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/form/SingleSendForm.java`

```java
@NotNull
private Integer state;
```

文件：`beacon-api/src/main/java/com/cz/api/form/InternalSingleSendForm.java`

```java
@NotNull(message = "state can not be null")
private Integer state;
```

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

```java
submit.setState(state == null ? 1 : state);
```

### 原因

1. DTO 层强制非空，控制器层却保留空值兜底，策略重复且不一致。
2. 代码意图不清晰，后续维护者难判断真实业务规则。

### 如何重构

二选一并全局统一：

1. 严格模式：`state` 必填，删除控制器默认值逻辑。
2. 兼容模式：`state` 可选，移除 `@NotNull`，由控制器统一默认为 `1`。

建议先走严格模式，避免“静默修正”影响上游。

### 目标代码（建议）

```java
submit.setState(state);
```

---

## 3.6 P1：内部发送接口认证能力需升级（防重放、可审计）

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

```java
@Value("${internal.sms.token:}")
private String internalSmsToken;

if (StringUtils.hasText(internalSmsToken) && !internalSmsToken.equals(requestToken)) {
    return R.error(-403, "internal token invalid");
}
```

### 原因

1. 静态 token 模式抗泄漏能力弱，缺少时效性与重放防护。
2. 缺少请求级签名和 nonce，无法抵御录包重放。
3. 审计维度有限，无法区分不同内部调用方身份。

### 如何重构

1. 升级为 `appId + timestamp + nonce + HMAC` 请求签名。
2. 对 `timestamp` 做时钟窗校验（如 5 分钟），并在缓存中记录 `nonce` 去重。
3. token/secret 全部放配置中心密文，不落仓库。
4. 如有网关，优先在网关层做内部路由白名单与鉴权下沉。

### 目标代码（建议）

```java
if (!internalAuthService.verify(appId, timestamp, nonce, signature, requestBodyDigest)) {
    return R.error(-403, "internal auth invalid");
}
```

---

## 3.7 P1：全局异常处理覆盖面不足

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/advice/ApiExceptionHandler.java`

```java
@ExceptionHandler(ApiException.class)
public ResultVO apiExceptionHandler(ApiException ex){
    return R.error(ex);
}
```

### 原因

1. 仅处理 `ApiException`，参数校验异常、JSON 反序列化异常无法统一返回。
2. 生产问题可能直接透传 500，影响接口稳定性与可观测性。

### 如何重构

1. 增加 `MethodArgumentNotValidException`、`ConstraintViolationException`、`HttpMessageNotReadableException`、`Exception` 处理器。
2. 统一错误码映射与日志字段（traceId、uri、clientId）。
3. 仅对未知异常打 `error`，业务异常打 `warn`。

### 目标代码（建议）

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResultVO handleValid(MethodArgumentNotValidException ex) { ... }

@ExceptionHandler(Exception.class)
public ResultVO handleUnknown(Exception ex) { ... }
```

---

## 3.8 P1：API 模块重复定义响应模型，应与 common 统一

### 现状代码（需要重构）

文件：

1. `beacon-api/src/main/java/com/cz/api/utils/R.java`
2. `beacon-api/src/main/java/com/cz/api/vo/ResultVO.java`
3. `beacon-common/src/main/java/com/cz/common/util/R.java`
4. `beacon-common/src/main/java/com/cz/common/vo/ResultVO.java`

### 原因

1. 两套 `R/ResultVO` 并存，语义和字段存在漂移风险。
2. 跨模块调用与接口文档难统一。
3. 后续接入网关统一响应拦截时成本高。

### 如何重构

1. 以 `beacon-common` 为唯一响应模型来源，API 侧删除重复类。
2. 设定 1 个小版本兼容期：保留旧字段映射，通知上游逐步迁移。
3. 后续在 common 引入泛型 `ResultVO<T>`，提升契约表达能力。

---

## 3.9 P1：过滤器实现中存在强制类型转换风险

### 现状代码（需要重构）

文件：`beacon-api/src/main/java/com/cz/api/filter/impl/FeeCheckFilter.java`

```java
Long balance = ((Integer) cacheClient.hget(...)).longValue();
```

文件：`beacon-api/src/main/java/com/cz/api/filter/impl/IPCheckFilter.java`

```java
List<String> ip = (List<String>) cacheClient.hget(...);
```

### 原因

1. 缓存值可能是 `Integer/Long/String` 任意一种，强转容易 `ClassCastException`。
2. 数据异常时缺少降级与明确错误提示。

### 如何重构

1. 全量迁移到 typed Feign 接口。
2. 过渡期增加转换工具：`safeToLong(Object)`、`safeToStringList(Object)`。
3. 对解析失败统一抛业务异常并记录 key/field。

### 目标代码（建议）

```java
Long balance = cacheClient.hgetLong(balanceKey, "balance");
if (balance == null) {
    throw new ApiException(ExceptionEnums.BALANCE_NOT_ENOUGH);
}
```

---

## 3.10 P2：消息投递可靠性与回执语义可继续加强

### 现状代码（可优化）

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

```java
rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PRE_SEND, submit,
        new CorrelationData(submit.getSequenceId().toString()));
return R.ok();
```

### 原因

1. 当前返回语义是“已受理”，而非“已可靠投递”，需在接口文档中明确。
2. confirm/return 回调在配置类内记录日志，但缺少业务侧补偿联动。

### 如何重构

1. 保持“受理成功”语义，但在文档中写清楚异步处理模型。
2. 增加发送失败补偿（重试队列或发送失败事件）。
3. 引入投递状态表/缓存，用于追踪 `sid` 状态。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，先稳住风险）

1. 清理依赖：去掉重复 OpenFeign、评估移除 MySQL 驱动。
2. 下线测试入口：移除 `TestController` 与 `System.out.println`。
3. 加固过滤器链：`CheckFilterContext` 空值防御和配置 fail-fast。
4. 统一 `state` 规则：删除冲突逻辑。

## 阶段二（P1，契约与治理）

1. Feign typed 化：联动 `beacon-cache` 发布 V2 typed API。
2. 强化异常处理：补齐全局异常映射。
3. 统一响应模型：收敛到 `beacon-common`。
4. 内部接口鉴权升级：签名 + 时间窗 + nonce。

## 阶段三（P2，可靠性与长期演进）

1. MQ 投递状态追踪与补偿。
2. 统一链路日志规范（traceId/clientId/sid）。
3. 清理历史兼容代码与废弃接口。

---

## 5. 测试与验收清单

## 5.1 单元测试

1. `CheckFilterContext`：未知 filter key、空白 filter、正常顺序执行。
2. `SmsController`：参数非法、内部 token 鉴权失败、正常入队。
3. `FeeCheckFilter/IPCheckFilter`：缓存返回不同类型时的解析行为。

## 5.2 集成测试

1. API -> Cache：typed Feign 接口序列化/反序列化一致性。
2. API -> MQ：`sid` 生成、消息体字段、CorrelationData 对齐。
3. 全局异常：校验错误、JSON 格式错误、未知异常返回码一致。

## 5.3 回归与压测

1. 回归外部接口字段兼容性（`uid/sid/code/msg`）。
2. 压测过滤器链与 MQ 入队吞吐（重点关注 p95/p99）。
3. 灰度期间监控 `4xx/5xx`、鉴权失败率、消息投递失败率。

---

## 6. 跨模块联动建议

1. 与 `beacon-cache`：先发布 typed V2 接口，再切换 API 客户端。
2. 与 `beacon-common`：先统一 `ResultVO/R`，再清理 API 本地重复类。
3. 与 `beacon-monitor`：补充 API 维度监控项（鉴权失败、过滤器失败、入队失败）。

建议按“先兼容新增、再流量切换、最后删除旧实现”的方式推进，避免一次性改动过大。


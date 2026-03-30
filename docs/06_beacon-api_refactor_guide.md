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

## 阶段二（P1，契约与治理）

1. 强化异常处理：补齐全局异常映射。
2. 统一响应模型：收敛到 `beacon-common`。
3. 过滤器与缓存调用强类型化，减少业务层分散强转。
4. 内部接口鉴权升级：签名 + 时间窗 + nonce。

## 阶段三（P2，可靠性与长期演进）

1. MQ 投递状态追踪与补偿。
2. 统一链路日志规范（traceId/clientId/sid）。
3. 清理历史兼容代码与废弃接口。

---

## 5. 测试与验收清单

## 5.1 单元测试

1. `SmsController`：参数非法、内部 token 鉴权失败、正常入队。
2. `FeeCheckFilter/IPCheckFilter`：缓存返回不同类型时的解析行为。

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


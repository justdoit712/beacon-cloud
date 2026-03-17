# beacon-common 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-common  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-common` 是整个项目的“公共契约与基础工具模块”，不直接对外提供 HTTP/MQ 消费能力，主要承担：

1. 统一跨模块数据模型（如 `StandardSubmit`、`StandardReport`、`ResultVO`）。
2. 统一错误码与异常体系（`ExceptionEnums` + `ApiException/StrategyException/SearchException`）。
3. 统一缓存 Key、MQ 队列名、短信业务常量定义。
4. 提供底层工具（雪花 ID、JSON、运营商映射、CMPP 状态映射、临时内存仓储）。

这个模块被多个业务服务直接依赖，是“高耦合中心”。一旦契约变动，会联动影响 `beacon-api`、`beacon-strategy`、`beacon-smsgateway`、`beacon-search`、`beacon-webmaster` 等多个模块。

---

## 2. 代码结构与类清单

源码目录：`beacon-common/src/main/java/com/cz/common`

按包划分如下：

| 包 | 数量 | 代表类 | 作用 |
|---|---:|---|---|
| `constant` | 5 | `CacheKeyConstants`, `RabbitMQConstants` | 跨模块常量约定 |
| `enums` | 4 | `ExceptionEnums`, `MobileOperatorEnum` | 业务枚举 |
| `exception` | 3 | `ApiException`, `StrategyException` | 统一业务异常 |
| `model` | 2 | `StandardSubmit`, `StandardReport` | MQ 与业务链路主模型 |
| `util` | 8 | `SnowFlakeUtil`, `JsonUtil` | 通用工具类 |
| `vo` | 1 | `ResultVO` | 统一返回对象 |

---

## 3. 核心契约说明

## 3.1 `StandardSubmit`

文件：`beacon-common/src/main/java/com/cz/common/model/StandardSubmit.java`

该对象贯穿发送主链路：

1. 在 `beacon-api` 构建基础字段（apikey/mobile/text/state/uid）。
2. 在 API 校验链补全客户、签名、模板、费用等信息。
3. 进入 `beacon-strategy` 做黑名单、敏感词、限流、路由、扣费。
4. 进入 `beacon-smsgateway` 做 CMPP 下发。
5. 进入 `beacon-search` 做日志入库。

关键字段分层：

1. `apiKey`, `clientId`, `uid`, `mobile`, `text`, `state`：客户请求信息。
2. `fee`, `sign`, `signId`, `realIp`：API 校验与费用信息。
3. `operatorId`, `areaCode`, `area`：归属地策略信息。
4. `channelId`, `srcNumber`：路由结果信息。
5. `reportState`, `errorMsg`：过程与结果状态。
6. `sendTime`, `sequenceId`：链路追踪主键。

## 3.2 `StandardReport`

文件：`beacon-common/src/main/java/com/cz/common/model/StandardReport.java`

该对象用于“状态报告与回调”链路：

1. 策略拒绝或网关回执后封装报告。
2. `beacon-push` 根据 `callbackUrl` 做回调。
3. `beacon-search` 根据 `sequenceId` 更新日志状态。

关键字段：

1. 识别字段：`sequenceId`, `apikey`, `clientId`, `uid`, `mobile`。
2. 状态字段：`reportState`, `errorMsg`。
3. 推送控制字段：`isCallback`, `callbackUrl`, `resendCount`。
4. 检索补偿字段：`reUpdate`（用于 ES 更新补偿流程）。

## 3.3 `ResultVO` + `R`

文件：

1. `beacon-common/src/main/java/com/cz/common/vo/ResultVO.java`
2. `beacon-common/src/main/java/com/cz/common/util/R.java`

统一接口返回格式：

1. 成功默认 `code=0`。
2. 支持普通对象与分页返回（`total` + `rows`）。
3. 失败支持 `ExceptionEnums` 或自定义错误码。

---

## 4. 常量域详细说明

## 4.1 缓存 Key 契约 `CacheKeyConstants`

文件：`beacon-common/src/main/java/com/cz/common/constant/CacheKeyConstants.java`

该类定义了各服务共享的 Redis key 前缀，是“缓存数据模型”的事实标准。代表性前缀：

1. 客户配置：`client_business:`
2. 签名模板：`client_sign:`, `client_template:`
3. 余额：`client_balance:`
4. 策略数据：`dirty_word`, `black:`, `limit:*`
5. 路由数据：`client_channel:`, `channel:`

## 4.2 MQ 契约 `RabbitMQConstants`

文件：`beacon-common/src/main/java/com/cz/common/constant/RabbitMQConstants.java`

定义了从 API 到策略、策略到网关、网关到检索/回调的关键队列与交换机命名，例如：

1. `SMS_PRE_SEND`：API -> Strategy
2. `SMS_GATEWAY` 前缀：Strategy -> Gateway（按 channelId 动态队列）
3. `SMS_WRITE_LOG`：Strategy/Gateway -> Search
4. `SMS_PUSH_REPORT`：Strategy/Gateway -> Push
5. `SMS_GATEWAY_*`：网关延迟与死信链路

## 4.3 短信与后台常量

1. `SmsConstant`：短信类型与状态码约定。
2. `ApiConstant`：签名前后缀、计费单价。
3. `WebMasterConstants`：后台鉴权、验证码 key 等常量。

---

## 5. 工具类能力分析

## 5.1 `SnowFlakeUtil`

文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

职责：

1. 生成全局唯一 `long` ID，供消息序列号使用。
2. 支持配置化 `machineId` / `serviceId`。
3. 在时钟回拨时抛出业务异常。

现状评价：

1. 具备基本可用性，满足当前单机/小规模部署。
2. 与 Spring 生命周期结合（`@Component` + `@PostConstruct`）合理。

## 5.2 `JsonUtil`

文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`

职责：

1. 对象转 JSON 字符串。

现状评价：

1. 功能极简。
2. 仅封装 `writeValueAsString`，无复杂转换逻辑。

## 5.3 `OperatorUtil`

文件：`beacon-common/src/main/java/com/cz/common/util/OperatorUtil.java`

职责：

1. 把运营商名称映射为运营商 ID，用于策略判断。

## 5.4 CMPP 映射与临时仓储工具

文件：

1. `beacon-common/src/main/java/com/cz/common/util/CMPP2ResultUtil.java`
2. `beacon-common/src/main/java/com/cz/common/util/CMPP2DeliverUtil.java`
3. `beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java`
4. `beacon-common/src/main/java/com/cz/common/util/CMPPDeliverMapUtil.java`

职责：

1. CMPP 返回码/状态码转可读描述。
2. 网关异步回执阶段临时缓存 `submit/report` 对象。

---

## 6. 依赖关系与影响面

从 `src/main/java` 导入统计看，`beacon-common` 被广泛依赖：

1. `beacon-strategy`：49 处导入（最重依赖）。
2. `beacon-webmaster`：37 处导入。
3. `beacon-api`：36 处导入。
4. `beacon-smsgateway`：20 处导入。
5. `beacon-search`：10 处导入。
6. `beacon-push`、`beacon-monitor`：轻度依赖。

结论：

1. 这是高影响模块。
2. 任意字段名、常量值、异常码变更都可能引发跨模块回归。

---

## 7. 主要风险与技术债

## 7.1 契约一致性风险

1. `StandardSubmit.apiKey` 与 `StandardReport.apikey` 命名不一致。
2. 链路里仍有 `BeanUtils.copyProperties(...)` 复制，关键字段容易静默丢失。

## 7.2 异常码集中但边界混合

1. `ExceptionEnums` 同时混合 API、策略、搜索、后台登录等多域错误。
2. 枚举膨胀后会导致“语义跨域污染”，维护成本升高。

## 7.3 工具类临时仓储仍是进程内状态

1. `CMPPSubmitRepoMapUtil` 与 `CMPPDeliverMapUtil` 仍然属于本地进程内缓存。
2. 进程重启后上下文会丢失，多实例之间也不能共享。
3. 监控与外部化存储能力仍有限。

## 7.4 测试覆盖仍不充分

1. 公共契约层仍缺少更系统的跨模块兼容测试与发布前回归矩阵。
2. 一旦主链路对象继续演进，回归成本仍然偏高。

---

## 8. 改造建议（按优先级）

## P0（优先执行）

1. 为 `StandardSubmit`、`StandardReport` 增加契约回归测试（JSON 序列化/反序列化、字段兼容测试）。
2. 继续给 CMPP 临时缓存补监控、命中率与外部化演进方案。
3. 围绕 `apiKey/apikey` 命名不一致增加专门的兼容与复制测试。

## P1（中期）

1. 按领域拆分异常码枚举（例如 `ApiErrorCodes`、`StrategyErrorCodes`、`WebErrorCodes`），保留兼容映射层。
2. 规范 DTO 字段命名，优先收敛 `apiKey/apikey` 这一类跨对象命名债。

## P2（长期）

1. 将公共契约模块引入“版本化策略”（文档化 + 兼容窗口 + 发布说明）。
2. 建立跨模块 contract test（至少覆盖 API -> Strategy -> Gateway 主链路对象）。

---

## 9. 结论

`beacon-common` 仍然是高耦合基础模块。  
当前更值得优先关注的是“跨对象命名不一致 + 契约兼容测试 + 进程内缓存外部化”，否则主链路改动仍会放大回归成本。

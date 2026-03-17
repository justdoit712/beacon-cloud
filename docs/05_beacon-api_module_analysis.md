# beacon-api 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-api  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-api` 是短信平台的接入网关（业务入口层），主要职责：

1. 接收外部/内部发送请求。
2. 对外部请求执行校验链（apikey、IP、签名、模板、费用、手机号等）。
3. 生成全局序列号并投递到策略队列。
4. 快速返回受理结果（异步发送）。

在整体链路中的位置：

`webmaster(管理端调用)` -> `beacon-api` -> `RabbitMQ(SMS_PRE_SEND)` -> `beacon-strategy`

---

## 2. 模块结构

源码目录：`beacon-api/src/main/java/com/cz/api`

按分层划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `ApiStarterApp` | 服务启动、注册发现、Feign 启用 |
| 控制器层 | `SmsController`, `TestController` | 对外/内部发送入口，测试入口 |
| 校验链层 | `CheckFilterContext` + 6 个 `CheckFilter` 实现 | 可配置顺序的校验管道 |
| 远程依赖层 | `BeaconCacheClient` | 调用 `beacon-cache` 读取业务配置 |
| MQ 配置层 | `RabbitMQConfig`, `RabbitTemplateConfig` | 队列声明、消息序列化、confirm/return 回调 |
| 异常处理层 | `ApiExceptionHandler` | 统一业务异常返回 |
| 入参对象层 | `SingleSendForm`, `InternalSingleSendForm` | 请求参数校验 |
| 工具与返回层 | `PhoneFormatCheckUtil`, `R`, `ResultVO` | 模块内返回和手机号校验 |

---

## 3. 核心流程

## 3.1 外部发送接口 `/sms/single_send`

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

流程：

1. 参数校验（Bean Validation + BindingResult）。
2. 解析真实 IP。
3. 构建 `StandardSubmit`（初始字段）。
4. 执行 `checkFilterContext.check(submit)`。
5. 生成雪花 ID、写入发送时间。
6. 投递 `RabbitMQConstants.SMS_PRE_SEND`。
7. 返回 `uid` + `sid`（序列号）。

## 3.2 内部发送接口 `/sms/internal/single_send`

同文件：

1. 参数校验。
2. 校验 `X-Internal-Token`（仅当 `internal.sms.token` 配置存在时生效）。
3. 基于 `apikey` 查询 `clientId`。
4. 构建 `StandardSubmit`。
5. 直接入队（不走 `checkFilterContext`）。

现状结论：

1. 内部接口强化了“服务间调用便捷性”。
2. 与外部接口在校验行为上不一致，存在“同类请求不同约束”风险。

---

## 4. 校验链机制（CheckFilterContext）

文件：`beacon-api/src/main/java/com/cz/api/filter/CheckFilterContext.java`

特征：

1. 基于 Spring Map 注入所有 `CheckFilter` Bean。
2. 从配置读取过滤器顺序：`filters`。
3. 默认顺序：`apikey,ip,sign,template`。
4. 运行时按名称逐个执行。

当前实现的过滤器：

1. `apikey` -> `ApiKeyCheckFilter`
2. `ip` -> `IPCheckFilter`
3. `sign` -> `SignCheckFilter`
4. `template` -> `TemplateCheckFilter`
5. `fee` -> `FeeCheckFilter`
6. `mobile` -> `MobileCheckFilter`

说明：

1. `fee` 和 `mobile` 虽已实现，但默认不在 `filters` 默认值中。
2. 是否启用由 Nacos 配置决定。

---

## 5. 过滤器详细职责

## 5.1 `ApiKeyCheckFilter`

1. 从 `client_business:{apikey}` 取客户业务配置。
2. 校验 apikey 合法性。
3. 回填 `submit.clientId`。

## 5.2 `IPCheckFilter`

1. 从客户配置中读取 `ipAddress`。
2. 若白名单为空则放行。
3. 若不为空且请求 IP 不在名单中则拒绝。

## 5.3 `SignCheckFilter`

1. 检查短信内容签名包裹（前后缀）。
2. 提取签名文本。
3. 校验签名是否在客户签名集合中。
4. 回填 `sign` 与 `signId`。

## 5.4 `TemplateCheckFilter`

1. 去除签名后匹配模板。
2. 支持整段匹配。
3. 支持占位符 `#...#` 规则匹配（通过前后缀）。

## 5.5 `FeeCheckFilter`

1. 按短信字数估算费用（70/67 规则）。
2. 从 `client_balance:{clientId}` 读取余额并校验。
3. 回填 `submit.fee`。

## 5.6 `MobileCheckFilter`

1. 校验手机号格式（中国手机号正则）。

---

## 6. 外部依赖关系

## 6.1 上游调用方

1. `beacon-webmaster` 通过 `ApiSmsClient` 调用内部接口  
文件：`beacon-webmaster/src/main/java/com/cz/webmaster/client/ApiSmsClient.java`

## 6.2 下游服务

1. `beacon-cache`（Feign）  
文件：`beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`
2. `beacon-strategy`（RabbitMQ 队列）  
队列常量：`SMS_PRE_SEND`

---

## 7. MQ 与可靠性设计

相关文件：

1. `beacon-api/src/main/java/com/cz/api/config/RabbitMQConfig.java`
2. `beacon-api/src/main/java/com/cz/api/config/RabbitTemplateConfig.java`

已有能力：

1. 声明了预发送队列 `SMS_PRE_SEND`。
2. 配置了 JSON 消息转换（支持 `LocalDateTime`）。
3. 配置了 ConfirmCallback（exchange 投递确认）。
4. 配置了 ReturnCallback（路由失败告警）。

现状评价：

1. 具备基础生产可用能力。
2. 缺少“失败后补偿策略”落地（当前主要记录日志，不含重投或落库）。

---

## 8. 主要风险与技术债

## 8.1 内外接口行为不一致（高风险）

文件：`beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

1. 外部接口走全量校验链。
2. 内部接口只做 token + clientId 解析，未执行校验链。
3. 可能导致内部调用跳过费用/模板/敏感约束前置条件。

## 8.2 测试入口存在空指针风险（高风险）

文件：`beacon-api/src/main/java/com/cz/api/controller/TestController.java`

1. `checkFilterContext.check(null)` 直接传入 null。
2. 若访问该接口，极易触发运行时异常。
3. 测试入口应隔离到非生产 profile。

## 8.3 类型转换脆弱（高风险）

1. `FeeCheckFilter` 对余额做 `(Integer)` 强转。
2. `IPCheckFilter` 对缓存值做 `List<String>` 强转。
3. 缓存数据形态变化时，可能直接抛 `ClassCastException`。

## 8.4 Feign 接口存在重复路径映射（中风险）

文件：`beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`

1. `hget` 和 `hgetString` 指向同一路径 `/cache/hget/{key}/{field}`。
2. 依赖调用方自行约束类型，缺少显式契约分层。

## 8.5 模块内重复返回模型（中风险）

文件：

1. `beacon-api/src/main/java/com/cz/api/vo/ResultVO.java`
2. `beacon-common/src/main/java/com/cz/common/vo/ResultVO.java`

问题：

1. API 模块维护了自己的一套 `ResultVO/R`，与 common 中同名职责重叠。
2. 长期容易产生跨模块返回格式不一致。

## 8.6 依赖声明冗余/异常（中风险）

文件：`beacon-api/pom.xml`

1. `spring-cloud-starter-openfeign` 重复声明两次。
2. 引入 `mysql-connector-j`，但模块代码未体现 MySQL 直接使用。

## 8.7 测试覆盖不足（中风险）

1. 仅有一个空测试 `CheckFilterContextTest`。
2. 关键校验链与控制器分支缺乏自动化回归保护。

---

## 9. 改造建议（按优先级）

## P0（优先）

1. 统一 internal/external 接口关键字段约束，至少补齐 `fee` 前置保障。
2. 下线或隔离 `TestController`。
3. 修复强转脆弱点，增加空值与类型防御。
4. 增加核心链路单元测试与集成测试：
   `single_send` 正常流、apikey 错误、余额不足、模板不匹配、internal token 错误。

## P1（中期）

1. 清理重复依赖与无效依赖。
2. 统一返回模型到 `beacon-common`，减少重复实现。
3. Feign 客户端按语义拆分返回类型，降低路径复用造成的歧义。

## P2（长期）

1. 将校验链结果引入可观测指标（每个 filter 的拒绝率、耗时）。
2. 建立“配置驱动校验链”的灰度发布与回滚机制。

---

## 10. 结论

`beacon-api` 作为入口模块，主路径设计清晰，异步化和基础可靠性配置到位。  
当前主要风险不在“功能缺失”，而在“校验一致性”和“类型稳定性”。  
优先治理 internal 路径一致性、测试接口暴露和强转脆弱点，可显著降低线上不可控异常。

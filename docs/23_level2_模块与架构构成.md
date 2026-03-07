# Level 2 - 模块与架构构成

本文聚焦系统内部构造，回答“项目由什么组件构成、组件之间如何交互”。  
内容全部基于当前仓库代码与配置。

## 1. 系统分层视图

按职责可以分成 5 层：

1. 接入层：`beacon-api`、`beacon-webmaster`
2. 策略层：`beacon-strategy`
3. 通道网关层：`beacon-smsgateway`
4. 日志与回调层：`beacon-search`、`beacon-push`
5. 平台支撑层：`beacon-cache`、`beacon-monitor`、`beacon-common`、`beacon-test`

## 2. 模块拆解与边界

| 模块 | 服务名/启动类 | 主要职责 | 主要上游 | 主要下游 |
| --- | --- | --- | --- | --- |
| beacon-api | `beacon-api` / `ApiStarterApp` | 提供短信发送接口，执行接入校验，投递预发送消息 | 客户系统、webmaster内部调用 | strategy（MQ）、cache（Feign） |
| beacon-strategy | `beacon-strategy` / `StrategyStarterApp` | 消费预发送消息，按客户策略链处理，路由到通道队列 | api（MQ） | smsgateway（MQ）、search（MQ）、push（MQ）、cache（Feign） |
| beacon-smsgateway | `beacon-smsgateway` / `SmsGatewayStarterApp` | 监听通道队列，封装 CMPP 报文下发，处理运营商响应与状态报告 | strategy（MQ） | search（MQ）、push（MQ）、CMPP 服务端、cache（Feign） |
| beacon-search | `beacon-search` / `SearchStarterApp` | 消费日志消息写入 ES，消费状态消息更新 ES，提供检索接口 | strategy/smsgateway（MQ）、webmaster（HTTP） | webmaster（HTTP） |
| beacon-push | `beacon-push` / `PushStarterApp` | 消费状态推送消息，调用客户回调地址，失败按延迟策略重试 | strategy/smsgateway（MQ） | 客户回调地址（HTTP） |
| beacon-cache | `beacon-cache` / `CacheStarterApp` | 提供统一 Redis 读写接口，执行调用方签名鉴权与权限控制 | api/strategy/smsgateway/monitor/test（Feign） | Redis |
| beacon-webmaster | `beacon-webmaster` / `WebMasterStarterApp` | 运营后台（Shiro + MyBatis），管理客户/通道/策略，调用发送与查询链路 | 管理端用户 | api（Feign）、search（Feign）、MySQL |
| beacon-monitor | `beacon-monitor` / `MonitorStarterApp` | XXL-Job 任务：队列积压巡检、客户余额巡检并发邮件 | xxl-job-admin 调度 | RabbitMQ、cache（Feign）、SMTP |
| beacon-common | - | 公共常量、模型、异常、工具与签名逻辑 | 全部业务模块 | 全部业务模块 |
| beacon-test | `beacon-test` / `TestStarter` | 测试/初始化相关实体与 mapper，包含 cache Feign 客户端 | 开发联调 | MySQL、cache（Feign） |

## 3. 服务注册与配置关系

当前配置模式：

1. `beacon-api`、`beacon-strategy`、`beacon-smsgateway`、`beacon-search`、`beacon-push`、`beacon-cache`、`beacon-monitor` 使用 `bootstrap.yml`。
2. `beacon-webmaster`、`beacon-test` 使用 `application.yml`（同时配置 Nacos）。
3. 多数模块默认 `spring.profiles.active=dev`，并配置 `Nacos server-addr=192.168.88.128:8848`。

## 4. 跨模块同步调用（HTTP/Feign）

当前可见的关键 Feign 关系：

1. `beacon-api` -> `beacon-cache`（读取客户、签名、模板、余额等缓存信息）。
2. `beacon-strategy` -> `beacon-cache`（读取策略、通道、黑名单、限流、余额等缓存并写入限流/扣费数据）。
3. `beacon-smsgateway` -> `beacon-cache`（读取回调开关与回调地址）。
4. `beacon-monitor` -> `beacon-cache`（读取 `keys` 和余额相关数据）。
5. `beacon-webmaster` -> `beacon-api`（内部发送接口）与 `beacon-search`（查询、统计接口）。

## 5. MQ 拓扑（当前实现）

| 消息实体 | 交换机/队列 | 生产方 | 消费方 | 声明位置（代码） |
| --- | --- | --- | --- | --- |
| 预发送短信 | `sms_pre_send_topic` | api | strategy | api/strategy `RabbitMQConfig` |
| 手机归属地异步信息 | `mobile_area_operator_topic` | strategy(phase) | 当前仓库内未检索到监听类 | strategy `RabbitMQConfig` |
| 发送日志 | `sms_write_log_topic` | strategy(异常分支)、smsgateway(提交响应) | search | strategy `RabbitMQConfig` |
| 客户状态推送 | `sms_push_report_topic` | strategy(异常分支)、smsgateway(状态报告) | push | strategy `RabbitMQConfig` |
| 通道业务队列 | `sms_gateway_topic_{channelId}` | strategy(route) | smsgateway（由 `gateway.sendtopic` 指定监听） | strategy 动态声明队列；smsgateway 声明 `gateway.sendtopic` 队列 |
| 状态更新链路（普通） | `sms_gateway_normal_exchange` -> `sms_gateway_normal_queue` | smsgateway、search(重投) | RabbitMQ TTL/死信转发 | smsgateway/search `RabbitMQConfig` |
| 状态更新链路（死信） | `sms_gateway_dead_exchange` -> `sms_gateway_dead_queue` | normal queue TTL 转入 | search | smsgateway/search `RabbitMQConfig` |
| 回调重试延迟链路 | `push_delayed_exchange` -> `push_delayed_queue` | push | push | push `RabbitMQConfig` |

## 6. 数据存储构成

### 6.1 MySQL（后台管理数据）

从 `beacon-webmaster` mapper 可见的核心表：

1. `client_business`
2. `client_channel`
3. `channel`
4. `sms_user`
5. `sms_role`
6. `sms_menu`

### 6.2 Redis（运行时配置与状态）

键前缀由 `CacheConstant` 定义，主要包括：

1. 客户与签名模板：`client_business:*`、`client_sign:*`、`client_template:*`
2. 余额：`client_balance:*`
3. 策略与路由：`client_channel:*`、`channel:*`
4. 黑名单与携号转网：`black:*`、`transfer:*`
5. 号段：`phase:*`
6. 限流：`limit:minutes:*`、`limit:hours:*`、`limit:days:*`

### 6.3 Elasticsearch（短信日志检索）

`beacon-search` 当前索引命名规则：

1. 前缀：`sms_submit_log_`
2. 年份后缀：`{yyyy}`
3. 当前查询索引：`sms_submit_log_{当前年份}`

## 7. 安全与访问控制（已实现机制）

### 7.1 Cache 服务鉴权

`beacon-cache` 对调用方启用签名校验：

1. 请求头：`X-Cache-Caller`、`X-Cache-Timestamp`、`X-Cache-Sign`
2. 签名算法：`HmacSHA256`（`CacheAuthSignUtil`）
3. 权限模型：`READ`、`WRITE`、`KEYS`、`TEST`（由 `cache.security.caller-permissions` 控制）

### 7.2 内部发送接口令牌

`beacon-api` 的 `/sms/internal/single_send` 支持 `X-Internal-Token` 校验（配置项 `internal.sms.token`）。

### 7.3 后台权限

`beacon-webmaster` 使用 Shiro 处理登录、菜单、角色与资源访问控制。


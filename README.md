# beacon-cloud

`beacon-cloud` 是一个基于 Spring Cloud 的短信平台微服务项目，覆盖短信提交、策略过滤、网关下发、状态回执、日志检索、运营后台和监控告警等完整链路。

## 1. 技术栈

- JDK: `1.8`
- Spring Boot: `2.3.12.RELEASE`
- Spring Cloud: `Hoxton.SR12`
- Spring Cloud Alibaba: `2.2.6.RELEASE`
- Maven: 多模块聚合构建
- 中间件:
- Nacos（注册中心 + 配置中心）
- MySQL `5.7.x`
- Redis `5.x`
- RabbitMQ `3.8.x`
- Elasticsearch `7.6.2`
- xxl-job `2.3.0`

## 2. 项目结构

根工程为 `pom` 聚合模块，子模块如下：

- `beacon-api`: 短信接入层，对外提供发送接口，执行前置校验并投递预发送消息。
- `beacon-strategy`: 策略引擎，消费预发送消息，执行黑名单/敏感词/限流/路由等策略。
- `beacon-smsgateway`: 运营商网关适配层（CMPP + Netty），负责实际下发与回执对接。
- `beacon-search`: Elasticsearch 日志服务，写入提交日志并更新回执状态。
- `beacon-push`: 回执推送服务，向客户回调地址推送状态报告（含延迟重试）。
- `beacon-cache`: 统一缓存服务，对 Redis 能力做 HTTP/Feign 封装，并带调用鉴权。
- `beacon-monitor`: 监控告警服务（xxl-job），检查队列积压和客户余额并邮件通知。
- `beacon-webmaster`: 运营后台（Shiro + MyBatis + 静态前端），管理客户、通道、策略等数据。
- `beacon-common`: 公共模块，提供常量、模型、异常、工具类和安全签名能力。
- `beacon-test`: 测试/数据初始化辅助模块（含 Mapper 与缓存写入相关能力）。

## 3. 核心链路

短信发送主链路如下：

1. 调用 `beacon-api` 接口 `POST /sms/single_send`（或内部接口）。
2. `beacon-api` 组装 `StandardSubmit`，投递到 `sms_pre_send_topic`。
3. `beacon-strategy` 消费后执行策略链，按通道路由到 `sms_gateway_topic_{channelId}`。
4. `beacon-smsgateway` 消费路由消息，转为 CMPP 报文下发运营商。
5. 下发结果/回执进入日志与推送分支：
- 发送日志写入队列 `sms_write_log_topic`，由 `beacon-search` 入 ES。
- 状态报告写入队列 `sms_push_report_topic`，由 `beacon-push` 回调客户系统。
- 网关更新分支使用 `sms_gateway_normal_exchange/sms_gateway_dead_queue`，由 `beacon-search` 更新日志状态。

主要 MQ 常量定义在：

- `beacon-common/src/main/java/com/cz/common/constant/RabbitMQConstants.java`

## 4. 配置中心约定

大部分服务使用 `bootstrap.yml` 指向 Nacos，约定由配置中心维护环境配置：

- `spring.profiles.active=dev`
- `spring.cloud.nacos.discovery.server-addr=192.168.88.128:8848`
- `spring.cloud.nacos.config.server-addr=192.168.88.128:8848`

典型 DataId 约定为：

- `beacon-api-dev.yml`
- `beacon-strategy-dev.yml`
- `beacon-smsgateway-dev.yml`
- `beacon-search-dev.yml`
- `beacon-push-dev.yml`
- `beacon-monitor-dev.yml`
- `beacon-cache-dev.yml`

说明：

- `beacon-webmaster` 与 `beacon-test` 在仓库中使用 `application.yml`，同时仍接入 Nacos。
- 端口等运行参数主要应在 Nacos 配置中统一管理（`beacon-test` 本地示例端口为 `20000`）。

## 5. 运行前准备

请先准备以下依赖服务：

1. Nacos（注册中心 + 配置中心）
2. MySQL（项目使用库名示例：`duanxin_pingtai`）
3. Redis
4. RabbitMQ
5. Elasticsearch
6. xxl-job-admin（如启用监控任务）

并在 Nacos/环境变量中补齐各服务配置，例如：

- 数据库连接
- Redis/RabbitMQ/ES 地址
- SMTP 邮件配置（`beacon-monitor`）
- 网关 CMPP 连接参数（`beacon-smsgateway`）
- 内部鉴权参数（如 `internal.sms.token`、`cache.client.auth.*`）

### 5.1 `beacon-smsgateway` 额外前置条件（必须）

网关模块启动前，必须先准备一个可连接的 `CMPP Server`（真实运营商通道或本地模拟服务），否则 `beacon-smsgateway` 会持续重连，无法完成下发链路联调。

当前代码默认连接参数（来自 `beacon-smsgateway/src/main/java/com/cz/smsgateway/netty4/NettyStartCMPP.java`）：

- `host=127.0.0.1`
- `port=7890`
- `serviceId=laozheng`
- `pwd=JavaLaoZheng123!`

因此本地联调时，请先启动模拟 `CMPP Server` 并监听 `127.0.0.1:7890`，再启动 `beacon-smsgateway`。
模拟服务至少需要支持以下协议交互：

- `CMPP_CONNECT_RESP`
- `CMPP_SUBMIT_RESP`
- `CMPP_DELIVER`（状态报告）
- `CMPP_ACTIVE_TEST_RESP`（心跳响应）

## 6. 构建与启动

### 6.1 构建

在根目录执行：

```bash
mvn clean package -DskipTests
```

### 6.2 推荐启动顺序

建议按依赖从底到上启动：

1. `beacon-cache`
2. `beacon-search`
3. `beacon-push`
4. 启动并确认模拟 `CMPP Server` 可用（`127.0.0.1:7890`）
5. `beacon-smsgateway`
6. `beacon-strategy`
7. `beacon-api`
8. `beacon-webmaster`
9. `beacon-monitor`
10. `beacon-test`（按需）

可在 IDE 直接运行各模块启动类：

- `com.cz.cache.CacheStarterApp`
- `com.cz.search.SearchStarterApp`
- `com.cz.push.PushStarterApp`
- `com.cz.smsgateway.SmsGatewayStarterApp`
- `com.cz.strategy.StrategyStarterApp`
- `com.cz.api.ApiStarterApp`
- `com.cz.webmaster.WebMasterStarterApp`
- `com.cz.monitor.MonitorStarterApp`
- `com.cz.test.TestStarter`

## 7. 关键接口示例

### 7.1 短信发送

- 外部发送：`POST /sms/single_send`
- 内部发送：`POST /sms/internal/single_send`

接口定义位于：

- `beacon-api/src/main/java/com/cz/api/controller/SmsController.java`

### 7.2 日志检索

- `POST /search/sms/list`
- `POST /search/sms/countSmsState`

接口定义位于：

- `beacon-search/src/main/java/com/cz/search/controller/SmsSearchController.java`

## 8. 安全与配置注意事项

当前仓库中的部分配置文件包含示例地址/账号信息，部署前请务必替换并外置化：

- 不要在仓库中保存真实数据库、邮箱、网关密码等敏感信息。
- 建议统一使用 Nacos 密文配置或环境变量注入。
- `beacon-cache` 开启鉴权时，调用方需携带以下 Header：
- `X-Cache-Caller`
- `X-Cache-Timestamp`
- `X-Cache-Sign`

相关实现参考：

- `beacon-cache/src/main/java/com/cz/cache/security/CacheAuthInterceptor.java`
- `beacon-common/src/main/java/com/cz/common/security/CacheAuthSignUtil.java`

## 9. 目录补充说明

- `docs/`: 各模块重构建议文档（refactor guide）
- `db/`: 模块分析与风险说明文档

---

如果你是第一次接手该项目，建议先按以下顺序阅读：

1. `beacon-common`（常量/模型）
2. `beacon-api` + `beacon-strategy`（发送入口与策略链）
3. `beacon-smsgateway`（CMPP 下发）
4. `beacon-search` + `beacon-push`（日志与回执）
5. `beacon-webmaster`（运营配置面）

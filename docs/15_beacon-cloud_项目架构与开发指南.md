# beacon-cloud 项目架构与开发指南



---

## 1. 文档目标

回答下面几个问题：

1. `beacon-cloud` 到底是做什么的。
2. 这个项目有哪些服务，每个服务负责什么。
3. 一条短信从请求进入到最终回执，代码是怎么流转的。
4. 本地把项目跑起来至少需要哪些中间件，启动顺序是什么。
5. 日常开发时哪些配置、缓存、MQ、鉴权约束必须先知道。
6. 遇到“发不出去”“查不到日志”“客户没收到回调”时，应该先排查哪里。

---

## 2. 项目概述

### 2.1 一句话简介

`beacon-cloud` 是一个基于 Spring Cloud 的企业短信平台微服务系统，覆盖短信提交、策略过滤、网关下发、运营商状态回执、日志检索、客户回调、运营后台和巡检告警等完整链路。

### 2.2 业务闭环

系统当前实现的业务闭环可以概括为：

1. 客户系统或运营后台提交短信发送请求。
2. 平台对请求做身份、IP、签名、模板、余额等校验。
3. 平台根据客户绑定的策略和通道，把短信路由到目标运营商网关。
4. 网关以 CMPP 协议发送短信并接收运营商应答。
5. 平台把发送日志写入 Elasticsearch，供后台检索。
6. 平台接收最终状态报告，更新短信状态并在需要时回调客户系统。
7. 平台后台提供客户、通道、签名、模板、余额、缓存重建、短信检索等运维能力。

### 2.3 这个项目的两个面

从系统职责看，当前项目可以理解为两个部分：

1. 业务执行面  
   负责真正的短信发送链路，包括 `beacon-api`、`beacon-strategy`、`beacon-smsgateway`、`beacon-search`、`beacon-push`。

2. 控制与支撑面  
   负责配置管理、缓存服务、后台管理和巡检告警，包括 `beacon-webmaster`、`beacon-cache`、`beacon-monitor`、`beacon-common`。

这两个面之间的关系非常重要：

1. 执行面追求的是请求异步化、链路解耦和最终一致。
2. 控制面追求的是主数据维护、缓存镜像刷新和排障可观测性。

---

## 3. 宏观架构说明

### 3.1 总体流向

当前项目没有单独的 Spring Cloud Gateway 服务。

系统整体流向如下：

```text
客户系统 / 运营后台
        |
        v
   beacon-api  <------ beacon-webmaster（内部发送入口）
        |
        | RabbitMQ: sms_pre_send_topic
        v
  beacon-strategy
        |
        | RabbitMQ: sms_gateway_topic_{channelId}
        v
 beacon-smsgateway <----> CMPP Server / 运营商网关
        |
        |-- sms_write_log_topic ------------> beacon-search
        |-- sms_push_report_topic ---------> beacon-push
        |-- sms_gateway_normal_exchange ---> TTL + DLQ ---> beacon-search

beacon-cache 为多服务提供统一 Redis 访问
beacon-monitor 负责巡检 MQ 堆积与客户余额
beacon-webmaster 负责配置、缓存同步、后台发送、检索聚合
```

### 3.2 分层理解

按职责可以将系统拆成 5 层：

1. 接入层  
   `beacon-api`、`beacon-webmaster`

2. 策略层  
   `beacon-strategy`

3. 协议网关层  
   `beacon-smsgateway`

4. 日志与回调层  
   `beacon-search`、`beacon-push`

5. 平台支撑层  
   `beacon-cache`、`beacon-monitor`、`beacon-common`

### 3.3 架构特征

这个项目有几个非常鲜明的工程特征：

1. 主链路强依赖 RabbitMQ  
   API、策略、网关、日志、回调之间大多不是同步调用，而是通过 MQ 解耦。

2. Redis 被服务化  
   多个业务服务并不直接操作 Redis，而是通过 `beacon-cache` 访问。

3. MySQL 是主数据真源  
   客户、通道、余额、签名、模板等主数据主要由 `beacon-webmaster` 管理。

4. Elasticsearch 是读侧检索索引  
   `beacon-search` 主要承接短信日志检索与状态统计，不是主事实库。

5. 后台服务体量明显偏大  
   `beacon-webmaster` 除了后台页面和权限，还承担余额扣减、缓存同步、缓存重建等控制平面职责。

---

## 4. 微服务职责清单

说明：

1. 当前仓库大多数服务端口不在本地配置文件中显式写死，实际端口通常由 Nacos 配置中心下发。
2. 因此下表的端口列统一写“以 Nacos 配置为准”。

| 服务名 | 端口 | 核心职责 | 关键入口 / 关键类 |
| --- | --- | --- | --- |
| `beacon-api` | 以 Nacos 配置为准 | 短信接入层，负责参数校验、客户校验、构建 `StandardSubmit`、投递预发送消息 | `SmsController` |
| `beacon-strategy` | 以 Nacos 配置为准 | 策略引擎，执行号段补齐、黑名单、敏感词、限流、余额、路由等策略 | `PreSendListener`、`StrategyFilterContext` |
| `beacon-smsgateway` | 以 Nacos 配置为准 | 短信网关服务，消费通道消息，组装 CMPP 报文，下发运营商并接收回执 | `SmsGatewayListener`、`NettyClient`、`CMPPHandler` |
| `beacon-search` | 以 Nacos 配置为准 | Elasticsearch 日志服务，写入发送日志、更新最终状态、提供后台查询和统计 | `SmsWriteLogListener`、`SmsUpdateLogListener`、`ElasticsearchServiceImpl` |
| `beacon-push` | 以 Nacos 配置为准 | 客户回调服务，消费状态报告并发起 HTTP 回调，支持延迟重试 | `PushReportListener` |
| `beacon-cache` | 以 Nacos 配置为准 | Redis 统一访问服务，封装 Hash / Set / String / ZSet 能力，并附带签名鉴权 | `CacheController`、`CacheFacade`、`CacheAuthInterceptor` |
| `beacon-monitor` | 以 Nacos 配置为准 | 监控告警服务，基于 XXL-Job 巡检 RabbitMQ 队列积压与客户余额 | `MonitorQueueMessageCountTask`、`MonitorClientBalanceTask` |
| `beacon-webmaster` | 以 Nacos 配置为准 | 运营后台与控制平面服务，负责客户、通道、签名、模板、余额、缓存同步、缓存重建、后台发送 | `WebMasterStarterApp`、`InternalBalanceController`、`CacheSyncServiceImpl` |
| `beacon-common` | 非独立服务 | 公共模块，提供常量、模型、缓存域契约、工具类、异常、签名安全能力 | `StandardSubmit`、`StandardReport`、`RabbitMQConstants` |

---

## 5. 核心技术栈说明

### 5.1 基础框架

- JDK：`1.8`
- Spring Boot：`2.3.12.RELEASE`
- Spring Cloud：`Hoxton.SR12`
- Spring Cloud Alibaba：`2.2.6.RELEASE`
- Maven：建议 `3.6+`

### 5.2 核心中间件

| 组件 | 当前版本 / 建议版本 | 用途 |
| --- | --- | --- |
| Nacos | `2.1.x` | 注册中心 + 配置中心 |
| MySQL | `5.7.x` | 主数据存储 |
| Redis | `5.x` | 缓存与状态镜像 |
| RabbitMQ | `3.8.x` | 主链路异步解耦 |
| Elasticsearch | `7.6.2` | 短信日志检索 |
| Kibana | `7.6.2` | ES 可视化与排障 |
| XXL-Job | `2.3.x / 2.4.x` | 巡检与告警调度 |
| Hippo4j | `1.5.x` | 动态线程池 |

### 5.3 服务治理与开发框架

- OpenFeign：服务间同步调用
- Spring AMQP：RabbitMQ 集成
- MyBatis：数据访问层
- Druid：数据源
- Shiro：后台权限与登录
- Netty：CMPP 协议网关
- IKAnalyzer / Hutool DFA：敏感词处理

### 5.4 当前技术选型的注意点

1. 技术版本整体偏旧，但仍可运行。
2. `beacon-webmaster` 使用的是传统后台技术栈，不是前后端完全分离架构。
3. `beacon-smsgateway` 不是 HTTP 网关，而是运营商协议网关。
4. `beacon-cache` 是本项目的重要特色，它让 Redis 成为统一服务能力，而不是每个服务各连各的 Redis。

---

## 6. 仓库结构说明

根目录当前主要结构如下：

```text
beacon-cloud/
├─ beacon-api/
├─ beacon-common/
├─ beacon-cache/
├─ beacon-strategy/
├─ beacon-smsgateway/
├─ beacon-search/
├─ beacon-push/
├─ beacon-monitor/
├─ beacon-webmaster/
├─ docs/
├─ xxl-job/
├─ README.md
└─ pom.xml
```

### 6.1 根 POM 说明

根项目是 Maven 聚合工程，统一管理：

1. Spring Boot 版本
2. Spring Cloud 版本
3. Spring Cloud Alibaba 版本
4. 子模块编译版本

### 6.2 公共模块说明

`beacon-common` 中最值得优先了解的是：

1. `com.cz.common.model.StandardSubmit`
2. `com.cz.common.model.StandardReport`
3. `com.cz.common.constant.RabbitMQConstants`
4. `com.cz.common.constant.CacheKeyConstants`
5. `com.cz.common.cache.meta.CacheDomainRegistry`
6. `com.cz.common.enums.ExceptionEnums`

这几个类基本决定了整个系统的“公共语言”。

---

## 7. 核心数据对象

### 7.1 `StandardSubmit`

这是短信发送主链路中的统一提交对象，主要贯穿：

`beacon-api -> beacon-strategy -> beacon-smsgateway -> beacon-search`

核心字段包括：

| 字段 | 含义 |
| --- | --- |
| `sequenceId` | 平台内部短信唯一流水号 |
| `clientId` | 客户 ID |
| `apiKey` | 客户 API Key |
| `uid` | 客户业务侧请求 ID |
| `mobile` | 目标手机号 |
| `sign` | 短信签名 |
| `text` | 短信内容 |
| `sendTime` | 平台发送时间 |
| `fee` | 本次短信费用 |
| `operatorId` | 运营商标识 |
| `area` / `areaCode` | 号段归属地信息 |
| `channelId` | 路由后的通道 ID |
| `srcNumber` | 通道源号码 |
| `reportState` | 发送状态 |
| `errorMsg` | 错误信息 |
| `realIp` | 请求来源 IP |
| `state` | 短信类型 |

### 7.2 `StandardReport`

这是状态报告与回调共用的统一对象，主要贯穿：

`beacon-smsgateway -> beacon-search -> beacon-push`

核心字段包括：

| 字段 | 含义 |
| --- | --- |
| `sequenceId` | 平台内部短信流水号 |
| `apiKey` | 客户 API Key |
| `clientId` | 客户 ID |
| `uid` | 客户业务侧请求 ID |
| `mobile` | 目标手机号 |
| `sendTime` | 发送时间 |
| `reportState` | 最终状态 |
| `errorMsg` | 失败原因 |
| `isCallback` | 是否开启回调 |
| `callbackUrl` | 客户回调地址 |
| `resendCount` | 回调重试次数 |
| `reUpdate` | 是否为二次状态更新投递 |

### 7.3 两者关系

理解这两个对象非常关键：

1. `StandardSubmit` 表示“待发送短信的执行载体”。
2. `StandardReport` 表示“最终状态或回调载体”。
3. 短信在网关提交成功后，会由 `StandardSubmit` 派生出 `StandardReport`。

---

## 8. 配置中心与配置文件管理

### 8.1 当前配置方式

项目采用两类配置：

1. 本地基础配置  
   多个服务在 `src/main/resources/bootstrap.yml` 中定义服务名、激活环境、Nacos 地址等。

2. Nacos 动态配置  
   实际的数据库、RabbitMQ、Redis、ES、服务端口、业务开关等通常由 Nacos 下发。

`beacon-webmaster` 是一个例外，它当前仍保留较多本地 `application.yml` 配置。

### 8.2 当前仓库可见的本地配置文件

| 模块 | 配置文件 |
| --- | --- |
| `beacon-api` | `bootstrap.yml` |
| `beacon-strategy` | `bootstrap.yml` |
| `beacon-smsgateway` | `bootstrap.yml` |
| `beacon-search` | `bootstrap.yml` |
| `beacon-push` | `bootstrap.yml` |
| `beacon-cache` | `bootstrap.yml` |
| `beacon-monitor` | `bootstrap.yml` |
| `beacon-webmaster` | `application.yml` |

### 8.3 Nacos DataId 约定

典型 DataId 如下：

- `beacon-api-dev.yml`
- `beacon-strategy-dev.yml`
- `beacon-smsgateway-dev.yml`
- `beacon-search-dev.yml`
- `beacon-push-dev.yml`
- `beacon-monitor-dev.yml`
- `beacon-cache-dev.yml`

### 8.4 配置管理规范

新人开发时要遵守下面几条：

1. 服务名、环境名、Nacos 地址这类基础配置可放本地。
2. 数据库密码、SMTP 授权码、Token、密钥、ES 账号密码、RabbitMQ 账号密码这类敏感配置必须外置。
3. 本仓库中已有部分示例明文配置，仅用于理解当前运行方式，不建议继续沿用。
4. 新增配置优先进入 Nacos，不要把环境差异写死在代码里。

### 8.5 当前仓库里值得注意的配置现实

当前仓库可以直接看到：

1. Nacos 地址默认写成 `192.168.88.128:8848`
2. `beacon-webmaster` 内有 MySQL 示例连接和密码
3. `beacon-monitor` 内有 SMTP 示例账号和授权码
4. 多个内部 Token 默认是空字符串

因此，正式接手后应优先做一次配置清理与外置化检查。

---

## 9. 本地环境搭建指南

### 9.1 前置环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | `1.8` |
| Maven | `3.6+` |
| IDE | IntelliJ IDEA 或同类 Java IDE |
| 操作系统 | Windows / Linux / macOS 均可 |
| 可选 | Docker / Docker Compose |

### 9.2 本地必须启动的中间件

至少需要：

1. Nacos
2. MySQL
3. Redis
4. RabbitMQ
5. Elasticsearch

建议同时准备：

1. Kibana
2. XXL-Job Admin
3. Hippo4j Server
4. 本地模拟 CMPP Server

### 9.3 关键中间件说明

#### Nacos

作用：

1. 服务注册发现
2. 配置中心

本地最小要求：

1. 能访问 `8848`
2. 已导入各服务所需 DataId

#### MySQL

作用：

1. `beacon-webmaster` 的主数据真源
2. 余额、客户、通道、签名、模板等信息存储

建议：

1. 本地创建与开发环境一致的库名，例如 `duanxin_pingtai`
2. 导入初始化表结构和测试数据

#### Redis

作用：

1. 客户业务配置镜像
2. 客户余额镜像
3. 通道配置镜像
4. 号段、黑名单、敏感词、限流等缓存
5. CMPP 中间状态暂存

#### RabbitMQ

作用：

1. 主发送链路解耦
2. 日志异步落库
3. 状态回调异步执行
4. 延迟状态更新
5. 客户回调失败重试

本地必须注意：

1. 启用 `rabbitmq_delayed_message_exchange`
2. 建议启用 `rabbitmq_management`

#### Elasticsearch

作用：

1. 短信日志检索
2. 状态统计
3. 后台图表查询

### 9.4 推荐中间件启动顺序

1. `Nacos`
2. `MySQL`
3. `Redis`
4. `RabbitMQ`
5. `Elasticsearch`
6. `Kibana`
7. `xxl-job-admin`
8. `hippo4j-server`
9. `CMPP mock server`

### 9.5 `beacon-smsgateway` 的额外前置条件

这是本地联调最容易忽略的一点：

`beacon-smsgateway` 启动前必须先准备一个可连接的 CMPP Server。

当前代码中的默认连接参数是：

- `host=127.0.0.1`
- `port=7890`
- `serviceId=cz`
- `pwd=123`

如果本地没有模拟 CMPP Server：

1. 网关会不断重连
2. 你能看到服务启动，但无法真正跑通发送链路

### 9.6 微服务本地启动顺序

推荐顺序如下：

1. `beacon-cache`
2. `beacon-strategy`
3. `beacon-search`
4. `beacon-push`
5. 启动并确认 `CMPP mock server`
6. `beacon-smsgateway`
7. `beacon-api`
8. `beacon-webmaster`
9. `beacon-monitor`

这个顺序背后的原因是：

1. `beacon-strategy` 会声明主发送相关队列
2. `beacon-search` 会声明 normal/dead 交换机与队列
3. `beacon-push` 会声明延迟交换机与队列
4. `beacon-smsgateway` 依赖 CMPP Server 可用

### 9.7 启动类清单

- `com.cz.cache.CacheStarterApp`
- `com.cz.strategy.StrategyStarterApp`
- `com.cz.search.SearchStarterApp`
- `com.cz.push.PushStarterApp`
- `com.cz.smsgateway.SmsGatewayStarterApp`
- `com.cz.api.ApiStarterApp`
- `com.cz.webmaster.WebMasterStarterApp`
- `com.cz.monitor.MonitorStarterApp`

### 9.8 构建命令

打包：

```bash
mvn clean package -DskipTests
```

跑测试：

```bash
mvn test
```

说明：

1. 当前仓库可以 `package` 成功。
2. 当前测试并非全部稳定，特别是 `beacon-search` 相关测试需要额外关注。

---

## 10. 核心鉴权与接口规范

### 10.1 外部接口鉴权

当前 `beacon-api` 的外部发送入口主要依赖：

1. `apiKey`
2. 客户缓存配置
3. IP 白名单
4. 签名 / 模板 / 余额等业务规则

它不是统一 JWT 网关模式，而是短信平台自己的业务校验模式。

### 10.2 内部接口鉴权

当前内部接口主要包括：

1. `beacon-api` 的 `/sms/internal/single_send`
2. `beacon-webmaster` 的 `/internal/balance/debit`

当前实现方式：

1. 请求头传 `X-Internal-Token`
2. 服务端读取 `internal.sms.token` 或 `internal.balance.token`
3. 两边一致则放行

注意：

1. 当前默认配置中这些 token 可能为空
2. token 为空时，相当于弱化了校验
3. 上线前应强制补齐，不要依赖默认空值

### 10.3 后台鉴权

`beacon-webmaster` 使用：

1. Shiro
2. Session
3. Captcha

不是 JWT 模式。

因此：

1. 后台接口默认是会话态
2. 排查“未登录”“无权限”问题时，要先看 Shiro 过滤链和登录态
3. 新增后台接口时要明确是否允许匿名访问

### 10.4 服务间缓存访问鉴权

`beacon-cache` 对外不是裸开放的 Redis API，而是带签名鉴权的 HTTP 服务。

调用方需要携带：

- `X-Cache-Caller`
- `X-Cache-Timestamp`
- `X-Cache-Sign`

签名内容为：

```text
caller + "\n" + timestamp + "\n" + method + "\n" + normalizedPath
```

签名算法：

- `HmacSHA256`

### 10.5 统一返回体

系统里常见两种返回体：

#### 通用返回体 `ResultVO<T>`

示例：

```json
{
  "code": 0,
  "msg": "",
  "data": {}
}
```

约定：

1. `code = 0` 表示成功
2. 非 0 表示失败
3. `msg` 是错误信息或提示文案
4. `data` 为业务数据

#### 短信发送返回体

发送接口通常返回：

```json
{
  "code": 0,
  "msg": "受理成功",
  "uid": "biz-xxx",
  "sid": "平台流水号"
}
```

### 10.6 开发时的接口约束

1. 外部接口新增字段时，要优先考虑兼容旧调用方。
2. 内部接口新增鉴权逻辑时，要同步更新 Feign 调用方。
3. 缓存服务调用要尽量走封装好的 Feign 配置，不要手写 Header。
4. 跨服务传输对象尽量复用 `beacon-common`，避免出现多个版本的“相似对象”。

---

## 11. 核心消息链路与 RabbitMQ 拓扑

### 11.1 为什么 RabbitMQ 是核心组件

在这个项目里，RabbitMQ 不是辅助工具，而是发送主链路的一部分。

它主要承担：

1. API 受理异步化
2. 策略与网关解耦
3. 日志异步写 ES
4. 状态回调异步执行
5. 延迟更新状态
6. 客户回调失败重试

### 11.2 关键队列 / 交换机清单

| 名称 | 类型 | 生产者 | 消费者 | 作用 |
| --- | --- | --- | --- | --- |
| `sms_pre_send_topic` | 队列 | `beacon-api` | `beacon-strategy` | 预发送入口 |
| `sms_gateway_topic_{channelId}` | 动态队列 | `beacon-strategy` | `beacon-smsgateway` | 按通道路由后的发送队列 |
| `sms_write_log_topic` | 队列 | `beacon-strategy` / `beacon-smsgateway` | `beacon-search` | 写发送日志 |
| `sms_push_report_topic` | 队列 | `beacon-strategy` / `beacon-smsgateway` | `beacon-push` | 客户状态回调 |
| `mobile_area_operator_topic` | 队列 | `beacon-strategy` | 当前仓库未见消费者 | 号段信息异步同步 |
| `sms_gateway_normal_exchange` | `fanout` | `beacon-smsgateway` | `sms_gateway_normal_queue` | 延迟状态更新入口 |
| `sms_gateway_normal_queue` | 队列 | normal exchange | 无直接业务消费方 | TTL 10 秒后进入死信 |
| `sms_gateway_dead_exchange` | `fanout` | normal queue 死信转入 | `sms_gateway_dead_queue` | 延迟状态更新死信交换机 |
| `sms_gateway_dead_queue` | 队列 | dead exchange | `beacon-search` | 更新最终短信状态 |
| `push_delayed_exchange` | `x-delayed-message` | `beacon-push` | `push_delayed_queue` | 回调失败后的延迟重试 |
| `push_delayed_queue` | 队列 | delayed exchange | `beacon-push` | 回调重试消费队列 |

### 11.3 重点理解

这个项目里有两个常被新人误解的点：

1. `xxx_topic` 在很多地方其实是“队列名”，不是 TopicExchange。
2. `sms_gateway_dead_queue` 当前不是“消费失败垃圾桶”，而是“延迟 10 秒后再更新 ES 状态”的通道。

### 11.4 当前实现的工程注意事项

1. API 和 strategy 虽然配置了 confirm / return 回调，但目前主要是日志级处理。
2. strategy 的消费失败处理边界需要特别小心。
3. smsgateway 当前存在“保存状态后即 ack”的实现特征。
4. push 的延迟交换机依赖 RabbitMQ 插件。

如果你要修改 MQ 链路，建议先读：

- `docs/13_rabbitmq_消息链路与死信队列说明.md`
- `docs/14_rabbitmq_实现问题与改进方案.md`

---

## 12. 缓存架构与 `beacon-cache`

### 12.1 为什么单独做一个缓存服务

当前项目没有让所有服务直接连接 Redis，而是通过 `beacon-cache` 暴露统一能力。

这样做的好处：

1. 可以统一做命名空间隔离
2. 可以统一做调用鉴权
3. 可以集中做 key 扫描和批量删除
4. 可以让业务服务通过逻辑 key 访问 Redis，而不是每个服务自己拼物理 key

### 12.2 逻辑 key 与物理 key

例如业务代码里使用：

- `client_business:{apikey}`
- `client_balance:{clientId}`
- `client_channel:{clientId}`
- `channel:{id}`

而实际 Redis 中会带统一命名空间前缀，例如：

```text
beacon:dev:beacon-cloud:cz:client_business:demo-api-key
```

### 12.3 当前缓存域契约

系统当前已经在 `beacon-common` 中抽出了缓存域契约：

- `client_business`
- `client_sign`
- `client_template`
- `client_channel`
- `channel`
- `client_balance`
- `transfer`
- `black`
- `dirty_word`

这套契约明确了：

1. 逻辑 key 模板
2. Redis 数据结构类型
3. 真源是 MySQL 还是别的
4. 写策略、删策略、重建策略
5. 归属服务是谁

### 12.4 日常开发约束

1. 修改主数据时，不能只改 MySQL 而忽略缓存刷新。
2. 修改缓存结构前，先确认 `CacheDomainRegistry` 是否已经定义了该域。
3. 读缓存时优先走各服务自己的 `CacheFacade` 封装，而不是直接操作 Feign 返回的原始 `Map`。
4. 新增缓存域时，要同步考虑：
   - 逻辑 key
   - 物理 key
   - 命名空间
   - 鉴权
   - 删除策略
   - 重建策略

---

## 13. `beacon-webmaster`：控制平面服务说明

### 13.1 为什么这个模块很重要

虽然它名字像“后台管理服务”，但它实际上不只是后台页面。

它还承担：

1. 主数据管理
2. 余额扣减内部接口
3. 缓存同步
4. 缓存重建
5. 后台批量发送
6. 搜索代理和权限裁剪

可以把它理解成：

> 当前系统的控制平面单体

### 13.2 它管理的典型数据

1. 客户信息
2. 客户业务配置
3. 客户余额
4. 客户签名
5. 客户模板
6. 客户通道绑定
7. 通道配置
8. 黑名单
9. 敏感词
10. 携号转网数据

### 13.3 当前较新的工程能力

当前仓库里比较值得关注的是：

1. 内部余额扣减接口  
   `InternalBalanceController`

2. 余额原子更新 + 提交后缓存双刷新  
   `BalanceCommandServiceImpl`

3. 缓存同步路由与重建引擎  
   `CacheSyncServiceImpl`

这说明后台模块最近已经在向“控制平面服务化”方向演进。

### 13.4 新人建议

不要一开始就通读 `beacon-webmaster` 全部代码。

建议优先看：

1. `InternalBalanceController`
2. `BalanceCommandServiceImpl`
3. `CacheSyncServiceImpl`
4. `SmsManageServiceImpl`
5. `SearchController`

先抓住它对主链路有影响的部分，再回头看后台 CRUD。

---

## 14. 核心业务主线导读

### 14.1 主线：外部短信发送

这是最值得新人先看懂的主链路。

#### 第一步：请求进入 `beacon-api`

入口接口：

- `POST /sms/single_send`

主要逻辑：

1. 参数校验
2. 从请求头中提取真实 IP
3. 组装 `StandardSubmit`
4. 执行基础校验链
5. 生成 `sequenceId`
6. 写入发送时间
7. 投递到 `sms_pre_send_topic`

建议重点看：

- `SmsController`
- `CheckFilterContext`
- `ApiKeyCheckFilter`
- `IPCheckFilter`
- `SignCheckFilter`
- `TemplateCheckFilter`
- `FeeCheckFilter`

#### 第二步：消息进入 `beacon-strategy`

入口监听器：

- `PreSendListener`

主要逻辑：

1. 从 MQ 消费 `StandardSubmit`
2. 根据客户配置读取策略链
3. 依次执行各个策略过滤器

建议重点看：

- `StrategyFilterContext`
- `PhaseStrategyFilter`
- `BlackGlobalStrategyFilter`
- `BlackClientStrategyFilter`
- `DirtyWord...StrategyFilter`
- `LimitOneHourStrategyFilter`
- `FeeStrategyFilter`
- `RouteStrategyFilter`

#### 第三步：路由到网关队列

路由逻辑主要发生在：

- `RouteStrategyFilter`

主要逻辑：

1. 读取客户通道绑定
2. 读取通道详情
3. 按权重与可用性排序
4. 根据运营商匹配合适通道
5. 动态声明 `sms_gateway_topic_{channelId}`
6. 投递路由后的短信

#### 第四步：`beacon-smsgateway` 下发 CMPP

入口监听器：

- `SmsGatewayListener`

主要逻辑：

1. 监听 `gateway.sendtopic`
2. 把 `StandardSubmit` 转成 `CmppSubmit`
3. 暂存中间状态
4. 通过 Netty 客户端发送给 CMPP Server

相关协议响应处理：

- `CMPPHandler`
- `SubmitRepoRunnable`
- `DeliverRunnable`

#### 第五步：提交日志写入 ES

在运营商提交应答后：

1. `SubmitRepoRunnable` 根据 SubmitResp 更新发送结果
2. 生成或补全日志对象
3. 投递到 `sms_write_log_topic`
4. `beacon-search` 消费后写入 Elasticsearch

#### 第六步：最终状态报告处理

在运营商最终状态报告到达后：

1. `DeliverRunnable` 根据状态报告构建 `StandardReport`
2. 如果客户开启回调，投递到 `sms_push_report_topic`
3. 同时投递到 `sms_gateway_normal_exchange`
4. 消息经过 TTL 和死信队列后，被 `beacon-search` 消费
5. `beacon-search` 更新 ES 中文档的最终状态

#### 第七步：客户回调

`beacon-push` 负责：

1. 消费 `sms_push_report_topic`
2. 调用客户 HTTP 回调地址
3. 如果失败，走延迟交换机重试

### 14.2 后台批量发送链路

后台批量发送并没有直接绕过主链路。

它的流程是：

1. 运营后台页面提交号码和短信内容
2. `beacon-webmaster` 校验客户权限
3. `SmsManageServiceImpl` 把号码拆分为多条内部发送请求
4. 逐条调用 `beacon-api /sms/internal/single_send`
5. 后续链路与外部发送完全一致

这意味着：

1. 后台发送不是单独的一套业务实现
2. 它只是“主链路的内部调用入口”

### 14.3 搜索与统计链路

后台查询短信时的流程是：

1. 前端请求 `beacon-webmaster`
2. `SearchController` 做权限裁剪
3. `SearchClient` 调用 `beacon-search`
4. `beacon-search` 查询 Elasticsearch
5. 返回列表和统计结果

---

## 15. 推荐读码顺序

### 15.1 第一阶段：先建立全局认知

先看：

1. `README.md`
2. `docs/01_level1_系统全局概览.md`
3. 本文

目标：

1. 知道有哪些模块
2. 知道主链路怎么走
3. 知道项目依赖哪些中间件

### 15.2 第二阶段：先看公共语言

先看 `beacon-common`：

1. `StandardSubmit`
2. `StandardReport`
3. `RabbitMQConstants`
4. `CacheKeyConstants`
5. `CacheDomainRegistry`
6. `ExceptionEnums`

目标：

1. 建立全局术语
2. 知道跨服务共享的数据结构

### 15.3 第三阶段：顺着主链路看

推荐顺序：

1. `beacon-api`
2. `beacon-strategy`
3. `beacon-smsgateway`
4. `beacon-search`
5. `beacon-push`

目标：

1. 看懂短信从入口到回执的完整闭环

### 15.4 第四阶段：再看控制平面

然后看：

1. `beacon-cache`
2. `beacon-webmaster`
3. `beacon-monitor`

目标：

1. 理解主数据来源
2. 理解缓存镜像与重建
3. 理解后台和监控如何影响主链路

### 15.5 第五阶段：专题阅读

最后建议读：

1. `docs/13_rabbitmq_消息链路与死信队列说明.md`
2. `docs/14_rabbitmq_实现问题与改进方案.md`
3. `docs/13_beacon-search_es_analysis.md`

---

## 16. 常见排障路径

### 16.1 接口请求成功，但短信没发出去

优先检查：

1. `beacon-api` 是否成功投递 `sms_pre_send_topic`
2. `sms_pre_send_topic` 是否堆积
3. `beacon-strategy` 是否正常消费
4. 策略是否命中黑名单、敏感词、余额不足、无可用通道

### 16.2 策略通过了，但网关没有发送

优先检查：

1. `sms_gateway_topic_{channelId}` 是否存在
2. 对应队列是否堆积
3. `gateway.sendtopic` 是否与目标通道队列一致
4. CMPP Server 是否可连接
5. `beacon-smsgateway` 是否正在重连

### 16.3 短信发了，但后台搜索不到

优先检查：

1. `sms_write_log_topic` 是否已被消费
2. `beacon-search` 是否正常写 ES
3. ES 账号密码和连接配置是否正确
4. 索引名是否落到了预期年份索引

### 16.4 搜索能看到发送记录，但状态不更新

优先检查：

1. `sms_gateway_normal_exchange`
2. `sms_gateway_normal_queue`
3. `sms_gateway_dead_queue`
4. `beacon-search` 是否消费了死信队列
5. ES 文档是否存在但未更新，还是压根未写入

### 16.5 客户没收到回调

优先检查：

1. `sms_push_report_topic` 是否有消息
2. `beacon-push` 是否正常消费
3. 客户 `callbackUrl` 是否配置正确
4. 客户接口是否返回 `SUCCESS`
5. `push_delayed_queue` 是否在不断重试

### 16.6 监控告警说队列堆积

优先检查：

1. `sms_pre_send_topic`
2. `sms_gateway_topic_{channelId}`
3. `sms_write_log_topic`
4. `sms_push_report_topic`
5. `sms_gateway_dead_queue`

---

## 17. 当前仓库接手时的现实注意事项

这部分不是“理想规范”，而是当前代码现实，接手时要心里有数。

### 17.1 文档与仓库并非完全一致

当前仓库中的部分旧文档或引用已经漂移，阅读时要以代码和本文为准。

### 17.2 内部接口安全边界需要重点复核

当前内部 token 在示例配置里可能为空，接手后第一时间应检查：

1. `beacon-api` 内部发送 token
2. `beacon-webmaster` 内部余额扣减 token
3. Shiro 对内部接口的放行规则

### 17.3 `beacon-smsgateway` 是最依赖外部环境的模块

它对本地联调最不友好，原因包括：

1. 依赖 CMPP Server
2. 有 Netty 长连接与重连逻辑
3. 对 MQ 与缓存中间状态依赖较深

### 17.4 `beacon-webmaster` 不是普通 CRUD 后台

它已经是控制平面核心，不要只按“后台管理模块”的心态看它。

### 17.5 RabbitMQ 和 ES 是最需要保持语义一致的两块

如果你后续要重构，优先保证：

1. 消息是否丢失
2. 消费失败如何处理
3. ES 索引落点是否正确
4. 状态更新是否可重试

---

## 18. 新人入手建议

如果你只有半天时间接手这个项目，建议按下面顺序推进：

### 第一步：先把项目跑起来

1. 准备 Nacos、MySQL、Redis、RabbitMQ、ES
2. 确认 Nacos 配置完整
3. 先启动 `beacon-cache`、`beacon-strategy`、`beacon-search`、`beacon-push`
4. 准备本地 CMPP mock server
5. 启动 `beacon-smsgateway`
6. 启动 `beacon-api`

### 第二步：跑通最小发送链路

目标不是先看后台，而是先确认：

1. `beacon-api` 能收请求
2. `beacon-strategy` 能消费
3. `beacon-smsgateway` 能下发
4. `beacon-search` 能写日志

### 第三步：再接后台与监控

1. 启动 `beacon-webmaster`
2. 确认客户、余额、通道等主数据可用
3. 看 `beacon-monitor` 是否能巡检

### 第四步：最后再做代码改动

在你还没搞清楚 MQ 和缓存镜像之前，不建议贸然修改：

1. 余额逻辑
2. 路由逻辑
3. 死信队列逻辑
4. 搜索索引逻辑

---

## 19. 推荐附加阅读文件

建议同时阅读以下文件：

- `README.md`
- `docs/01_level1_系统全局概览.md`
- `docs/13_rabbitmq_消息链路与死信队列说明.md`
- `docs/14_rabbitmq_实现问题与改进方案.md`
- `docs/13_beacon-search_es_analysis.md`

---

## 20. 最简总结

如果你只记住 6 句话，可以记下面这些：

1. `beacon-cloud` 的核心是“短信发送执行链”，不是单纯的后台系统。
2. 主链路是 `beacon-api -> beacon-strategy -> beacon-smsgateway -> beacon-search / beacon-push`。
3. `beacon-webmaster` 是控制平面核心，负责主数据、余额和缓存同步。
4. RabbitMQ 是主链路核心组件，很多问题本质上都是 MQ 语义问题。
5. Elasticsearch 是读侧日志索引，不是主事实库。
6. 本地联调最容易卡在 Nacos 配置不全和 CMPP mock server 未启动。


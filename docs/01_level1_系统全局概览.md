# 系统总览与主链路总文档

文档类型：系统总览  
适用对象：新同学 / 答辩 / 架构理解  
验证基线：代码静态核对  
关联模块：全局  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `01_level1_系统全局概览.md`
2. `23_level2_模块与架构构成.md`
3. `24_level3_核心业务链路分析.md`
4. `sms-send-workflow-analysis.md`

本文面向第一次接触 `beacon-cloud` 的读者，目标是用最少前置知识建立系统整体认知。  
内容仅描述当前仓库中已经落地的实现。

## 1. 项目定位

`beacon-cloud` 是一个短信平台微服务项目。  
从代码结构和运行链路看，它覆盖了短信业务的完整闭环：

1. 接入短信发送请求。
2. 执行发送前校验与策略处理。
3. 对接运营商协议网关下发短信。
4. 记录发送日志并更新回执状态。
5. 在需要时把状态回调给客户系统。
6. 提供后台查询与运营管理能力。

## 2. 业务参与方

按当前实现，可抽象为 4 类参与方：

1. 客户系统：通过 `beacon-api` 提交短信发送请求。
2. 平台业务服务：`beacon-api`、`beacon-strategy`、`beacon-smsgateway`、`beacon-search`、`beacon-push`。
3. 运营后台：`beacon-webmaster`，用于客户、通道、策略、短信查询等管理操作。
4. 外部基础设施：Nacos、MySQL、Redis、RabbitMQ、Elasticsearch、XXL-Job、CMPP 服务端。

## 3. 顶层业务闭环

当前主链路可以概括为：

1. 短信请求进入 API（外部接口或后台内部接口）。
2. 请求被封装为 `StandardSubmit`，进入 RabbitMQ 预发送队列。
3. 策略模块按客户配置执行策略链，并路由到通道队列。
4. 网关模块把消息转换成 CMPP 报文并发送给运营商侧。
5. 网关收到提交响应/状态报告后：
   - 写入日志队列供搜索模块入库。
   - 写入状态更新交换机供搜索模块更新状态。
   - 在客户开启回调时写入回调队列供推送模块发送回调。
6. 后台模块通过搜索服务查询短信明细与状态统计。

## 4. 顶层架构（当前实现）

```text
客户端/后台
   |
   v
beacon-api  <----- beacon-webmaster(内部发送入口)
   |
   |  sms_pre_send_topic
   v
beacon-strategy
   |
   |  sms_gateway_topic_{channelId}
   v
beacon-smsgateway <----> CMPP服务端
   |                     (运营商协议侧)
   |-- sms_write_log_topic ------------> beacon-search(写日志到ES)
   |-- sms_gateway_normal_exchange ---> (TTL) ---> sms_gateway_dead_queue ---> beacon-search(更新状态)
   |-- sms_push_report_topic ---------> beacon-push(客户回调)

beacon-cache 为 API/策略/网关/监控提供统一 Redis 访问
beacon-monitor 通过 XXL-Job 执行队列与余额巡检任务
```

## 5. 核心数据对象

当前链路中的两个核心对象：

1. `StandardSubmit`（`beacon-common`）
   - 表示一条待发送短信在平台内部的统一载体。
   - 典型字段：`sequenceId`、`clientId`、`mobile`、`text`、`state`、`fee`、`channelId`、`reportState`。
2. `StandardReport`（`beacon-common`）
   - 表示短信回执/状态更新/回调推送的统一载体。
   - 典型字段：`sequenceId`、`apikey`、`mobile`、`reportState`、`errorMsg`、`callbackUrl`、`resendCount`。

## 6. 当前模块清单

根 `pom.xml` 当前聚合 10 个子模块：

1. `beacon-api`
2. `beacon-common`
3. `beacon-cache`
4. `beacon-test`
5. `beacon-strategy`
6. `beacon-search`
7. `beacon-push`
8. `beacon-smsgateway`
9. `beacon-monitor`
10. `beacon-webmaster`

## 7. 当前基础技术底座（代码可见）

1. Spring Boot `2.3.12.RELEASE`（根 `pom.xml`）。
2. Spring Cloud `Hoxton.SR12`，Spring Cloud Alibaba `2.2.6.RELEASE`（根 `pom.xml`）。
3. Java 编译目标 `1.8`（根 `pom.xml`）。
4. 消息总线：RabbitMQ（各业务模块使用 `spring-boot-starter-amqp`）。
5. 缓存：Redis（`beacon-cache` 提供统一访问）。
6. 搜索存储：Elasticsearch（`beacon-search` 使用 `elasticsearch-rest-high-level-client`）。
7. 关系库：MySQL（`beacon-webmaster`、`beacon-test`）。
8. 调度：XXL-Job（`beacon-monitor`）。

---

## 8. 模块与架构构成摘要

从职责上看，当前系统可以拆成 5 层：

1. 接入层：`beacon-api`、`beacon-webmaster`
2. 策略层：`beacon-strategy`
3. 通道网关层：`beacon-smsgateway`
4. 日志与回调层：`beacon-search`、`beacon-push`
5. 平台支撑层：`beacon-cache`、`beacon-monitor`、`beacon-common`、`beacon-test`

关键模块边界可以简化理解为：

1. `beacon-api`
   - 统一受理短信请求
2. `beacon-strategy`
   - 执行客户策略链并路由到通道
3. `beacon-smsgateway`
   - 把平台对象转换成 CMPP 报文并处理运营商回执
4. `beacon-search`
   - 写日志、更新状态、提供检索统计
5. `beacon-push`
   - 对客户回调状态报告
6. `beacon-cache`
   - 提供统一 Redis 访问能力
7. `beacon-webmaster`
   - 提供管理后台与内部发送入口
8. `beacon-monitor`
   - 定时巡检和告警

---

## 9. 核心业务链路摘要

### 9.1 外部接口发送

1. `beacon-api /sms/single_send`
2. 构建 `StandardSubmit`
3. 投递 `sms_pre_send_topic`
4. `beacon-strategy` 执行策略链
5. 路由到 `sms_gateway_topic_{channelId}`
6. `beacon-smsgateway` 下发 CMPP
7. `beacon-search` 写入发送日志
8. `beacon-smsgateway` 收到状态报告后更新 `StandardReport`
9. `beacon-search` 更新最终状态
10. `beacon-push` 在需要时执行客户回调

### 9.2 后台批量发送

1. `beacon-webmaster` 接收批量手机号
2. 逐号码调用 `beacon-api /sms/internal/single_send`
3. 后续进入与外部接口相同的主链路

### 9.3 检索与统计

1. `beacon-webmaster` 调 `beacon-search`
2. `beacon-search` 从 ES 返回列表与状态聚合
3. `beacon-webmaster` 做权限裁剪和页面展示

---

## 10. 关键对象流转摘要

当前主链路里的两个关键对象是：

1. `StandardSubmit`
   - 贯穿 API -> 策略 -> 网关 -> 日志写入
2. `StandardReport`
   - 贯穿网关回执 -> 搜索状态更新 -> 客户回调

最关键的流转关系：

1. `StandardSubmit` 在网关提交成功后派生为 `StandardReport`
2. `StandardReport` 再分流到“状态更新链路”和“客户回调链路”
3. 这也是当前 `apiKey/apikey`、`fee`、回调字段一致性问题最集中的位置

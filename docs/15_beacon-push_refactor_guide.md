# beacon-push 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-push  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `14_beacon-push_module_analysis.md`
2. `15_beacon-push_refactor_guide.md`

## 1. 模块定位

`beacon-push` 是平台的“状态报告推送模块”，核心职责：

1. 监听 `SMS_PUSH_REPORT` 队列，向客户回调地址推送状态报告。
2. 推送失败后投递延迟消息，执行重试。
3. 最终控制重试次数，避免无限重试。

核心代码入口：

1. 启动类：`beacon-push/src/main/java/com/cz/push/PushStarterApp.java`
2. 监听器：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java`
3. MQ 配置：`beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java`
4. HTTP 客户端配置：`beacon-push/src/main/java/com/cz/push/config/RestTemplateConfig.java`

---

## 2. 当前实现概览

## 2.1 主流程

1. `consume(...)` 监听 `SMS_PUSH_REPORT`，检查 `callbackUrl`。
2. `pushReport(...)` 使用 `RestTemplate.postForObject` 推送回调。
3. 失败则 `isResend(...)` 按延迟数组重投到 `push_delayed_exchange`。
4. `delayedConsume(...)` 消费 `push_delayed_queue` 后重复同样逻辑。

## 2.2 当前重试策略

重试延迟写死为：

```java
private int[] delayTime = {0,15000,30000,60000,300000};
```

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:35`

最大重试次数硬编码为 5 次：

```java
if(report.getResendCount() >= 5){
    return;
}
```

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:128`

---

## 3. 需要重构的代码、原因与重构方案

以下按优先级列出。

## 3.1 P0：回调异常记录有限，观测与排障能力仍不足

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:113`

```java
try {
    String result = restTemplate.postForObject(...);
    flag = SUCCESS.equals(result);
} catch (RestClientException | IllegalStateException e) {
    log.warn("push callback failed, callbackUrl={}, sequenceId={}, resendCount={}, error={}",
            report.getCallbackUrl(), report.getSequenceId(), report.getResendCount(), e.getMessage());
}
```

### 原因

1. 日志只记录 message，没有统一错误分类，也没有完整上下文和指标。
2. 线上仍难快速区分 DNS、超时、连接拒绝、4xx/5xx 等失败类型。

### 如何重构

1. catch 中至少输出结构化日志（sid、callbackUrl、resendCount、异常类型）。
2. 按异常类型打标签统计（timeout/connect/http4xx/http5xx）。

### 目标代码（建议）

```java
} catch (RestClientException ex) {
    log.warn("push callback failed, sid={}, callbackUrl={}, resendCount={}",
            report.getSequenceId(), report.getCallbackUrl(), report.getResendCount(), ex);
}
```

---

## 3.2 P0：回调地址强制拼接 `http://`，协议兼容风险高

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:111`

```java
String result = restTemplate.postForObject("http://" + report.getCallbackUrl(), ...);
```

### 原因

1. 如果数据库里已存 `https://...` 或 `http://...`，会形成非法 URL。
2. 无法支持 HTTPS-only 回调目标。

### 如何重构

1. 入库时规范 callbackUrl（必须为完整 URL）。
2. 推送时直接使用 `callbackUrl`，不做协议强拼接。
3. 增加 URL 格式校验与白名单策略。

---

## 3.3 P0：`RestTemplate` 无超时配置，可能长期阻塞线程

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/config/RestTemplateConfig.java:16`

```java
return new RestTemplate();
```

### 原因

1. 默认超时策略不可控，网络问题时可能长时间占用消费线程。
2. 堆积后会放大回调延迟和消息积压。

### 如何重构

1. 配置 connect/read timeout（如 2s/3s）。
2. 使用连接池 HTTP 客户端，提升并发稳定性。
3. 超时参数配置化（Nacos）。

### 目标代码（建议）

```java
factory.setConnectTimeout(2000);
factory.setReadTimeout(3000);
return new RestTemplate(factory);
```

---

## 3.4 P0：`SMS_PUSH_REPORT` 队列契约依赖外部模块声明，边界不清

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:53`

```java
@RabbitListener(queues = RabbitMQConstants.SMS_PUSH_REPORT)
```

对比 `beacon-push` 本模块 MQ 配置，仅声明了延迟交换机和延迟队列：
文件：`beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java`

### 原因

1. `SMS_PUSH_REPORT` 队列由其他模块声明时，启动顺序与环境初始化耦合。
2. 当外部模块未先启动或队列未预建时，push 模块可用性受影响。

### 如何重构

1. 在 push 模块显式声明并绑定 `SMS_PUSH_REPORT` 队列。
2. 队列契约统一由消费方或基础设施层维护，不依赖生产方。

---

## 3.5 P0：延迟交换机配置为非持久化，重启后消息可靠性风险

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/config/RabbitMQConfig.java:33`

```java
new CustomExchange(DELAYED_EXCHANGE, DELAYED_EXCHANGE_TYPE, false, false, args);
```

### 原因

1. `durable=false` 导致 Broker 重启后交换机配置丢失。
2. 重试链路属于关键可靠性路径，不应使用非持久交换机。

### 如何重构

1. 改为持久化交换机（`durable=true`）。
2. 启动时校验 delayed plugin 可用性，失败时快速告警。

---

## 3.6 P1：重试策略硬编码，不具备可运营能力

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:35`

```java
private int[] delayTime = {0,15000,30000,60000,300000};
```

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:128`

```java
if(report.getResendCount() >= 5){
    return;
}
```

### 原因

1. 无法按客户等级、业务类型调整重试窗口。
2. 修改策略必须发版，响应慢。

### 如何重构

1. 将重试次数和间隔放入配置中心。
2. 支持“固定间隔 / 指数退避”策略切换。
3. 达到最大重试后进入失败归档队列（便于人工补偿）。

---

## 3.7 P1：回调成功判定过于单一

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:37`

```java
private final String SUCCESS = "SUCCESS";
```

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:112`

```java
flag = SUCCESS.equals(result);
```

### 原因

1. 只接受返回字符串完全等于 `SUCCESS`，与客户多样化返回协议不兼容。
2. 客户返回 JSON（如 `{"code":0}`）会被当成失败并触发重试风暴。

### 如何重构

1. 支持按客户配置成功判定规则（状态码、body jsonPath、关键字）。
2. 至少将 HTTP 2xx 与业务 body 判定拆分处理。

---

## 3.8 P1：监听器重复逻辑可抽象，降低维护成本

### 现状代码（需要重构）

文件：

1. `beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:53`
2. `beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:81`

两个监听方法都执行：

1. `pushReport(report)`
2. `isResend(report, flag)`
3. `basicAck(...)`

### 原因

1. 逻辑重复，后续改动容易漏改。
2. 行为一致性依赖人工保证。

### 如何重构

1. 提取统一处理方法 `process(report, channel, message)`。
2. 两个 listener 只负责入口路由。

---

## 3.9 P1：依赖 `org.apache.commons.lang.StringUtils` 版本老旧风格

### 现状代码（需要重构）

文件：`beacon-push/src/main/java/com/cz/push/mq/PushReportListener.java:10`

```java
import org.apache.commons.lang.StringUtils;
```

### 原因

1. `commons-lang`（老包名）与项目其他模块常见 `lang3` 风格不一致。
2. 增加依赖歧义和维护成本。

### 如何重构

1. 替换为 `org.springframework.util.StringUtils` 或 `org.apache.commons.lang3.StringUtils`。
2. 统一全项目字符串工具使用规范。

---

## 3.11 P2：测试缺失，回调与重试链路无自动回归

### 现状

`beacon-push/src/test` 当前为空。

### 风险

1. 回调成功/失败/重试分支没有自动化保护。
2. 任何策略调整都可能引入行为回归。

### 如何重构

1. 单元测试：`pushReport` 返回判定、`isResend` 重试边界。
2. 集成测试：Rabbit + Mock callback server 场景。
3. 故障测试：超时、DNS失败、5xx、非法 URL。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，先保可用性与可靠性）

1. 修复 `pushReport` 异常吞掉问题，补齐日志与错误分类。
2. 移除 `http://` 强拼接，改为完整 URL 校验。
3. 增加 `RestTemplate` 超时配置。
4. 在 push 模块声明 `SMS_PUSH_REPORT` 队列契约。
5. 将 delayed exchange 改为 durable。

## 阶段二（P1，提升可运营能力）

1. 重试次数与间隔配置化。
2. 回调成功判定规则配置化。
3. 抽取监听器重复逻辑，统一处理入口。
4. 统一 StringUtils 依赖风格。

## 阶段三（P2，工程化治理）

1. 替换 JsonUtil 旧实现依赖。
2. 补齐单测与集成测试，覆盖关键失败路径。

---

## 5. 建议测试清单

## 5.1 单元测试

1. `callbackUrl` 为空时直接 ack。
2. 推送成功不重试。
3. 推送失败后延迟重试次数与 delay 值正确。
4. 达到最大重试后停止重投。

## 5.2 集成测试

1. `SMS_PUSH_REPORT` -> HTTP callback -> 成功 ack 链路。
2. 回调超时/5xx -> 延迟队列重试链路。
3. delayed queue 消费后再次重试并最终收敛。

## 5.3 回归与压测

1. 高并发下回调吞吐与失败率。
2. 回调目标抖动场景（间歇性失败）重试稳定性。
3. 插件不可用（x-delayed-message）时启动行为与告警。

---

## 6. 跨模块联动建议

1. 与 `beacon-strategy`：统一 `StandardReport` 字段约定和成功判定策略。
2. 与 `beacon-monitor`：增加 push 失败率、重试次数、积压量监控。
3. 与 `beacon-common`：统一 JSON/日志工具，减少模块内重复治理。

建议按“先修可靠性，再做策略可配置化，最后完善测试”的顺序推进，避免回调链路在生产中出现不可观测失败。


# beacon-monitor 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-monitor  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `18_beacon-monitor_module_analysis.md`
2. `12_beacon-monitor_refactor_guide.md`

## 1. 模块定位

`beacon-monitor` 是短信平台的“监控告警执行器”，当前主要职责：

1. 通过 XXL-Job 定时扫描队列堆积并发送邮件告警
2. 通过 XXL-Job 定时扫描客户余额并发送余额不足邮件
3. 通过 Feign 调用 `beacon-cache` 获取监控数据
4. 通过 RabbitMQ `ConnectionFactory` 获取队列消息量

核心代码文件：

1. 启动类：`beacon-monitor/src/main/java/com/cz/monitor/MonitorStarterApp.java`
2. 任务配置：`beacon-monitor/src/main/java/com/cz/monitor/config/XxlJobConfig.java`
3. 队列告警任务：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`
4. 余额告警任务：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`
5. 邮件工具：`beacon-monitor/src/main/java/com/cz/monitor/util/MailUtil.java`
6. 缓存客户端：`beacon-monitor/src/main/java/com/cz/monitor/client/CacheClient.java`
7. 配置文件：`beacon-monitor/src/main/resources/bootstrap.yml`

---

## 2. 当前实现概览

## 2.1 启动与注册

`beacon-monitor` 使用 `SpringBoot + Nacos + OpenFeign`：

1. `@EnableDiscoveryClient`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/MonitorStarterApp.java:14`
2. `@EnableFeignClients`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/MonitorStarterApp.java:15`

## 2.2 定时任务

1. 队列堆积任务：`@XxlJob("monitorQueueMessageCountTask")`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java:55`
2. 余额告警任务：`@XxlJob("monitorClientBalanceTask")`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java:37`
3. 测试任务：`@XxlJob("test")`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/task/TestTask.java:15`

## 2.3 外部依赖

1. `beacon-cache`（Feign）  
文件：`beacon-monitor/src/main/java/com/cz/monitor/client/CacheClient.java:12`
2. RabbitMQ（队列消息数）  
文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java:47`
3. JavaMail（邮件告警）  
文件：`beacon-monitor/src/main/java/com/cz/monitor/util/MailUtil.java:29`

---

## 3. 需要重构的代码、原因、如何重构

## 3.1 配置安全：明文敏感信息在仓库中

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/resources/bootstrap.yml`

```yml
spring:
  mail:
    username: 2931163626@qq.com
    password: optzmoheptapdcfb
    tos: lc204573@gmail.com
```

### 原因

1. 明文账号和授权码进入代码仓库，安全风险高
2. 环境切换（dev/test/prod）成本高
3. 审计不可控

### 如何重构

1. 所有敏感配置迁移至 Nacos 密文配置或环境变量
2. 本地 `bootstrap.yml` 只保留占位符
3. 增加启动时配置校验（缺失则 fail-fast）

---

## 3.3 队列监控任务存在资源泄漏和异常处理问题

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

```java
Connection connection = connectionFactory.createConnection();
Channel channel = connection.createChannel(false);
...
channel.queueDeclare(queueName,true,false,false,null);
...
e.printStackTrace();
```

### 原因

1. `Connection/Channel` 未显式关闭，长期运行可能积累资源
2. 使用 `printStackTrace`，不利于日志聚合
3. `queueDeclare` 在监控任务中带有“改动 RabbitMQ 状态”副作用

### 如何重构

1. 使用 try-with-resources 或 finally 关闭连接和通道
2. 全面替换 `printStackTrace` 为结构化日志
3. 优先改用被动查询（如可行）避免声明队列副作用

### 目标代码（建议）

```java
try (Connection connection = connectionFactory.createConnection();
     Channel channel = connection.createChannel(false)) {
    // query logic
} catch (Exception ex) {
    log.error("monitor queue failed", ex);
}
```

---

## 3.4 队列告警逻辑缺少“防告警风暴”机制

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorQueueMessageCountTask.java`

```java
if (count > MESSAGE_COUNT_LIMIT) {
    mailUtil.sendEmail(subject, content);
}
```

### 原因

1. 每次调度都可能重复发同一告警，造成告警风暴
2. 未设置恢复告警（恢复正常后通知）
3. 阈值固定写死：`MESSAGE_COUNT_LIMIT = 10000`

### 如何重构

1. 增加告警去重窗口（如 Redis 记录最近告警时间）
2. 支持升级告警和恢复告警
3. 阈值改成配置化：队列级、全局级

---

## 3.5 对 `beacon-cache` 的 Feign 契约过于弱类型

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/java/com/cz/monitor/client/CacheClient.java`

```java
@PostMapping(value = "/cache/keys/{pattern}")
Set<String> keys(@PathVariable String pattern);

@GetMapping("/cache/hgetall/{key}")
Map hGetAll(@PathVariable("key") String key);
```

### 原因

1. `Map` 原始类型，调用方解析易出错
2. `keys` 使用 PathVariable 传 `channel:*`，路径编码脆弱
3. 强依赖 `beacon-cache` V1 接口，缺少版本化

### 如何重构

1. 迁移到 `beacon-cache` V2 typed API
2. 把 `pattern` 改为 QueryParam
3. 定义监控专用 DTO，去除裸 `Map`

---

## 3.6 余额告警任务对数据质量缺少防御

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/java/com/cz/monitor/task/MonitorClientBalanceTask.java`

```java
Map map = cacheClient.hGetAll(key);
Long balance = Long.parseLong(map.get(BALANCE) + "");
String email = (String) map.get(EMAIL);
if(balance < balanceLimit){
    mailUtil.sendEmail(email, ...);
}
```

### 原因

1. `map.get(...)` 可能为 null，存在 NPE/NumberFormatException
2. `extend1` 作为邮箱字段是隐式约定，可读性差
3. 阈值 `balanceLimit = 500000` 的单位与业务意义不透明

### 如何重构

1. 定义 `ClientBalanceSnapshot` DTO，显式字段与单位
2. 增加空值与格式校验，异常单条隔离
3. 告警阈值配置化，支持客户级覆盖

---

## 3.7 邮件工具类能力基础但缺少生产特性

### 现状代码（需要重构）

文件：`beacon-monitor/src/main/java/com/cz/monitor/util/MailUtil.java`

```java
helper.setText(text);
```

### 原因

1. 当前文本包含 HTML 片段但未显式 `html=true`
2. 缺少超时、重试、失败统计
3. 不支持统一模板化（主题、变量）

### 如何重构

1. 新增 `sendHtmlEmail` 与 `sendPlainEmail`
2. 引入邮件发送结果埋点（成功/失败计数）
3. 封装模板渲染（Thymeleaf/Freemarker 或轻量模板）

## 4. 推荐重构顺序

1. **第一阶段：安全与稳定兜底（优先）**
- 移除明文邮件密码
- 修复资源关闭与异常日志
- 给任务增加空值防御

2. **第二阶段：契约升级**
- `beacon-cache` Feign 迁移到 typed V2
- DTO 化替换原始 `Map`

3. **第三阶段：告警体系完善**
- 去重、恢复、升级告警
- 阈值配置中心化

4. **第四阶段：可观测与治理**
- 发送指标、任务耗时指标、失败重试指标

---

## 5. 建议新增测试

当前模块没有 `src/test` 测试目录，建议补齐：

1. `MonitorQueueMessageCountTaskTest`
- 队列超阈值时发告警
- 队列正常时不发告警
- `cacheClient.keys` 返回空时正常退出

2. `MonitorClientBalanceTaskTest`
- 余额低于阈值触发
- 无邮箱/非法余额时单条跳过不中断批次

3. `MailUtilTest`
- HTML 与纯文本发送分支
- 发件参数校验

## 6. 交付物建议

1. `docs/12_beacon-monitor_refactor_guide.md`（本文档）
2. 监控任务重构 PR（分阶段）
3. 告警降噪方案说明
4. 监控指标接入文档（Prometheus/Grafana）


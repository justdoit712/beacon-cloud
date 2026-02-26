# beacon-search 重构文档

## 1. 模块定位

`beacon-search` 是短信平台的“检索与日志索引模块”，主要职责：

1. 消费 `SMS_WRITE_LOG` 队列，将 `StandardSubmit` 写入 Elasticsearch。
2. 消费 `SMS_GATEWAY_DEAD_QUEUE` 队列，更新短信最终状态（成功/失败）。
3. 提供短信日志查询接口与状态聚合统计接口。

核心代码入口：

1. 启动类：`beacon-search/src/main/java/com/cz/search/SearchStarterApp.java`
2. 写日志监听：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java`
3. 更新日志监听：`beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java`
4. ES 核心实现：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java`
5. 查询接口：`beacon-search/src/main/java/com/cz/search/controller/SmsSearchController.java`

---

## 2. 当前实现概览

## 2.1 写入链路

1. `SmsWriteLogListener.consume` 接收 `StandardSubmit`。
2. 将对象转 `Map`，补充 `sendTimeMillis`。
3. 调用 `searchService.index(...)` 写入 `sms_submit_log_{year}`。
4. 手动 `ack`。

## 2.2 更新链路

1. `SmsUpdateLogListener.consume` 接收 `StandardReport`。
2. 把 report 放入 `SearchUtils` 的 `ThreadLocal`。
3. 调用 `searchService.update(...)` 更新 `reportState`。
4. 手动 `ack`。

## 2.3 查询链路

1. `SmsSearchController` 接收前端 `Map` 参数。
2. `ElasticsearchServiceImpl` 构造 bool 查询和高亮。
3. 返回分页列表（`total` + `rows`）与状态聚合（`waiting/success/fail`）。

---

## 3. 需要重构的代码、原因与重构方案

以下按优先级从高到低列出。

## 3.1 P0：`SearchRequest` 传入空索引名，存在查询异常风险

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:137`

```java
SearchRequest request = new SearchRequest(SearchUtils.getCurrYearIndex(), "");
```

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:185`

```java
SearchRequest request = new SearchRequest(SearchUtils.getCurrYearIndex(), "");
```

### 原因

1. 第二个索引参数是空字符串，可能导致 ES 请求报错或行为不可预期。
2. 查询链路是核心路径，不能依赖“空字符串恰好被忽略”。

### 如何重构

1. 只传有效索引数组。
2. 后续支持跨年查询时，传明确索引集合，不要放占位空值。

### 目标代码（建议）

```java
SearchRequest request = new SearchRequest(SearchUtils.getCurrYearIndex());
```

---

## 3.2 P0：写入幂等处理错误，重复消息会被判定为失败

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:68`

```java
String result = response.getResult().getLowercase();
if (!CREATED.equals(result)) {
    throw new SearchException(ExceptionEnums.SEARCH_INDEX_ERROR);
}
```

### 原因

1. ES `index` 同 ID 重试可能返回 `updated`，当前代码会直接抛错。
2. 在 MQ 至少一次投递语义下，重复消息应幂等成功，而不是报错。

### 如何重构

1. 将 `CREATED` 和 `UPDATED` 都视为成功。
2. 对重复消息写入 `debug/info` 日志，不抛业务异常。

### 目标代码（建议）

```java
Result result = response.getResult();
if (result != Result.CREATED && result != Result.UPDATED) {
    throw new SearchException(ExceptionEnums.SEARCH_INDEX_ERROR);
}
```

---

## 3.3 P0：更新链路使用 `ThreadLocal` 传参，且清理不完整

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java:29`

```java
SearchUtils.set(report);
searchService.update(...);
```

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:99`

```java
StandardReport report = SearchUtils.get();
if (report.getReUpdate()) { ... }
```

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:109`

```java
SearchUtils.remove();
```

### 原因

1. `remove()` 仅在“文档不存在”分支调用；更新成功路径未清理，存在线程复用污染风险。
2. `report` 可能为 `null`，`report.getReUpdate()` 存在 NPE 风险。
3. 业务参数依赖线程上下文，调试和测试成本高。

### 如何重构

1. 取消 `ThreadLocal` 传参，直接把 `report` 作为 `update` 方法参数传入。
2. 若短期保留 `ThreadLocal`，必须在 listener `finally` 中统一 `remove()`。
3. 增加空值防御。

### 目标代码（建议）

```java
public void update(String index, String id, Map<String, Object> doc, StandardReport report) { ... }
```

---

## 3.4 P0：索引年份使用“当前系统时间”，跨年场景会错索引

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java:42`

```java
searchService.index(INDEX + getYear(), submit.getSequenceId().toString(), ...);
```

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java:33`

```java
searchService.update(SearchUtils.INDEX + SearchUtils.getYear(), report.getSequenceId().toString(), doc);
```

### 原因

1. 索引按“消费时年份”而不是“业务发送时间年份”路由。
2. 年末积压消息或跨年重试时，写入和更新可能落到错误索引。

### 如何重构

1. 写入索引由 `submit.sendTime` 决定年份。
2. 更新索引由 `report` 中原始发送时间或已存储索引信息决定。
3. 无法确定索引时，按可控范围进行多索引更新（例如当年/前一年）。

---

## 3.5 P1：监听器异常路径缺少统一 nack/retry 策略

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java:46`

```java
channel.basicAck(...);
```

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsUpdateLogListener.java:36`

```java
channel.basicAck(...);
```

### 原因

1. 当前方法直接 `throws IOException`，异常重试行为依赖容器默认配置。
2. 未区分“可重试异常”和“不可重试异常”。
3. 高峰期会造成重试风暴或消息丢弃不可控。

### 如何重构

1. 明确 Rabbit listener 容器 retry、requeue、DLQ 策略。
2. 在监听器内按异常类型决定 ack/nack。
3. 增加失败计数与报警。

---

## 3.6 P1：查询参数与结果映射是弱类型，存在类型转换风险

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/controller/SmsSearchController.java:24`

```java
public Map<String,Object> findSmsByParameters(@RequestBody Map<String,Object> parameters)
```

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:240`

```java
if (clientIDObj instanceof List) {
    clientIDList = (List) clientIDObj;
}
...
boolQuery.must(QueryBuilders.termsQuery("clientId", clientIDList.toArray(new Long[] {})));
```

文件：`beacon-search/src/main/java/com/cz/search/service/impl/ElasticsearchServiceImpl.java:264`

```java
boolQuery.must(QueryBuilders.prefixQuery("mobile", (String) mobileObj));
```

### 原因

1. `Map` + 强转会在前端参数类型变化时出现 `ClassCastException/ArrayStoreException`。
2. 接口契约不清晰，前后端协作成本高。

### 如何重构

1. 定义 `SmsSearchQueryDTO`（分页、手机号、时间范围、clientIds、content）。
2. controller 层做校验和类型转换，service 层只处理强类型对象。
3. `clientIds` 统一转换为 `List<Long>` 后再构建 termsQuery。

---

## 3.7 P1：`SmsWriteLogListener` 每条消息 new `ObjectMapper`

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/mq/SmsWriteLogListener.java:33`

```java
ObjectMapper mapper = new ObjectMapper();
Map<String, Object> doc = mapper.convertValue(submit, Map.class);
```

### 原因

1. 高频场景下重复创建对象增加 GC 压力。
2. 与全局 JSON 配置不一致，时间字段序列化行为可能漂移。

### 如何重构

1. 注入 Spring 管理的 `ObjectMapper` 或统一转换组件。
2. 封装 `SubmitDocumentMapper`，集中维护字段映射规则。

---

## 3.8 P1：`SearchUtils` 工具类职责混杂（索引工具 + 线程上下文）

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/utils/SearchUtils.java:16`

```java
public static final String INDEX = "sms_submit_log_";
```

文件：`beacon-search/src/main/java/com/cz/search/utils/SearchUtils.java:32`

```java
private static ThreadLocal<StandardReport> reportThreadLocal = new ThreadLocal<>();
```

### 原因

1. 索引工具与运行时上下文耦合在一个类中，不利于维护。
2. ThreadLocal 语义不应放在通用 util 类中。

### 如何重构

1. `IndexNameResolver`：专门负责索引命名策略。
2. `ReportUpdateContext`：如确需上下文，封装成明确组件并统一清理。
3. 优先去除 ThreadLocal（见 3.3）。

---

## 3.9 P1：ES 客户端配置缺少参数校验与连接参数

### 现状代码（需要重构）

文件：`beacon-search/src/main/java/com/cz/search/config/RestHighLevelClientConfig.java:35`

```java
String[] hostAndPort = hostAndPorts.get(i).split(":");
httpHosts[i] = new HttpHost(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
```

### 原因

1. 未校验 host:port 格式，配置错误会在启动时直接抛数组越界/数字转换异常。
2. 未设置连接超时、socket 超时、请求超时。

### 如何重构

1. 引入 `@ConfigurationProperties` + `@Validated` 校验配置。
2. 设置 requestConfig（connectTimeout/socketTimeout）。
3. 对 host 列表做 trim 与格式校验。

---

## 3.10 P2：ES 客户端版本老旧，后续升级需规划

### 现状代码（需要重构）

文件：`beacon-search/pom.xml:50`

```xml
<artifactId>elasticsearch-rest-high-level-client</artifactId>
<version>7.6.2</version>
```

### 原因

1. `RestHighLevelClient` 已进入维护尾期，长期演进受限。
2. 与未来 ES 版本兼容成本会持续上升。

### 如何重构

1. 中短期先稳定现有契约。
2. 中长期迁移到 Elasticsearch Java API Client（新客户端）。

---

## 3.11 P2：测试覆盖薄弱，缺少关键回归场景

### 现状代码（需要重构）

文件：`beacon-search/src/test/java/com/cz/search/service/SearchServiceTest.java:21`

```java
searchService.index("sms_submit_log_2026","3","{\"clientId\": 3}");
```

### 原因

1. 测试用例几乎无断言，主要是调用验证。
2. 缺少监听器、重试、跨年索引、查询条件组合等核心场景测试。

### 如何重构

1. 单元测试：`buildBoolQuery`、索引命名策略、更新分支。
2. 集成测试：MQ listener + ES mock（Testcontainers 或 Mock client）。
3. 回归测试：重复消息幂等、跨年更新、clientIds 多类型输入。

---

## 4. 建议重构顺序（执行路线）

## 阶段一（P0，先修正确性）

1. 修复 `SearchRequest` 空索引参数问题。
2. 修复 `index()` 幂等逻辑（CREATED/UPDATED）。
3. 去掉 ThreadLocal 传参或补齐 finally 清理 + 空值防御。
4. 按业务时间路由索引（写入与更新一致）。

## 阶段二（P1，做稳定性与契约治理）

1. listener 异常重试策略显式化（ack/nack/requeue）。
2. 查询入参 DTO 化与类型安全改造。
3. ObjectMapper 复用与映射组件化。
4. Rest 客户端配置校验与超时参数补齐。

## 阶段三（P2，演进与工程化）

1. ES 客户端升级路线落地。
2. 完整测试体系建设与压测验证。

---

## 5. 建议测试清单

## 5.1 单元测试

1. `ElasticsearchServiceImpl#index`：CREATED/UPDATED/其他结果分支。
2. `ElasticsearchServiceImpl#buildBoolQuery`：content/mobile/time/clientIds 多组合输入。
3. 索引路由：基于 sendTime 的跨年场景。

## 5.2 集成测试

1. `SmsWriteLogListener`：写入成功、ES异常重试路径。
2. `SmsUpdateLogListener`：文档存在/不存在、重投递分支、最终 ack 行为。
3. 回执更新幂等：重复报告消息对最终状态的影响。

## 5.3 回归测试

1. 分页查询、关键词高亮、聚合统计准确性。
2. 大批量写入下的吞吐与失败率。
3. 年切换窗口（12/31 -> 1/1）消息一致性。

---

## 6. 跨模块联动建议

1. 与 `beacon-smsgateway`：统一回执更新的索引路由规则（按发送时间而非消费时间）。
2. 与 `beacon-common`：统一异常码与返回体，减少 search 模块对裸 `Map` 的依赖。
3. 与 `beacon-monitor`：增加 ES 写入失败率、更新重试次数、死信堆积监控。
4. 与 `beacon-webmaster`：对查询接口输出字段（`corpname/sign/sendTime`）形成稳定契约。

建议采用“先保证数据正确，再治理契约与性能，最后做客户端升级”的路径推进。


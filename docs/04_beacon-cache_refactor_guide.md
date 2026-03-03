# beacon-cache 模块重构文档

## 1. 模块定位

`beacon-cache` 是全系统的缓存能力中台，向业务模块暴露 Redis 操作接口（HTTP + Feign），主要服务对象包括：

1. `beacon-api`（鉴权、签名、模板、余额校验）
2. `beacon-strategy`（策略链、黑名单、限流、路由、扣费）
3. `beacon-smsgateway`（状态回调参数查询）
4. `beacon-monitor`（队列监控、余额巡检）
5. `beacon-test`（初始化/灌缓存）

模块入口与配置文件：

1. 启动类：`beacon-cache/src/main/java/com/cz/cache/CacheStarterApp.java`
2. Redis 配置：`beacon-cache/src/main/java/com/cz/cache/config/RedisConfig.java`
3. 对外接口：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`
4. 内部封装：`beacon-cache/src/main/java/com/cz/cache/redis/LocalRedisClient.java`
5. 运行配置：`beacon-cache/src/main/resources/bootstrap.yml`

---

## 2. 当前接口清单（现状）

核心对外接口都在 `CacheController`：

1. Hash
- `POST /cache/hmset/{key}`
- `GET /cache/hgetall/{key}`
- `GET /cache/hget/{key}/{field}`
- `POST /cache/hincrby/{key}/{field}/{delta}`

2. String
- `POST /cache/set/{key}`
- `GET /cache/get/{key}`

3. Set
- `POST /cache/sadd/{key}`
- `GET /cache/smember/{key}`
- `POST /cache/saddstr/{key}`
- `POST /cache/sinterstr/{key}/{sinterKey}`

4. ZSet
- `POST /cache/zadd/{key}/{score}/{member}`
- `GET /cache/zrangebyscorecount/{key}/{start}/{end}`
- `DELETE /cache/zremove/{key}/{member}`

5. 其他
- `POST /cache/pipeline/string`
- `POST /cache/keys/{pattern}`

调试接口（建议迁移/下线）：

1. `POST /test/set/{key}`
2. `GET /test/get/{key}`
3. `POST /test/pipeline`

文件：`beacon-cache/src/main/java/com/cz/cache/controller/TestController.java`

---

## 3. 需要重构的代码、原因、如何重构

## 3.1 Redis 序列化配置存在安全与兼容风险

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/config/RedisConfig.java`

```java
objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
```

### 原因

1. `enableDefaultTyping` 在新版本 Jackson 中已不推荐，存在反序列化安全风险
2. 序列化类型信息过强，跨语言或版本演进兼容性差
3. 当前模块作为共享缓存服务，序列化策略应更保守、可控

### 如何重构

1. 改为 `GenericJackson2JsonRedisSerializer`（推荐）
2. 或使用 `Jackson2JsonRedisSerializer<T>` + 明确类型边界
3. 禁止不受控默认多态，改白名单或显式类型

### 目标代码（建议）

```java
@Bean
public RedisSerializer<Object> redisSerializer() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return new GenericJackson2JsonRedisSerializer(mapper);
}
```

---

## 3.2 对外接口类型过于宽泛（大量 `Object/Map/Set` 原始类型）

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

```java
public Map hGetAll(...)
public Object hget(...)
public Set smember(...)
```

### 原因

1. 编译期类型信息丢失，调用方大量强转
2. Feign 客户端出现同一路径多返回类型映射（脆弱）
3. 容易把缓存服务变成“弱类型 RPC 黑盒”

### 如何重构

1. 增加 V2 typed API（保留 V1 兼容）
2. 对关键业务值定义 DTO（如 `ClientBusinessCacheDTO`）
3. 统一响应包装与错误码（建议复用 `ResultVO`）

### 目标代码（建议）

```java
@GetMapping("/v2/cache/hash/{key}")
public ResultVO<Map<String, String>> hGetAllString(@PathVariable String key) {
    Map<String, String> data = cacheFacade.hGetAllString(key);
    return ResultVO.success(data);
}
```

---

## 3.3 同一路径在多客户端映射为多种返回类型

### 现状代码（需要重构）

文件：

1. `beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`
2. `beacon-strategy/src/main/java/com/cz/strategy/client/BeaconCacheClient.java`
3. `beacon-smsgateway/src/main/java/com/cz/smsgateway/client/BeaconCacheClient.java`

示例：

```java
@GetMapping("/cache/hget/{key}/{field}")
Object hget(...);

@GetMapping("/cache/hget/{key}/{field}")
String hgetString(...);
```

### 原因

1. 同一路径、多返回类型依赖运行时 JSON 反序列化，容易出现类型偏差
2. 某些场景返回数值、字符串、对象时，调用方行为不可预测

### 如何重构

1. 在服务端拆分 typed endpoint：
- `/v2/cache/hash/{key}/string/{field}`
- `/v2/cache/hash/{key}/long/{field}`
- `/v2/cache/hash/{key}/int/{field}`
2. 或统一返回 JSON 节点对象，调用方按 schema 解析

---

## 3.4 `keys` 能力直接暴露，存在性能和稳定性风险

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

```java
Set<String> keys = redisTemplate.keys(pattern);
```

### 原因

1. `KEYS` 在大 key 空间下会阻塞 Redis
2. 该接口被 `beacon-monitor` 调用频繁，存在放大风险
3. 将 pattern 放在 PathVariable 中，也会有特殊字符转义问题

### 如何重构

1. 使用 `SCAN` 迭代替代 `KEYS`
2. 接口入参改 QueryParam：`GET /v2/cache/keys?pattern=...&count=...`
3. 添加白名单 pattern（仅允许受控前缀）

### 目标代码（建议）

```java
@GetMapping("/v2/cache/keys")
public ResultVO<List<String>> scanKeys(@RequestParam String pattern,
                                       @RequestParam(defaultValue = "1000") int count) {
    return ResultVO.success(redisScanService.scan(pattern, count));
}
```

---

## 3.5 `sinterstr` 实现存在并发与原子性问题

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

```java
redisClient.sAdd(key,value);
Set<Object> result = redisTemplate.opsForSet().intersect(key, sinterKey);
redisClient.delete(key);
```

### 原因

1. 三步操作非原子，失败时可能遗留临时 key
2. 如果调用方传入的 `key` 冲突，会互相污染
3. 对高并发下的敏感词检测路径不够稳健

### 如何重构

1. 服务端生成临时 key（UUID + 前缀），不信任外部传入临时 key
2. 用 Lua 脚本或事务确保“写临时集 -> 交集 -> 删除临时集”原子执行
3. 增加超时删除保护（TTL）

---

## 3.6 控制层直接承载业务与 Redis 细节，分层不清

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

控制器中存在大量 Redis 操作细节与日志拼接。

### 原因

1. Controller 层过重，不利于测试
2. 业务场景（限流、扣费、签名等）没有服务层语义
3. 难以做统一权限、审计、限流策略

### 如何重构

建议新增分层：

1. `cache.application`：用例服务（如 `ClientBalanceService`）
2. `cache.infrastructure.redis`：Redis 存取实现
3. `cache.interfaces.http`：仅做参数映射与响应

---

## 3.7 缺少访问控制和接口分级

### 现状代码（需要重构）

当前 `beacon-cache` 对外接口几乎全部裸露，`/test/**` 也在生产包内。

### 原因

1. 缓存服务属于高敏模块，写能力暴露风险高
2. 一旦被误调用，可直接改余额、黑名单、路由等关键数据

### 如何重构

1. 增加内部鉴权（网关白名单、mTLS 或内部 token）
2. 写接口与读接口分域
3. `/test/**` 迁移到 `test` profile 或独立工具模块

---

## 3.8 启动类与日志细节

### 现状代码（需要重构）

文件：`beacon-cache/src/main/java/com/cz/cache/CacheStarterApp.java`

```java
System.out.println("CacheStarterApp  mission complete");
```

### 原因

1. 标准服务日志应使用 slf4j
2. 与全局日志策略不一致

### 如何重构

1. 替换为 `log.info`
2. 增加启动参数和版本信息输出

---

## 4. 推荐重构顺序（beacon-cache 内部）

1. **第一阶段（低风险）**
- 清理日志与注释编码
- 下线/隔离 `/test/**`
- 增加基础测试和接口契约文档

2. **第二阶段（兼容改造）**
- 引入 `/v2/cache/**` typed API
- 原 `V1` 接口保留，标记 `@Deprecated`
- 新增 facade/service 分层

3. **第三阶段（稳定性）**
- `KEYS -> SCAN`
- `sinterstr` 原子化
- 增加限流、熔断、超时配置

4. **第四阶段（安全收口）**
- 替换 `enableDefaultTyping`
- 接口鉴权与写权限隔离
- 逐步迁移各模块 Feign 到 V2

---

## 5. 跨模块联动迁移顺序

因为 `beacon-cache` 是被依赖方，建议迁移顺序如下：

1. `beacon-cache` 先提供 V2 并保持 V1
2. 先迁移 `beacon-monitor`（接口少）
3. 再迁移 `beacon-smsgateway`（读取少）
4. 再迁移 `beacon-api`（入口核心）
5. 最后迁移 `beacon-strategy`（调用最重）
6. `beacon-test` 最后调整

---

## 6. 建议新增测试

1. `RedisConfigSerializationTest`
- 验证 LocalDateTime 序列化一致性
- 验证新序列化器兼容旧缓存值

2. `CacheControllerContractTest`
- 覆盖 hmset/hget/hincrby/zadd/zrange/smember/sinter
- 验证错误入参与边界值

3. `KeysScanPerformanceTest`
- 对比 `KEYS` 与 `SCAN` 性能和阻塞情况

4. `SinterAtomicityTest`
- 并发场景下验证无临时 key 泄漏

---

## 7. 交付物建议

1. `docs/04_beacon-cache_refactor_guide.md`（本文档）
2. `docs/beacon-cache-api-v2.md`（接口契约）
3. `beacon-cache` V2 代码与兼容层
4. 调用方 Feign 迁移 PR（分模块推进）


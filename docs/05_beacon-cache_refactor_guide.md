# beacon-cache 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-cache  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `03_beacon-cache_module_analysis.md`
2. `05_beacon-cache_refactor_guide.md`

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
- `GET /cache/keys?pattern=...&count=...`
- `DELETE /cache/delete/{key}`
- `POST /cache/delete/batch`

调试接口（当前默认关闭，长期仍建议迁移/下线）：

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

## 3.4 `keys` 链路仍有稳定性边界需要继续收口

### 当前代码

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

```java
@GetMapping(value = "/cache/keys")
public Set<String> keys(@RequestParam("pattern") String pattern,
                        @RequestParam(value = "count", defaultValue = "1000") Integer count){
    String logicalPattern = namespaceKeyResolver.toLogicalPattern(pattern);
    if (!isAllowedPattern(logicalPattern)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "pattern not allowed");
    }
    String physicalPattern = namespaceKeyResolver.toPhysicalPattern(logicalPattern);
    Set<String> physicalKeys = redisScanService.scan(physicalPattern, count);
    return namespaceKeyResolver.toLogicalKeys(physicalKeys);
}
```

### 现状

1. 该接口仍缺少调用频率治理与指标观测。
2. 白名单完全依赖配置正确性，配置漂移时仍可能放大扫描范围。
3. 大 keyspace 下 `SCAN` 虽然安全性更高，但仍有实际成本。

### 下一步建议

1. 给 `keys` 接口补充 QPS、耗时、返回 key 数量等指标。
2. 为高风险调用方增加更细粒度的 pattern 前缀限制。
3. 如后续引入 `/v2/cache/**`，可把 scan 能力一并迁移到 typed/受控的新接口层。

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

## 3.7 访问控制还需要密钥治理与接口分级

### 当前代码

文件：

1. `beacon-cache/src/main/java/com/cz/cache/config/WebMvcSecurityConfig.java`
2. `beacon-cache/src/main/java/com/cz/cache/security/CacheAuthInterceptor.java`
3. `beacon-cache/src/main/java/com/cz/cache/controller/TestController.java`

```java
registry.addInterceptor(cacheAuthInterceptor)
        .addPathPatterns("/cache/**", "/test/**");
```

```java
if (uri.startsWith("/cache/keys")) {
    return CachePermission.KEYS;
}
if ("GET".equalsIgnoreCase(request.getMethod())) {
    return CachePermission.READ;
}
return CachePermission.WRITE;
```

```java
@ConditionalOnProperty(prefix = "cache.security",
        name = "test-api-enabled", havingValue = "true")
public class TestController { ... }
```

### 现状

1. `callerSecrets` 仍直接放在配置文件中，缺少密钥托管与轮换机制。
2. 读接口与写接口仍在同一控制器下，边界表达不够清晰。
3. 当前权限粒度仍偏“调用方级”，尚未细化到更小的业务域或接口组。

### 下一步建议

1. 把密钥迁移到 Nacos/环境变量/密钥中心，并设计轮换策略。
2. 对写接口、扫描接口增加更细粒度的审计与告警。
3. 后续在 `/v2/cache/**` 上按读/写/扫描做更清晰的接口分层。

## 4. 推荐重构顺序（beacon-cache 内部）

1. **第一阶段（低风险）**
- 固化 `cache.security` 的密钥、权限与白名单配置
- 增加基础测试和接口契约文档

2. **第二阶段（兼容改造）**
- 引入 `/v2/cache/**` typed API
- 原 `V1` 接口保留，标记 `@Deprecated`

3. **第三阶段（稳定性）**
- `sinterstr` 原子化
- `keys` 扫描链路的限流/监控补齐
- 增加限流、熔断、超时配置

4. **第四阶段（安全收口）**
- 替换 `enableDefaultTyping`
- 密钥托管/轮换与更细粒度的写权限隔离
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

1. `docs/05_beacon-cache_refactor_guide.md`（本文档）
2. `docs/beacon-cache-api-v2.md`（接口契约）
3. `beacon-cache` V2 代码与兼容层
4. 调用方 Feign 迁移 PR（分模块推进）


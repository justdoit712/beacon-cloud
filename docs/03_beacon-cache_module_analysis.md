# beacon-cache 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-cache  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-cache` 是项目内统一的 Redis 能力服务，角色上属于“基础数据服务层”。  
其核心职责是把 Redis 的 Hash/Set/ZSet/String 操作封装成 HTTP 接口，供其他微服务通过 Feign 远程调用。

该模块本身业务逻辑很薄，但对系统稳定性影响非常大，因为：

1. API 校验、策略计算、路由选择、余额扣减都依赖它。
2. 监控模块也依赖它查询模式匹配 key（如 `client_balance:*`、`channel:*`）。

---

## 2. 模块结构

源码目录：`beacon-cache/src/main/java/com/cz/cache`

类清单：

1. `CacheStarterApp`：服务启动类。
2. `RedisConfig`：`RedisTemplate` 与序列化配置。
3. `LocalRedisClient`：对 `RedisTemplate` 的二次封装。
4. `CacheController`：正式缓存接口。
5. `RedisScanService`：基于 `SCAN` 的 key 扫描服务。
6. `CacheAuthInterceptor`：服务间鉴权与权限拦截。
7. `CacheSecurityProperties`：调用方密钥、权限、白名单配置。
8. `CacheNamespaceProperties`：缓存物理 key 命名空间配置。
9. `NamespaceKeyResolver`：逻辑 key 与物理 key 双向转换。
10. `TestController`：测试接口，仅在 `cache.security.test-api-enabled=true` 时注册。

配置文件：

1. `beacon-cache/src/main/resources/bootstrap.yml`

---

## 3. 技术实现分析

## 3.1 启动与注册

文件：`beacon-cache/src/main/java/com/cz/cache/CacheStarterApp.java`

能力：

1. `@SpringBootApplication`
2. `@EnableDiscoveryClient`

说明：

1. 服务通过 Nacos 注册发现。
2. 其他模块通过 `@FeignClient(value = "beacon-cache")` 调用。

## 3.2 Redis 序列化配置

文件：`beacon-cache/src/main/java/com/cz/cache/config/RedisConfig.java`

关键点：

1. key/hashKey 使用字符串序列化。
2. value/hashValue 使用 Jackson JSON 序列化。
3. 手动注册了 `LocalDate` / `LocalDateTime` 的序列化与反序列化格式。

意义：

1. 解决了时间类字段在缓存中的可读性和跨服务兼容问题。

## 3.3 Redis 操作封装

文件：`beacon-cache/src/main/java/com/cz/cache/redis/LocalRedisClient.java`

已封装能力：

1. Hash：`hSet`, `hGetAll`, `hGet`, `hIncrementBy`
2. String：`set`, `get`
3. Set：`sAdd`, `sMembers`
4. ZSet：`zAdd`, `zRemove`
5. 删除：`delete`
6. Pipeline：`pipelined`

特点：

1. 封装层很轻，便于扩展。
2. 泛型较宽，接口简单但类型安全弱。

## 3.4 访问控制、命名空间与 key 扫描

文件：

1. `beacon-cache/src/main/java/com/cz/cache/security/CacheAuthInterceptor.java`
2. `beacon-cache/src/main/java/com/cz/cache/config/WebMvcSecurityConfig.java`
3. `beacon-cache/src/main/java/com/cz/cache/security/CacheSecurityProperties.java`
4. `beacon-cache/src/main/java/com/cz/cache/security/CacheNamespaceProperties.java`
5. `beacon-cache/src/main/java/com/cz/cache/redis/NamespaceKeyResolver.java`
6. `beacon-cache/src/main/java/com/cz/cache/redis/RedisScanService.java`

关键点：

1. `/cache/**` 与 `/test/**` 当前都经过 `CacheAuthInterceptor`。
2. 调用方需提供 `X-Cache-Caller`、`X-Cache-Timestamp`、`X-Cache-Sign`，签名算法为 `HmacSHA256`。
3. 权限模型已细分为 `READ`、`WRITE`、`KEYS`、`TEST`、`ADMIN`。
4. `TestController` 默认关闭，只有显式开启 `cache.security.test-api-enabled=true` 才会注册。
5. 业务调用方传入的仍是逻辑 key；真正落 Redis 前由 `NamespaceKeyResolver` 统一追加 `cache.namespace.fullPrefix`。
6. `keys` 查询通过 `RedisScanService.scan(...)` 执行 `SCAN` 遍历，并按白名单前缀限制 pattern。

---

## 4. HTTP 接口清单（CacheController）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

## 4.1 Hash/Value/Set

1. `POST /cache/hmset/{key}`：批量写 hash
2. `GET /cache/hgetall/{key}`：读 hash 全量
3. `GET /cache/hget/{key}/{field}`：读 hash 字段
4. `POST /cache/hincrby/{key}/{field}/{delta}`：hash 字段自增
5. `POST /cache/set/{key}`：写 string
6. `GET /cache/get/{key}`：读 string
7. `POST /cache/sadd/{key}`：写 set（对象）
8. `POST /cache/saddstr/{key}`：写 set（字符串）
9. `GET /cache/smember/{key}`：读 set
10. `POST /cache/sinterstr/{key}/{sinterKey}`：交集计算（临时 key）

## 4.2 ZSet / 运营类

1. `POST /cache/zadd/{key}/{score}/{member}`：zset 写入
2. `GET /cache/zrangebyscorecount/{key}/{start}/{end}`：按分值区间统计
3. `DELETE /cache/zremove/{key}/{member}`：zset 删除
4. `GET /cache/keys?pattern=...&count=...`：按 pattern 查询 key 集合（基于 `SCAN`）
5. `DELETE /cache/delete/{key}`：删除单个逻辑 key
6. `POST /cache/delete/batch`：批量删除逻辑 key
7. `POST /cache/pipeline/string`：批量 pipeline 写入 string

---

## 5. 调用方与调用关系

`beacon-cache` 当前主要调用方如下：

1. `beacon-api`  
文件：`beacon-api/src/main/java/com/cz/api/client/BeaconCacheClient.java`  
用途：apikey、签名、模板、余额校验。

2. `beacon-strategy`  
文件：`beacon-strategy/src/main/java/com/cz/strategy/client/BeaconCacheClient.java`  
用途：黑名单、敏感词、限流、路由、扣费。

3. `beacon-smsgateway`  
文件：`beacon-smsgateway/src/main/java/com/cz/smsgateway/client/BeaconCacheClient.java`  
用途：查询回调开关与回调地址。

4. `beacon-monitor`  
文件：`beacon-monitor/src/main/java/com/cz/monitor/client/CacheClient.java`  
用途：扫描 key、监控余额和队列相关数据。

5. `beacon-test`  
文件：`beacon-test/src/main/java/com/cz/test/client/CacheClient.java`  
用途：测试数据准备与批量写入。

结论：

1. `beacon-cache` 是典型“共享底座服务”，可用性要求高于一般业务服务。

---

## 6. 在业务链路中的作用

## 6.1 API 校验链

1. 查客户信息：`client_business:*`
2. 查签名模板：`client_sign:*`, `client_template:*`
3. 查余额：`client_balance:*`

## 6.2 策略链

1. 黑名单校验：`black:*`
2. 敏感词校验：`dirty_word` + 交集计算
3. 限流校验：`limit:minutes:*`, `limit:hours:*`, `limit:days:*`
4. 路由选择：`client_channel:*`, `channel:*`
5. 扣费回滚：`hincrby`

## 6.3 监控链

1. 余额告警：`client_balance:*`
2. 队列监控辅助：`channel:*`

---

## 7. 主要风险与技术债

## 7.1 安全边界仍需继续收口（中风险）

1. 当前通过 `CacheAuthInterceptor` 对 `/cache/**`、`/test/**` 启用服务间 HMAC 鉴权。
2. 按调用方细分 `READ/WRITE/KEYS/TEST/ADMIN` 权限。
3. 仍然存在密钥明文配置、调用方权限矩阵维护，以及高影响写接口审计与观测不足的问题。

## 7.2 生产接口与测试接口仍在同一应用中，但已条件化暴露（中风险）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/TestController.java`

1. `TestController` 通过 `@ConditionalOnProperty(prefix = "cache.security", name = "test-api-enabled", havingValue = "true")` 控制注册。
2. 默认配置下测试接口不会注册，且即使开启，也仍受 `CacheAuthInterceptor` 的 `TEST` 权限控制。
3. 但测试代码仍在主应用包内，长期仍建议迁出主包或按 profile 做更强隔离。

## 7.3 `keys` 查询仍需关注白名单与扫描成本（中风险）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

1. 当前实现通过 `RedisScanService.scan(...)` 使用 `SCAN`。
2. 查询 pattern 会先经过 `cache.security.key-pattern-allow-list` 白名单校验，再转换为带命名空间的物理 pattern。
3. 大范围扫描仍有成本，后续仍需要控制 `count`、调用频率与监控指标。

## 7.4 序列化策略存在升级风险（中风险）

文件：`beacon-cache/src/main/java/com/cz/cache/config/RedisConfig.java`

1. 使用 `objectMapper.enableDefaultTyping(...)`（历史方案）。
2. Jackson 升级后兼容/安全治理成本较高。

## 7.5 类型安全不足（中风险）

1. 控制器和 Feign 多处使用 `Object`、原始 `Map/Set`。
2. 调用方强转失败时容易出现运行时异常。

## 7.6 缺少数据生命周期治理（中风险）

1. 大部分写操作未设置 TTL。
2. 临时数据和长期配置数据共享能力层，容易形成数据膨胀。

## 7.7 路径参数承载业务值（中风险）

1. 如 `zadd/{member}` 把业务值放在 path variable。
2. 特殊字符场景（空格、斜杠、编码符号）可用性不稳定。

---

## 8. 改造建议（按优先级）

## P0（优先执行）

1. 固化 `caller-secrets`、`caller-permissions`、`key-pattern-allow-list` 的配置治理，避免权限漂移。
2. 对 `/cache/keys`、`/cache/delete*` 等高影响接口补审计日志和调用指标。
3. 继续收紧 `TestController` 暴露策略，长期迁出主包或至少按 profile 隔离。

## P1（中期）

1. 引入 DTO 化接口，减少 `Object/Map` 直传和强转。
2. 分离“配置类 key”与“临时计算 key”的读写策略，按类型补 TTL。
3. 对高频接口增加熔断/限流与基础观测指标（QPS、耗时、错误率）。

## P2（长期）

1. 根据业务重要度将 cache 能力拆分：配置缓存、计数缓存、风控缓存。
2. 建立 contract test，保证 Feign 接口与 Controller 路径长期兼容。

---

## 9. 结论

`beacon-cache` 当前承担关键基础设施角色。  
下一阶段更值得优先处理的是“序列化升级 + typed API + 临时集合原子化 + 密钥治理”，避免基础层在扩容和演进时重新暴露风险。

# beacon-cache 模块详细分析

更新时间：2026-02-26  
适用仓库：`beacon-cloud`

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
5. `TestController`：测试接口。

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
4. `POST /cache/keys/{pattern}`：按 pattern 查询 key 集合
5. `POST /cache/pipeline/string`：批量 pipeline 写入 string

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

## 7.1 安全边界弱（高风险）

1. `CacheController` 和 `TestController` 接口默认全开放。
2. 未见鉴权、白名单、签名校验、限流保护。
3. 一旦被误调用或恶意调用，会直接影响全链路策略和余额。

## 7.2 生产接口与测试接口混合（高风险）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/TestController.java`

1. 测试入口仍在主应用中启用。
2. 建议仅在 `dev/test` profile 暴露或迁移到测试模块。

## 7.3 `keys` 命令潜在阻塞（高风险）

文件：`beacon-cache/src/main/java/com/cz/cache/controller/CacheController.java`

1. 使用 `redisTemplate.keys(pattern)`。
2. 大 keyspace 时会阻塞 Redis，影响在线请求。
3. 监控场景建议改为 `SCAN` 渐进遍历。

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

1. 为缓存接口加鉴权与最小授权控制（至少服务间 token 或网关内网隔离）。
2. 下线或隔离 `TestController` 到非生产 profile。
3. 将 `KEYS` 查询替换为 `SCAN`。

## P1（中期）

1. 引入 DTO 化接口，减少 `Object/Map` 直传和强转。
2. 分离“配置类 key”与“临时计算 key”的读写策略，按类型补 TTL。
3. 对高频接口增加熔断/限流与基础观测指标（QPS、耗时、错误率）。

## P2（长期）

1. 根据业务重要度将 cache 能力拆分：配置缓存、计数缓存、风控缓存。
2. 建立 contract test，保证 Feign 接口与 Controller 路径长期兼容。

---

## 9. 结论

`beacon-cache` 当前实现简洁，能快速支撑各模块协同，但已经承担了关键基础设施角色。  
下一阶段应优先补“安全隔离 + 查询方式治理 + 测试接口下线”三件事，避免基础层问题放大到整条短信链路。


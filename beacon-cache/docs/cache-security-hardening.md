# beacon-cache 接口安全收口与稳定性优化说明

更新时间：2026-02-27  
适用模块：`beacon-cache`（联动 `beacon-api`、`beacon-strategy`、`beacon-monitor`、`beacon-smsgateway`、`beacon-test`）

---

## 1. 文档目标

本文档用于说明 `beacon-cache` 本次“单环境下的接口安全收口”优化内容，明确：

1. 改了什么（代码与接口行为变化）。
2. 为什么这样改（风险治理目标）。
3. 如何配置（服务端与客户端配置项）。
4. 如何发布与验收（避免联调风险）。

---

## 2. 优化背景

`beacon-cache` 是全链路共享底座，承载余额、路由、黑名单、限流等核心数据读写。  
改造前存在以下高风险点：

1. `/cache/**`、`/test/**` 缺少统一认证与授权边界。
2. `TestController` 在主应用中默认暴露。
3. `KEYS` 查询直接暴露，可能阻塞 Redis。
4. 调用方 Feign 请求未做服务间签名认证。

本次目标是在不依赖“生产/测试环境拆分”的前提下，通过“能力隔离 + 调用方认证 + 权限分级”完成收口。

---

## 3. 本次改造总览

## 3.1 服务端安全框架（beacon-cache）

新增内容：

1. 认证与权限枚举
   - `beacon-cache/src/main/java/com/cz/cache/security/CachePermission.java`
2. 安全配置模型
   - `beacon-cache/src/main/java/com/cz/cache/security/CacheSecurityProperties.java`
3. 鉴权拦截器（HMAC + 时间戳 + 权限校验）
   - `beacon-cache/src/main/java/com/cz/cache/security/CacheAuthInterceptor.java`
4. Web 拦截注册
   - `beacon-cache/src/main/java/com/cz/cache/config/WebMvcSecurityConfig.java`

认证请求头约定（调用方必须带）：

1. `X-Cache-Caller`
2. `X-Cache-Timestamp`
3. `X-Cache-Sign`

签名校验失败返回：

1. `401`：缺头、时间戳非法、调用方未知、签名错误、时间漂移超限。
2. `403`：身份合法但权限不足。

---

## 3.2 测试接口收口

`TestController` 默认不加载，只有显式开启时才暴露：

1. 文件：`beacon-cache/src/main/java/com/cz/cache/controller/TestController.java`
2. 机制：`@ConditionalOnProperty(prefix = "cache.security", name = "test-api-enabled", havingValue = "true")`

默认行为：

1. `cache.security.test-api-enabled=false` 时，`/test/**` 不存在（404）。
2. 即使开启，也需调用方具备 `TEST` 权限才可访问。

---

## 3.3 KEYS 改造为 SCAN

接口改造：

1. 旧接口（高风险）：`POST /cache/keys/{pattern}`
2. 新接口：`GET /cache/keys?pattern=...&count=...`

实现变更：

1. `CacheController` 不再调用 `redisTemplate.keys(pattern)`。
2. 新增 `RedisScanService` 使用 `SCAN` 迭代：
   - `beacon-cache/src/main/java/com/cz/cache/redis/RedisScanService.java`
3. 新增 pattern 白名单限制，避免任意扫描：
   - `cache.security.key-pattern-allow-list`

注意：`beacon-monitor` 已同步 Feign 声明到新接口。

---

## 3.4 调用方 Feign 自动加签

新增公共签名工具（`beacon-common`）：

1. `beacon-common/src/main/java/com/cz/common/security/CacheAuthHeaders.java`
2. `beacon-common/src/main/java/com/cz/common/security/CacheAuthSignUtil.java`

为每个调用方新增 `CacheFeignAuthConfig`，自动在请求头写入 caller/timestamp/sign：

1. `beacon-api/src/main/java/com/cz/api/config/CacheFeignAuthConfig.java`
2. `beacon-strategy/src/main/java/com/cz/strategy/config/CacheFeignAuthConfig.java`
3. `beacon-monitor/src/main/java/com/cz/monitor/config/CacheFeignAuthConfig.java`
4. `beacon-smsgateway/src/main/java/com/cz/smsgateway/config/CacheFeignAuthConfig.java`
5. `beacon-test/src/main/java/com/cz/test/config/CacheFeignAuthConfig.java`

并在 `@FeignClient` 上绑定配置类，确保调用自动带签名。

---

## 4. 配置说明

## 4.1 服务端（beacon-cache）

配置位置：`beacon-cache/src/main/resources/bootstrap.yml`

```yml
cache:
  security:
    enabled: true
    test-api-enabled: false
    max-time-skew-seconds: 300
    key-pattern-allow-list:
      - client_balance:*
      - channel:*
    caller-secrets:
      beacon-api: cache_api_secret
      beacon-strategy: cache_strategy_secret
      beacon-monitor: cache_monitor_secret
      beacon-smsgateway: cache_smsgateway_secret
      beacon-test: cache_test_secret
    caller-permissions:
      beacon-api:
        - READ
      beacon-strategy:
        - READ
        - WRITE
      beacon-monitor:
        - READ
        - KEYS
      beacon-smsgateway:
        - READ
      beacon-test:
        - READ
        - WRITE
        - TEST
```

字段说明：

1. `enabled`：是否启用鉴权拦截（紧急回滚可置 `false`）。
2. `test-api-enabled`：是否暴露 `/test/**` 接口。
3. `max-time-skew-seconds`：允许客户端时间偏差秒数。
4. `key-pattern-allow-list`：允许扫描的 key 前缀模式白名单。
5. `caller-secrets`：调用方身份与签名密钥映射。
6. `caller-permissions`：调用方权限集合。

权限枚举：

1. `READ`
2. `WRITE`
3. `KEYS`
4. `TEST`
5. `ADMIN`（拥有全部权限）

---

## 4.2 调用方（Feign 客户端）

各模块配置位置：

1. `beacon-api/src/main/resources/bootstrap.yml`
2. `beacon-strategy/src/main/resources/bootstrap.yml`
3. `beacon-monitor/src/main/resources/bootstrap.yml`
4. `beacon-smsgateway/src/main/resources/bootstrap.yml`
5. `beacon-test/src/main/resources/application.yml`

配置结构：

```yml
cache:
  client:
    auth:
      enabled: true
      caller: beacon-api
      secret: cache_api_secret
```

字段说明：

1. `enabled`：是否开启请求自动加签。
2. `caller`：调用方身份，必须与服务端 `caller-secrets` 键一致。
3. `secret`：签名密钥，必须与服务端该 caller 对应值一致。

---

## 5. 签名规则

签名算法：`HMAC-SHA256`  
签名载荷拼接规则（严格换行）：

```text
{caller}\n{timestamp}\n{HTTP_METHOD}\n{path_without_query}
```

示例：

```text
beacon-api
1709020800000
GET
/cache/hget/client_balance:1001/balance
```

---

## 6. 接口行为变化（兼容性影响）

## 6.1 不兼容变更

1. `keys` 接口调用方式变更：  
   从 `POST /cache/keys/{pattern}` 变为 `GET /cache/keys?pattern=...&count=...`。
2. 所有 `beacon-cache` 接口默认要求鉴权头（启用鉴权时）。
3. `/test/**` 默认不再暴露。

## 6.2 兼容点

1. 除 `keys` 外，其他业务接口 URL 未改。
2. Feign 客户端在配置完成后自动加签，业务调用代码无侵入。

---

## 7. 发布建议（单环境）

推荐顺序：

1. 先部署调用方加签能力（客户端配置+代码）。
2. 再部署 `beacon-cache` 鉴权拦截器并开启 `cache.security.enabled=true`。
3. 最后观察 5~10 分钟调用日志与错误率，再执行后续优化。

若需降风险灰度：

1. 先将 `cache.security.enabled=false` 发布服务端代码。
2. 调用方全量完成后再把 `enabled` 改为 `true`。

---

## 8. 验收清单

1. 不带签名请求访问 `/cache/**` 返回 `401`。
2. 签名错误请求返回 `401`。
3. 非授权 caller 写接口返回 `403`。
4. `/test/**` 在默认配置下返回 `404`。
5. `beacon-monitor` 能通过新 `keys` 接口正常扫描白名单 pattern。
6. Redis 侧不再出现 `KEYS` 带来的阻塞告警。

---

## 9. 回滚方案

快速回滚开关（服务端）：

1. 将 `cache.security.enabled=false`，临时关闭鉴权拦截。
2. 如需恢复测试接口，`cache.security.test-api-enabled=true`。

注意：

1. 回滚仅用于紧急处置，建议尽快恢复鉴权。
2. 生产密钥请迁移到 Nacos 或密钥管理系统，不要长期明文留仓库。

---

## 10. 后续可选优化

1. `sinterstr` 原子化（Lua/事务 + 临时 key TTL）。
2. 将 `caller-secrets` 对接外部密钥管理，减少配置泄露风险。
3. 补充 `CacheAuthInterceptor` 与 `RedisScanService` 单元测试。
4. 推进 `V2 typed API`，逐步减少 `Object/Map` 弱类型返回。


# 第二层：运行时同步（架构说明）

文档定位：日常主链路层  
适用对象：开发 / 排障 / 答辩  
验证基线：当前仓库代码静态核对  
最后核对日期：2026-03-24

---

## 0. 本层在整套架构中的位置

第二层处理的是：

> 系统正常运行时，MySQL 刚改完，Redis 应该怎样尽快跟上。

它不负责：

1. 历史脏缓存的全量修复
2. 应用启动时的自动校准

它负责的是：

1. 业务成功写 MySQL 后，按当前域契约刷新 Redis
2. 在重建进行中时，避免运行时同步直接打断重建

---

## 1. 当前第二层主要类

### 1.1 通用同步入口

| 类 | 作用 |
| --- | --- |
| `CacheSyncRuntimeExecutor` | 决定“事务提交后执行”还是“立即执行”，并在重建中做脏标记避让 |
| `CacheSyncService` | 运行时同步统一门面 |
| `CacheSyncServiceImpl` | 按域路由构建 key、解析 payload、选择实际 Redis 写法 |
| `CacheSyncLogHelper` | 输出结构化同步日志 |

### 1.2 业务触发层

| 类 | 作用 |
| --- | --- |
| `ClientBusinessServiceImpl` | 触发 `client_business` 的 save/update/delete 同步 |
| `ChannelServiceImpl` | 触发 `channel` 的 save/update/delete 同步 |
| `ClientChannelServiceImpl` | 触发 `client_channel` 的整组快照重建 |
| `BalanceCommandServiceImpl` | 触发 `client_balance` 与 `client_business` 的双域刷新 |
| `LegacyCrudServiceImpl` | 触发 `black` / `dirty_word` / `transfer` 兼容同步 |

### 1.3 缓存服务调用桥

| 类 | 作用 |
| --- | --- |
| `BeaconCacheWriteClient` | 调用 `beacon-cache` 的 Feign 客户端 |
| `CacheFeignAuthConfig` | 负责对 Feign 请求自动签名 |

---

## 2. 当前运行时同步的总流程

当前运行时同步的大链路如下：

```text
业务 Service
    -> 先写 MySQL
    -> CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)
    -> CacheSyncServiceImpl.syncUpsert/syncDelete(...)
    -> BeaconCacheWriteClient
    -> beacon-cache / CacheController
    -> NamespaceKeyResolver
    -> Redis
```

展开后可以拆成 8 步：

1. 业务 Service 先更新 MySQL 真源
2. Service 决定需要同步哪个域
3. 调用 `CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)`
4. 如果当前有事务，就把动作挂到 `afterCommit`
5. 事务真正提交后，执行 `CacheSyncService.syncUpsert(...)` 或 `syncDelete(...)`
6. `CacheSyncServiceImpl` 根据域契约：
   - 规范化域名
   - 构建逻辑 key
   - 解析 payload
   - 选择 Hash / Set / String 写法
7. 通过 `BeaconCacheWriteClient` 调用 `beacon-cache`
8. `beacon-cache` 再把逻辑 key 转成物理 key 写入 Redis

---

## 3. 为什么运行时同步通常要等事务提交

当前项目的口径是：

1. MySQL 是真源
2. Redis 是派生缓存

因此如果事务还没提交，就先写 Redis，会出现典型问题：

1. Redis 已经是新值
2. MySQL 最后回滚
3. 结果缓存反而比真源“更靠前”

所以 `CacheSyncRuntimeExecutor` 的默认策略是：

1. **有事务时优先走 `afterCommit`**
2. **没事务时才立即执行**

当前关键方法：

1. `runAfterCommitOrNow(...)`
2. `runAction(...)`

---

## 4. `client_business` 运行时同步端到端详解

下面用 `client_business` 做完整示例。

假设当前客户业务配置的 `apiKey = ak_1001`。

### 4.1 新增链路：`save(...)`

入口类：

1. `ClientBusinessServiceImpl.save(...)`

当前步骤如下：

1. 页面或控制器提交客户业务配置
2. `ClientBusinessServiceImpl.save(...)` 补齐：
   - `id`
   - `clientFilters`
   - `created`
   - `updated`
   - `isDelete`
3. 调用 `clientBusinessMapper.insertSelective(...)` 写 MySQL
4. 写成功后，再调用 `clientBusinessMapper.selectByPrimaryKey(...)` 获取最新真源快照
5. 调用：
   - `cacheSyncRuntimeExecutor.runAfterCommitOrNow(() -> cacheSyncService.syncUpsert(...))`
6. 如果当前处于事务里，这个 `syncUpsert` 会在提交后执行
7. 进入 `CacheSyncServiceImpl.syncUpsert("client_business", latestEntity)`
8. `syncUpsert(...)` 做 3 件事：
   - `normalizeDomain("client_business")`
   - `requireDomainContract("client_business")`
   - `buildKey("client_business", latestEntity)`
9. `buildKey(...)` 会调用：
   - `CacheKeyBuilder.clientBusinessByApiKey(apiKey)`
10. 得到逻辑 key：
   - `client_business:ak_1001`
11. `doCurrentMainlineUpsert(...)` 识别到它是 Hash 域
12. `resolveClientBusinessPayload(...)` 把实体转成 Hash payload
13. 调用：
   - `BeaconCacheWriteClient.hmset("client_business:ak_1001", payload)`
14. Feign 请求经过 `CacheFeignAuthConfig` 自动带上缓存调用签名头
15. `beacon-cache` 收到后：
   - `CacheAuthInterceptor` 验签
   - `CacheController.hmset(...)` 执行写入
   - `NamespaceKeyResolver.toPhysicalKey(...)` 补上命名空间前缀
16. 最终 Redis 中落库的物理 key 例如：
   - `beacon:dev:beacon-cloud:cz:client_business:ak_1001`

### 4.2 典型 payload 长什么样

当前 `client_business` 最终写入 Redis 的 payload 可以理解为一份对象字段 Hash。

典型形态如下：

```json
{
  "id": 1001,
  "corpname": "demo-corp",
  "apikey": "ak_1001",
  "ipAddress": "127.0.0.1,10.0.0.1",
  "isCallback": 1,
  "callbackUrl": "client.example.com/report",
  "clientFilters": "blackGlobal,blackClient,dirtyword,route",
  "created": "2026-03-24T10:00:00",
  "updated": "2026-03-24T10:05:00",
  "isDelete": 0
}
```

这里要注意两点：

1. 当前不是手工拼几个字段，而是先把实体转成 Map
2. `resolveClientBusinessPayload(...)` 会强校验 `apiKey/apikey` 必须存在，否则认为 payload 非法

### 4.3 更新链路：`update(...)`

入口类：

1. `ClientBusinessServiceImpl.update(...)`

和新增相比，更新多了一步“旧 key 清理”。

当前步骤：

1. 先查更新前快照 `before`
2. 更新 MySQL
3. 再查更新后快照 `latest`
4. 如果 `before.apikey` 和 `latest.apikey` 不同，说明逻辑 key 已改变
5. 先注册：
   - `cacheSyncService.syncDelete("client_business", before)`
6. 再注册：
   - `cacheSyncService.syncUpsert("client_business", latest)`

所以当 `apiKey` 被修改时，链路会变成：

```text
旧 key 删除：client_business:oldApiKey
新 key 写入：client_business:newApiKey
```

### 4.4 删除链路：`deleteBatch(...)`

入口类：

1. `ClientBusinessServiceImpl.deleteBatch(...)`

当前步骤：

1. 先把 MySQL 记录逻辑删除
2. 对每条有效旧记录，注册一次：
   - `cacheSyncService.syncDelete("client_business", existing)`
3. `syncDelete(...)` 会构建逻辑 key
4. 最终调用 `BeaconCacheWriteClient.delete(key)` 删除 Redis key

---

## 5. `client_business` 链路里每个类各做什么

| 类 / 方法 | 当前作用 |
| --- | --- |
| `ClientBusinessServiceImpl.save` | 写 MySQL 后注册新增同步 |
| `ClientBusinessServiceImpl.update` | 处理旧 key 删除和新 key 写入 |
| `ClientBusinessServiceImpl.deleteBatch` | 处理逻辑删除后的缓存失效 |
| `CacheSyncRuntimeExecutor.runAfterCommitOrNow` | 控制执行时机 |
| `CacheSyncServiceImpl.syncUpsert` | 统一同步入口 |
| `CacheSyncServiceImpl.buildCurrentMainlineKey` | 构建逻辑 key |
| `CacheSyncServiceImpl.resolveClientBusinessPayload` | 构建 Hash payload |
| `BeaconCacheWriteClient.hmset` | 通过 Feign 调用缓存服务 |
| `CacheController.hmset` | 缓存服务 Hash 写入口 |
| `NamespaceKeyResolver.toPhysicalKey` | 逻辑 key -> 物理 key |

---

## 6. 当前第二层如何处理不同类型的缓存

### 6.1 Hash 型域

典型域：

1. `client_business`
2. `channel`
3. `client_balance`

当前统一做法：

1. 构建逻辑 key
2. 解析 Map payload
3. 调用 `hmset`

### 6.2 Set 型域

典型域：

1. `client_channel`
2. `client_sign`
3. `client_template`
4. `dirty_word`

当前统一做法不是增量加成员，而是：

1. 删除旧集合
2. 按全量快照重建

实现入口：

1. `rebuildSetDomain(...)`

### 6.3 String 型域

典型域：

1. `black`
2. `transfer`

当前统一走：

1. `CacheSyncServiceImpl.resolveStringValue(...)`
2. `BeaconCacheWriteClient.set(...)`

---

## 7. 当前两个最特殊的运行时域

### 7.1 `client_channel`

它不是一条对象记录，而是“某个客户的一整组通道路由结果”。

所以当前运行时同步不做：

1. 往 Set 里补一个成员
2. 从 Set 里删一个成员

而是做：

1. `ClientChannelServiceImpl.buildClientChannelPayload(clientId)` 重新查询该客户当前全部有效成员
2. payload 固定包含：
   - `clientId`
   - `members`
3. 统一调用：
   - `cacheSyncService.syncUpsert("client_channel", payload)`
4. 最终在 `CacheSyncServiceImpl` 内部执行：
   - 删除旧 Set
   - 按快照 `sadd`

### 7.2 `client_balance`

它是当前最严格的运行时域。

当前入口：

1. `InternalBalanceController`
2. `BalanceCommandService`
3. `BalanceCommandServiceImpl`

当前原则：

1. 余额真源是 MySQL `client_balance`
2. 扣费、充值、调账先走原子 SQL
3. SQL 成功后，再查询最新 `ClientBalance`
4. 再查询最新 `ClientBusiness`
5. 然后同时刷新两个域：
   - `client_balance`
   - `client_business`

关键方法：

1. `debitAndSync(...)`
2. `rechargeAndSync(...)`
3. `adjustAndSync(...)`
4. `scheduleBalanceDoubleRefresh(...)`

为什么要双域刷新：

1. `client_balance` 是余额镜像缓存
2. `client_business` 也可能承载部分客户整体配置视图

---

## 8. 运行时同步失败时当前怎么处理

当前实现不是“Redis 写失败就回滚 MySQL”，而是：

1. 记录结构化错误日志
2. 主业务流继续

`CacheSyncRuntimeExecutor.runAction(...)` 当前的行为是：

1. `action.run()` 成功则记成功日志
2. 如果失败，则记错误日志
3. 如果当前域是 `client_balance`，再额外记一条：
   - `compensationPlaceholder`
4. 非余额域则记一条：
   - `degradeContinue`

这代表当前方案的边界是：

1. 运行时同步是强约束但不是强事务
2. Redis 漂移仍然依靠后续重建路径修复

---

## 9. 如果运行时同步撞上重建怎么办

当前不会直接继续写 Redis。  
而是由 `CacheSyncRuntimeExecutor.shouldMarkDirtyAndSkip(...)` 处理。

当前逻辑：

1. 先判断同域是否正在重建
2. 如果正在重建，不直接执行原始写入
3. 只在 Redis 里记录一次脏标记
4. 当前同步动作跳过

这样做的原因是：

1. 避免运行时写入打断重建
2. 避免“删旧 key -> 重建中 -> 又被局部写入覆盖”的混乱状态

---

## 10. 本层小结

第二层可以总结成 5 句话：

1. 它解决“平时写库后 Redis 怎么尽快跟上”的问题
2. 它优先在 `afterCommit` 执行，避免事务回滚导致脏缓存
3. 它通过 `CacheSyncServiceImpl` 把不同域统一路由成正确的 Redis 写法
4. 它把 `client_channel` 当整组集合处理，把 `client_balance` 当高风险镜像域处理
5. 它不是终极修复手段，历史漂移仍由第三层和第四层负责兜底

如果把整套一致性设计比作一栋楼，第二层就是：

> 平时大家每天都在走的主楼层。

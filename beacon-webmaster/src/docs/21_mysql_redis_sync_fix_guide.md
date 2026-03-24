# MySQL 到 Redis 同步专题文档

文档类型：专题方案  
适用对象：开发 / 排障 / 答辩 / 演示  
验证基线：当前仓库代码静态核对  
关联模块：`beacon-webmaster` / `beacon-cache` / `beacon-common`  
最后核对日期：2026-03-24

---

原始来源（已合并并按当前代码刷新）：

1. `21_mysql_redis_sync_fix_guide.md`
2. `beacon-webmaster/src/docs/21_1_mysql_redis_sync_layer1_foundation.md`
3. `beacon-webmaster/src/docs/21_2_mysql_redis_sync_layer2_runtime_sync.md`
4. `beacon-webmaster/src/docs/21_3_mysql_redis_sync_layer3_manual_rebuild.md`
5. `beacon-webmaster/src/docs/21_4_mysql_redis_sync_layer4_boot_reconcile.md`

## 0. 这份文档解决什么问题

这份文档不再只停留在“方案概述”，而是直接对齐当前代码，回答下面 4 个问题：

1. 当前项目到底采用了什么缓存一致性方案
2. 一次业务数据变更是怎样从 MySQL 走到 Redis 的
3. 运行时同步、手工重建、启动校准分别经过哪些类
4. 关键常量、契约对象和缓存侧接口在整条链路里各自负责什么

如果只用一句话概括当前实现：

> 当前项目采用“规则冻结 + 运行时同步 + 手工重建 + 启动校准 + 缓存服务命名空间隔离”的组合方案，以 MySQL 为真源，以 Redis 为派生缓存，并通过运行时同步和重建链路共同收敛一致性。

---

## 1. 当前一致性方案的边界

### 1.1 真源是什么

当前主线缓存域的真源都是 MySQL。

这意味着：

1. Redis 负责加速读取，不负责定义最终正确值
2. Redis 脏了可以从 MySQL 重新恢复
3. 运行时同步失败时，系统会记录错误并依靠后续修复路径收敛

### 1.2 这是不是强一致分布式事务

不是。

当前方案的定位是：

1. 业务写 MySQL 成功后，尽快把 Redis 跟上
2. 通过 `afterCommit` 减少回滚导致的脏写
3. 通过手工重建和启动校准修复历史漂移
4. 通过重建锁和脏标记降低并发重建时的混乱风险

所以它是：

1. **以 MySQL 为真源的最终一致方案**
2. **适合当前项目规模和毕设场景的工程化落地**

### 1.3 当前闭环最完整的域

当前最完整的主线闭环域有 4 个：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

兼容保留域在注册表里也有契约定义：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

其中：

1. `black`、`dirty_word`、`transfer` 已接入兼容型运行时同步适配
2. `client_sign`、`client_template` 当前主要仍停留在契约层和通用写入层兼容

---

## 2. 先看“规则层”：常量、契约、范围

缓存一致性链路的第一步不是写 Redis，而是先把规则冻结。

### 2.1 关键常量 / 契约类总表

| 类 / 常量 | 当前作用 | 在链路中的位置 |
| --- | --- | --- |
| `CacheKeyConstants` | 定义逻辑 key 前缀，如 `client_business:`、`channel:` | 最底层命名约定 |
| `CacheKeyBuilder` | 统一拼接逻辑 key，避免业务侧手工硬编码 | 运行时同步 / 重建都会调用 |
| `CacheDomainRegistry` | 注册缓存域、主线范围、兼容范围、手工重建范围、boot 范围 | 规则收口点 |
| `CacheDomainContract` | 描述单域契约：key 模式、Redis 结构、真源、写删策略、重建策略 | 各层判断依据 |
| `CacheRedisType` | 描述 Redis 结构类型：`STRING` / `HASH` / `SET` / `ZSET` | 决定写法 |
| `CacheSourceOfTruth` | 定义真源：当前主线域都是 `MYSQL` | 口径约束 |
| `CacheWritePolicy` | 定义写入策略，如 `WRITE_THROUGH`、`DELETE_AND_REBUILD`、`MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | 决定运行时和重建时写法 |
| `CacheDeletePolicy` | 定义删除策略，如 `DELETE_KEY`、`OVERWRITE_ONLY` | 决定 `syncDelete` 是否真的删 key |
| `CacheRebuildPolicy` | 定义重建策略，如 `FULL_REBUILD` | 决定是否可参与重建 |
| `CacheAuthHeaders` | 定义缓存内部调用认证头：`X-Cache-Caller`、`X-Cache-Timestamp`、`X-Cache-Sign` | `webmaster -> cache` 调用安全 |

### 2.2 `CacheKeyConstants` 在当前代码里代表什么

`CacheKeyConstants` 不是“随便写写的前缀常量”，而是业务语义最原始的 key 模板来源。

例如：

1. `CLIENT_BUSINESS = "client_business:"`
2. `CLIENT_BALANCE = "client_balance:"`
3. `CLIENT_CHANNEL = "client_channel:"`
4. `CHANNEL = "channel:"`
5. `BLACK = "black:"`
6. `DIRTY_WORD = "dirty_word"`
7. `TRANSFER = "transfer:"`

这些常量本身不直接落 Redis。

真正的职责分工是：

1. `CacheKeyConstants` 提供 key 前缀语义
2. `CacheKeyBuilder` 负责把业务 id / apiKey 拼成逻辑 key
3. `NamespaceKeyResolver` 负责把逻辑 key 转成物理 key

### 2.3 当前主线域契约

当前 `CacheDomainRegistry` 注册的主线域如下：

| 域 | 逻辑 key 模式 | Redis 结构 | 真源 | 写入策略 | 删除策略 | 默认手工重建 | 默认启动校准 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apikey}` | `HASH` | `MYSQL` | `WRITE_THROUGH` | `DELETE_KEY` | 是 | 是 |
| `client_channel` | `client_channel:{clientId}` | `SET` | `MYSQL` | `DELETE_AND_REBUILD` | `DELETE_KEY` | 是 | 是 |
| `channel` | `channel:{id}` | `HASH` | `MYSQL` | `WRITE_THROUGH` | `DELETE_KEY` | 是 | 是 |
| `client_balance` | `client_balance:{clientId}` | `HASH` | `MYSQL` | `MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | `OVERWRITE_ONLY` | 是 | 是 |

这里最关键的两个特殊点：

1. `client_channel` 是集合型域，所以当前统一走“删旧集合 -> 全量重建”
2. `client_balance` 是高风险镜像域，所以当前不允许默认删 key，只允许覆盖刷新

### 2.4 为什么 `client_balance` 当前已经进入 boot / manual

当前代码里，`client_balance` 的契约已设置为 `bootRebuildEnabled=true`，并且它已处于当前主线域和当前手工重建域范围内。

因此：

1. 它已经可以进入 `ALL` 手工重建范围
2. 它也已经可以进入默认启动校准范围

这与旧版专题文档里“不要纳入普通 boot rebuild”的规划口径不同。  
当前应以代码为准：**它已经正式接入当前重建体系**。

---

## 3. 关键类按层分工

### 3.1 规则层

| 类 | 作用 |
| --- | --- |
| `CacheDomainRegistry` | 统一维护域清单、主线/兼容范围、manual/boot 范围 |
| `CacheDomainContract` | 定义单域契约 |
| `CacheKeyBuilder` | 统一构建逻辑 key |
| `CacheSyncProperties` | 统一承接 `sync.*` 配置，并校验命名空间一致性 |
| `CacheNamespaceConsistencyGuard` | 显式确保同步侧和缓存侧命名空间对齐 |

### 3.2 运行时同步层

| 类 | 作用 |
| --- | --- |
| `CacheSyncRuntimeExecutor` | 决定“事务提交后执行”还是“立即执行”，并处理重建期脏标记避让 |
| `CacheSyncService` | 统一同步门面 |
| `CacheSyncServiceImpl` | 按域路由构建 key、解析 payload、选择 Hash/Set/String 写法 |

### 3.3 业务触发层

| 类 | 作用 |
| --- | --- |
| `ClientBusinessServiceImpl` | 客户业务配置变更后触发 `client_business` 同步 |
| `ChannelServiceImpl` | 通道变更后触发 `channel` 同步 |
| `ClientChannelServiceImpl` | 客户通道路由变更后触发 `client_channel` 快照重建 |
| `BalanceCommandServiceImpl` | 余额扣费/充值/调账成功后同时刷新 `client_balance` 和 `client_business` |
| `LegacyCrudServiceImpl` | 兼容域 `black` / `dirty_word` / `transfer` 的运行时同步适配 |

### 3.4 重建层

| 类 | 作用 |
| --- | --- |
| `CacheRebuildController` | 手工重建入口 |
| `CacheRebuildService` / `CacheRebuildServiceImpl` | 对外暴露重建接口并委托底层引擎 |
| `DomainRebuildLoader` | 定义“某个域如何从 MySQL 拉全量快照” |
| `DomainRebuildLoaderRegistry` | 管理所有重建 loader |
| `CacheRebuildCoordinationSupport` | 管理域级锁和脏标记 |
| `CacheRebuildReport` | 输出结构化重建报告 |

### 3.5 启动校准层

| 类 | 作用 |
| --- | --- |
| `CacheBootReconcileRunner` | 应用启动完成后自动触发 boot 校准 |
| `CacheSyncServiceImpl.rebuildBootDomain` | boot 场景专用入口 |

### 3.6 缓存服务侧

| 类 | 作用 |
| --- | --- |
| `BeaconCacheWriteClient` | `beacon-webmaster` 调用 `beacon-cache` 的 Feign 客户端 |
| `CacheFeignAuthConfig` | 给 Feign 请求自动签名并统一记录错误 |
| `CacheAuthInterceptor` | 缓存服务侧验签和权限控制 |
| `CacheController` | 对外提供缓存写删、扫描、锁、脏标记消费等接口 |
| `NamespaceKeyResolver` | 逻辑 key 和物理 key 相互转换 |
| `CacheNamespaceProperties` | 定义当前 Redis 物理命名空间前缀 |
| `LocalRedisClient` | 真正执行 RedisTemplate 读写 |

---

## 4. 总体同步总流程

当前一次缓存同步的大链路如下：

```text
业务 Controller / 页面
    |
    v
业务 Service 写 MySQL
    |
    v
CacheSyncRuntimeExecutor
    |
    |-- 有事务 -> afterCommit
    |-- 无事务 -> 立即执行
    |-- 如果同域正在重建 -> 只记脏标记，跳过直接写 Redis
    v
CacheSyncServiceImpl
    |
    |-- normalizeDomain
    |-- requireDomainContract
    |-- buildKey
    |-- resolve payload
    |-- choose Hash / Set / String write
    v
BeaconCacheWriteClient (Feign)
    |
    |-- CacheFeignAuthConfig 自动附加签名头
    v
beacon-cache / CacheController
    |
    |-- CacheAuthInterceptor 验签鉴权
    |-- NamespaceKeyResolver 逻辑 key -> 物理 key
    |-- LocalRedisClient 落库
    v
Redis
```

这条主链路外侧还有两条修复链路：

1. **手工重建**：管理员主动触发，按域从 MySQL 全量回灌 Redis
2. **启动校准**：应用启动时自动按配置对有限域执行一次重建

---

## 5. `client_business` 运行时同步详解

下面以 `client_business` 为例，把“数据如何从 MySQL 走到 Redis”完整展开。

假设当前客户业务配置的 `apiKey = ak_1001`。

### 5.1 运行时新增：`save(...)`

入口类：

1. `ClientBusinessServiceImpl.save(...)`

当前代码步骤：

1. 页面或控制器提交客户业务配置
2. `ClientBusinessServiceImpl.save(...)` 先补齐默认值、时间字段、删除标记
3. 调用 `clientBusinessMapper.insertSelective(...)` 把数据写入 MySQL
4. 写库成功后，再执行一次 `clientBusinessMapper.selectByPrimaryKey(...)` 读取最新行
5. 调用 `CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)`
6. 如果当前有事务，同步动作会挂到 `afterCommit`
7. 事务真正提交后，执行 `cacheSyncService.syncUpsert("client_business", latestEntity)`
8. `CacheSyncServiceImpl.syncUpsert(...)` 先做 3 件事：
   - `normalizeDomain("client_business")`
   - `requireDomainContract("client_business")`
   - `buildKey("client_business", latestEntity)`
9. `buildKey(...)` 内部进一步调用 `CacheKeyBuilder.clientBusinessByApiKey(apiKey)`，得到逻辑 key：
   - `client_business:ak_1001`
10. `resolveClientBusinessPayload(...)` 把 `ClientBusiness` 实体转成 Hash 写入 payload
11. 调用 `BeaconCacheWriteClient.hmset("client_business:ak_1001", payload)`
12. `CacheFeignAuthConfig` 自动给请求附加：
   - `X-Cache-Caller`
   - `X-Cache-Timestamp`
   - `X-Cache-Sign`
13. `beacon-cache` 收到请求后，`CacheAuthInterceptor.preHandle(...)` 先验签和鉴权
14. `CacheController.hmset(...)` 调用 `NamespaceKeyResolver.toPhysicalKey(...)`
15. 假设当前命名空间前缀是 `beacon:dev:beacon-cloud:cz:`，最终物理 key 为：
   - `beacon:dev:beacon-cloud:cz:client_business:ak_1001`
16. `LocalRedisClient.hSet(...)` 把整份 Hash 写入 Redis

典型 payload 形态可以理解为：

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

1. `client_business` 当前走的是 Hash 覆盖写，Redis 里的字段基本就是 `ClientBusiness` 实体转成的字段集合
2. 当前实现不是手工摘几个字段组装，而是先把实体转成 Map，再强校验 `apiKey/apikey` 必须存在

对应类：

1. `ClientBusinessServiceImpl`
2. `CacheSyncRuntimeExecutor`
3. `CacheSyncServiceImpl`
4. `CacheKeyBuilder`
5. `BeaconCacheWriteClient`
6. `CacheFeignAuthConfig`
7. `CacheAuthInterceptor`
8. `CacheController`
9. `NamespaceKeyResolver`
10. `LocalRedisClient`

### 5.2 运行时更新：`update(...)`

入口类：

1. `ClientBusinessServiceImpl.update(...)`

当前更新链路比新增多一步“旧 key 处理”。

当前代码步骤：

1. 更新前先查询 `before = clientBusinessMapper.selectByPrimaryKey(id)`
2. 执行 `updateByPrimaryKeySelective(...)` 更新 MySQL
3. 更新后再查询 `latest = clientBusinessMapper.selectByPrimaryKey(id)`
4. 如果 `before.apikey` 和 `latest.apikey` 不同，说明逻辑 key 已发生变化
5. 这时会先注册一次：
   - `cacheSyncService.syncDelete("client_business", before)`
6. 再注册一次：
   - `cacheSyncService.syncUpsert("client_business", latest)`

所以如果 `apiKey` 改了，链路是：

```text
旧 key 删除：client_business:oldApiKey
新 key 写入：client_business:newApiKey
```

这一步的意义是：

1. 避免 Redis 留下“已经失效的旧 apiKey 对应配置”
2. 保证查询口径只剩一份最新值

### 5.3 运行时删除：`deleteBatch(...)`

入口类：

1. `ClientBusinessServiceImpl.deleteBatch(...)`

当前步骤：

1. 先把 MySQL 记录逻辑删除
2. 对每个已删除的有效客户配置，注册一次：
   - `cacheSyncService.syncDelete("client_business", existing)`
3. `syncDelete(...)` 会构建逻辑 key `client_business:{apikey}`
4. 再调用 `BeaconCacheWriteClient.delete(key)` 删除 Redis key

### 5.4 `client_business` 运行时链路中的关键方法

| 方法 | 作用 |
| --- | --- |
| `ClientBusinessServiceImpl.save` | 写 MySQL 后注册新增同步 |
| `ClientBusinessServiceImpl.update` | 处理“旧 key 删除 + 新 key 写入” |
| `ClientBusinessServiceImpl.deleteBatch` | 处理逻辑删除后的缓存失效 |
| `CacheSyncRuntimeExecutor.runAfterCommitOrNow` | 控制事务提交后执行 |
| `CacheSyncServiceImpl.syncUpsert` | 同步入口 |
| `CacheSyncServiceImpl.buildCurrentMainlineKey` | 构建逻辑 key |
| `CacheSyncServiceImpl.resolveClientBusinessPayload` | 构建 Hash payload |
| `CacheController.hmset` | 缓存服务 Hash 覆盖写入口 |

### 5.5 `client_business` 运行时同步时序图

```text
ClientBusinessServiceImpl.save/update
    -> clientBusinessMapper.insert/update (MySQL)
    -> clientBusinessMapper.selectByPrimaryKey (读取最新真源)
    -> CacheSyncRuntimeExecutor.runAfterCommitOrNow
    -> CacheSyncServiceImpl.syncUpsert
    -> CacheKeyBuilder.clientBusinessByApiKey
    -> BeaconCacheWriteClient.hmset
    -> CacheController.hmset
    -> NamespaceKeyResolver.toPhysicalKey
    -> LocalRedisClient.hSet
    -> Redis Hash: beacon:dev:beacon-cloud:cz:client_business:ak_1001
```

---

## 6. `client_business` 手工重建详解

运行时同步解决的是“系统正在运行时”的变化。  
手工重建解决的是“Redis 已经不可信”。

### 6.1 手工重建入口

入口类：

1. `CacheRebuildController`
2. `CacheRebuildServiceImpl`
3. `CacheSyncServiceImpl.rebuildDomain(...)`

管理员调用：

1. `POST /admin/cache/rebuild?domain=client_business`

### 6.2 单域重建的完整步骤

以 `client_business` 为例，当前完整步骤如下：

1. `CacheRebuildController.rebuild(...)` 先做登录态校验和管理员角色校验
2. 调用 `CacheRebuildService.rebuildDomain("client_business")`
3. `CacheSyncServiceImpl.rebuildDomain(...)` 先校验：
   - `sync.enabled`
   - `sync.manual.enabled`
   - 域是否属于当前主线范围
   - 域是否允许手工重建
   - 该域是否已注册 loader
4. 进入 `rebuildSingleDomain("client_business")`
5. 生成锁持有者 token
6. 调用 `CacheRebuildCoordinationSupport.tryAcquireRebuildLock(...)`
7. 实际上它会通过 `BeaconCacheWriteClient.setIfAbsent(...)` 在 Redis 里写一把域级锁：
   - `cache:rebuild:client_business`
8. 抢锁成功后，执行 `executeRebuildPass(...)`
9. `cleanupExistingDomainKeys(...)` 先清旧 key：
   - 从契约里拿到逻辑 key 模式 `client_business:{apikey}`
   - 转成扫描 pattern：`client_business:*`
   - 调用 `BeaconCacheWriteClient.keys(...)`
   - `CacheController.keys(...)` 再把逻辑 pattern 转成物理 pattern
   - 扫描出当前命名空间下所有 `client_business:*`
   - 调用 `deleteBatch(...)` 批量删除
10. `ClientBusinessDomainRebuildLoader.loadSnapshot()` 从 MySQL 查询全部有效客户业务数据
11. 对每一行 `ClientBusiness`：
   - 再次走 `buildKey(...)`
   - 再次走 `doUpsert(...)`
   - 再次调用 `BeaconCacheWriteClient.hmset(...)`
12. 重建过程中如果有新的运行时写请求打到同域，`CacheSyncRuntimeExecutor.shouldMarkDirtyAndSkip(...)` 会只写脏标记：
   - `cache:rebuild:dirty:client_business`
13. 第一轮重建结束后，`consumeDirty("client_business")` 会检查是否有脏标记
14. 如果有，就再执行一次 `executeRebuildPass(...)`，这就是 dirty replay
15. 完成后调用 `releaseRebuildLock(...)`
16. 安全释放锁不是直接删 key，而是调用 `deleteIfValueMatches(...)`，只有 token 匹配才释放
17. 返回 `CacheRebuildReport`

### 6.3 `client_business` 如何从 MySQL 拿到数据

这一步不是在 `CacheSyncServiceImpl` 里直接写 SQL，而是通过专门的 loader 完成。

入口类：

1. `ClientBusinessDomainRebuildLoader`

当前职责：

1. 查询所有未删除客户业务记录
2. 按 `id asc` 取快照
3. 返回 `List<Object>`，实际元素是 `ClientBusiness`

也就是说，`client_business` 在重建链路里“从 MySQL 拿数据”的位置非常明确：

1. SQL / Mapper 负责从 MySQL 拉真源快照
2. Loader 负责把“某一类域该怎么查”封装起来
3. 重建引擎只消费快照，不直接关心 SQL 细节

然后重建引擎统一接管后续流程：

1. 构建 key
2. 解析 payload
3. 写入 Redis

### 6.4 为什么手工重建不会再维护第二套写入逻辑

因为重建时最终仍然复用：

1. `buildKey(...)`
2. `doUpsert(...)`
3. `resolveClientBusinessPayload(...)`

这意味着：

1. 运行时同步和重建使用同一套 key 规则
2. 运行时同步和重建使用同一套 Redis 结构写法
3. 重建不会因为自己重写了一套逻辑而产生第二种口径

---

## 7. `client_business` 启动校准详解

启动校准不是新造一套逻辑，而是“在应用启动时自动触发一次重建”。

### 7.1 启动入口

入口类：

1. `CacheBootReconcileRunner`

启动后步骤：

1. 检查 `sync.enabled`
2. 检查 `sync.boot.enabled`
3. 读取 `sync.boot.domains`
4. 如果没显式配置，就从 `CacheDomainRegistry.currentBootReconcileDomainCodes()` 取默认 boot 域
5. 再过滤：
   - 域已注册
   - 域属于当前主线范围
   - 域契约允许 boot rebuild
   - 该域已经注册 loader
6. 对符合条件的域逐个调用 `rebuildBootDomain(domain)`

### 7.2 `client_business` 在 boot 阶段具体怎么走

当 `client_business` 被选中时，链路为：

```text
CacheBootReconcileRunner.run
    -> resolveRequestedDomains
    -> resolveExecutableDomains
    -> CacheRebuildService.rebuildBootDomain("client_business")
    -> CacheSyncServiceImpl.rebuildBootDomain("client_business")
    -> rebuildSingleDomain("client_business")
    -> 后续流程与手工重建完全一致
```

所以当前代码的原则是：

1. **启动校准复用手工重建底层引擎**
2. **入口语义不同，但重建规则相同**

### 7.3 为什么 boot 失败不阻塞启动

当前定位是：

1. 启动校准是“尽力修复”
2. 不是“缓存失败就不让系统启动”

所以：

1. 单域失败会记错误日志并继续后续域
2. 顶层失败会输出失败汇总
3. 不会把异常继续抛给 Spring Boot

---

## 8. `client_channel` 和 `client_balance` 两个特殊域

### 8.1 `client_channel`：集合域整组重建

`client_channel` 不是单个对象，而是“某个客户的一整组通道路由成员”。

当前做法：

1. `ClientChannelServiceImpl` 不直接增量改 Redis 成员
2. 每次保存、更新、删除绑定关系后，都重新构造当前客户的全量路由快照
3. payload 固定包含：
   - `clientId`
   - `members`
4. `CacheSyncServiceImpl` 对它统一执行：
   - 删除旧 Set
   - 再按全量成员 `sadd`

这样可以避免：

1. 老成员残留
2. 局部更新后集合状态不完整

### 8.2 `client_balance`：高风险镜像域

`client_balance` 的链路比普通域更严格。

当前入口：

1. `InternalBalanceController`
2. `BalanceCommandService`
3. `BalanceCommandServiceImpl`

当前原则：

1. 余额真源是 MySQL `client_balance`
2. 扣费、充值、调账都必须先走原子 SQL
3. 成功后刷新 `client_balance`
4. 同时刷新 `client_business`

对应的原子 SQL 在：

1. `ClientBalanceMapper.xml`
   - `debitBalanceAtomic`
   - `rechargeBalanceAtomic`
   - `adjustBalanceAtomic`

当前删除策略是 `OVERWRITE_ONLY`，所以：

1. `syncDelete("client_balance", ...)` 不会真的删 key
2. 当前口径是“用最新真源值覆盖镜像缓存”

---

## 9. 缓存服务侧到底提供了什么能力

`beacon-webmaster` 不直接拿 `RedisTemplate` 写 Redis。  
当前所有真正落 Redis 的动作，都通过 `beacon-cache` 暴露的接口完成。

### 9.1 核心写删接口

| 接口 | 当前用途 |
| --- | --- |
| `POST /cache/hmset/{key}` | Hash 覆盖写，如 `client_business`、`channel`、`client_balance` |
| `POST /cache/set/{key}` | String 覆盖写，如 `black`、`transfer` |
| `POST /cache/sadd/{key}` | 对象 Set 写入，如 `client_channel`、`client_sign`、`client_template` |
| `POST /cache/saddstr/{key}` | 字符串 Set 写入，如 `dirty_word` |
| `DELETE /cache/delete/{key}` | 删除单个逻辑 key |
| `POST /cache/delete/batch` | 批量删除逻辑 key |
| `GET /cache/keys` | 按逻辑 pattern 扫描当前命名空间 key |

### 9.2 重建协调相关接口

| 接口 | 当前用途 |
| --- | --- |
| `POST /cache/setnx/{key}` | 获取域级重建锁 |
| `DELETE /cache/pop/{key}` | 原子读取并删除脏标记 |
| `DELETE /cache/delete-if-match/{key}` | 只有锁 token 匹配时才释放锁 |

### 9.3 命名空间转换

当前缓存服务侧统一做命名空间隔离：

1. `NamespaceKeyResolver.toPhysicalKey(...)`：逻辑 key -> 物理 key
2. `NamespaceKeyResolver.toLogicalKey(...)`：物理 key -> 逻辑 key
3. `NamespaceKeyResolver.toPhysicalPattern(...)`：逻辑 pattern -> 物理 pattern

因此业务侧始终只需要关心：

1. `client_business:ak_1001`

不需要关心：

1. `beacon:dev:beacon-cloud:cz:client_business:ak_1001`

### 9.4 为什么要校验命名空间一致

`CacheSyncProperties.validate()` 会校验：

1. `sync.redis.namespace`
2. `cache.namespace.fullPrefix`

两者必须一致。

这样可以防止：

1. `webmaster` 认为自己在写 `dev` 前缀
2. `cache` 实际落到了另一个前缀

一旦不一致，应用启动阶段就会直接失败，避免静默写错空间。

---

## 10. `webmaster -> cache` 调用为什么是安全的

当前缓存服务不是开放式内部 API，而是带签名校验。

### 10.1 发起侧

类：

1. `CacheFeignAuthConfig`

作用：

1. 读取 `cache.client.auth.caller`
2. 读取 `cache.client.auth.secret`
3. 使用 `CacheAuthSignUtil` 构造签名 payload
4. 自动注入 3 个请求头：
   - `X-Cache-Caller`
   - `X-Cache-Timestamp`
   - `X-Cache-Sign`

### 10.2 接收侧

类：

1. `CacheAuthInterceptor`

作用：

1. 校验 caller / timestamp / sign 是否齐全
2. 校验时间戳是否过期
3. 校验调用方密钥是否存在
4. 校验签名是否一致
5. 按路径和方法解析权限：
   - `READ`
   - `WRITE`
   - `KEYS`
   - `TEST`

所以当前链路不仅有一致性设计，也有调用边界控制。

---

## 11. 当前保证了什么，没有保证什么

### 11.1 当前保证了什么

1. MySQL 成功提交后，Redis 会在正常情况下被及时刷新
2. 事务未提交时不会提前写 Redis
3. `client_channel` 这类集合域不会走危险的局部增量更新
4. `client_balance` 这类高风险域不会默认删 key
5. Redis 脏了时，可以按域从 MySQL 全量恢复
6. 启动后可以自动对有限域做一次校准
7. 重建期间同域运行时写入不会直接打断重建，而是会被收敛为脏标记补跑

### 11.2 当前没有保证什么

1. 没有实现 MySQL 和 Redis 的两阶段提交
2. 运行时同步失败时，Redis 不会自动立刻强制补偿成功
3. 兼容保留域当前完整度不如主线域一致
4. 这不是 CDC / Outbox / Binlog 级别的平台化同步系统

因此当前正确理解应该是：

> 运行时同步负责“平时别漂”，手工重建和启动校准负责“漂了能修回来”。

---

## 12. 推荐阅读顺序

如果你想继续按代码往下读，建议顺序如下：

1. `CacheDomainRegistry`
2. `CacheKeyBuilder`
3. `CacheSyncRuntimeExecutor`
4. `CacheSyncServiceImpl`
5. `ClientBusinessServiceImpl`
6. `BalanceCommandServiceImpl`
7. `CacheRebuildCoordinationSupport`
8. `ClientBusinessDomainRebuildLoader`
9. `CacheBootReconcileRunner`
10. `CacheController`
11. `NamespaceKeyResolver`
12. `CacheAuthInterceptor`

---

## 13. 本文小结

当前缓存一致性体系可以浓缩成 5 句话：

1. 先用 `CacheDomainRegistry + CacheDomainContract` 把规则冻住
2. 正常业务变更时，先写 MySQL，再通过 `afterCommit` 同步 Redis
3. 集合域走“删后全量重建”，余额域走“MySQL 原子更新后刷新镜像”
4. Redis 脏了时，通过 loader 从 MySQL 拉快照重建
5. 启动校准和手工重建共用同一套底层引擎，只是触发时机不同

如果只看 `client_business` 这一条链，它已经具备：

1. 运行时同步
2. 旧 key 切换处理
3. 手工重建
4. 启动校准
5. 命名空间隔离
6. 内部调用验签

这也是当前整套缓存一致性设计里最适合拿来做讲解和答辩展示的示例域。

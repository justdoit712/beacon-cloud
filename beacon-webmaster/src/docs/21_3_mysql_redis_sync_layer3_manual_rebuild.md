# 第三层：手工重建（架构说明）

文档定位：修复层 / 重建引擎层  
适用对象：开发 / 排障 / 答辩  
验证基线：当前仓库代码静态核对  
最后核对日期：2026-03-24

---

## 0. 本层在整套架构中的位置

第三层解决的是：

> 当 Redis 已经不可信时，怎样从 MySQL 真源把缓存重新修回来。

典型场景：

1. 停机期间手工改库
2. 运行时同步失败导致历史漂移
3. 演示、联调、排障前希望强制校准

第三层不是普通业务写入通道，而是“维修工具层”。

---

## 1. 当前第三层主要类

| 类 | 作用 |
| --- | --- |
| `CacheRebuildController` | 对外提供手工重建入口 |
| `CacheRebuildService` | 重建门面接口 |
| `CacheRebuildServiceImpl` | 委托到底层重建引擎 |
| `CacheSyncServiceImpl.rebuildDomain` | 手工重建入口实现 |
| `DomainRebuildLoader` | 定义某个域如何从 MySQL 拉全量快照 |
| `DomainRebuildLoaderRegistry` | 管理所有 loader |
| `CacheRebuildCoordinationSupport` | 管理重建锁和脏标记 |
| `CacheRebuildReport` | 输出结构化重建报告 |

当前主线 loader 包括：

1. `ClientBusinessDomainRebuildLoader`
2. `ChannelDomainRebuildLoader`
3. `ClientChannelDomainRebuildLoader`
4. `ClientBalanceDomainRebuildLoader`

---

## 2. 手工重建入口长什么样

当前入口在：

1. `CacheRebuildController`

接口形态：

1. `POST /admin/cache/rebuild?domain=client_business`
2. `POST /admin/cache/rebuild?domain=ALL`

当前入口职责：

1. 校验 `domain` 参数
2. 获取当前登录用户
3. 校验是否具备管理员角色
4. 调用 `CacheRebuildService.rebuildDomain(domain)`
5. 返回 `CacheRebuildReport`

---

## 3. 当前 `ALL` 到底是什么意思

`ALL` 不是“系统里所有缓存域”。

当前代码里，`ALL` 的含义是：

1. 域属于当前主线范围
2. 域允许进入手工重建范围
3. 域已注册对应 loader

因此：

1. `client_business`、`client_channel`、`channel`、`client_balance` 当前会进入 `ALL`
2. `client_sign`、`client_template`、`black`、`dirty_word`、`transfer` 当前不会因为 `ALL` 自动进入

---

## 4. 单域重建的完整流程

当前单域重建的核心方法在：

1. `CacheSyncServiceImpl.rebuildDomain(...)`
2. `CacheSyncServiceImpl.rebuildSingleDomain(...)`
3. `CacheSyncServiceImpl.executeRebuildPass(...)`

完整流程如下：

1. 接收到手工重建请求
2. 校验：
   - `sync.enabled`
   - `sync.manual.enabled`
   - 域是否已注册
   - 域是否属于当前主线范围
   - 域是否允许进入 manual 范围
   - 域是否已注册 loader
3. 进入 `rebuildSingleDomain(domain)`
4. 生成锁持有者 token
5. 尝试获取域级重建锁
6. 初始化 `CacheRebuildReport`
7. 执行第一轮 `executeRebuildPass(...)`
8. 如果重建期间发现有脏标记，再补跑一轮
9. 汇总报告状态
10. 释放锁

这里最重要的一点是：

> 第三层不会自己再实现一套 Redis 写入逻辑，而是复用第二层已经存在的 `buildKey + doUpsert`。

---

## 5. `client_business` 手工重建端到端详解

下面以 `client_business` 为例，把重建过程完整展开。

### 5.1 入口到引擎

当前调用链：

```text
CacheRebuildController.rebuild("client_business")
    -> CacheRebuildService.rebuildDomain("client_business")
    -> CacheSyncServiceImpl.rebuildDomain("client_business")
    -> rebuildSingleDomain("client_business")
```

### 5.2 域级锁是怎样拿的

进入 `rebuildSingleDomain(...)` 后，第一件事是抢锁。

当前执行方式：

1. 生成本次重建专属 `lockToken`
2. 调用 `CacheRebuildCoordinationSupport.tryAcquireRebuildLock(domain, lockToken)`
3. 该方法底层调用：
   - `BeaconCacheWriteClient.setIfAbsent(lockKey, token, ttlSeconds)`
4. 最终在 Redis 里尝试写入：
   - `cache:rebuild:client_business`

锁的作用是：

1. 同一时刻同一域只能有一个重建流程进入
2. 多实例部署时也能跨实例生效

### 5.3 旧 key 是怎么清理的

`executeRebuildPass(...)` 的第一步不是查 MySQL，而是先清旧 key。

当前流程：

1. 从 `CacheDomainContract` 里取逻辑 key 模式：
   - `client_business:{apikey}`
2. 转成扫描 pattern：
   - `client_business:*`
3. 调用：
   - `BeaconCacheWriteClient.keys("client_business:*", count)`
4. 到缓存服务侧：
   - `CacheController.keys(...)`
   - `NamespaceKeyResolver.toPhysicalPattern(...)`
5. 扫描出当前命名空间下命中的全部 `client_business:*`
6. 调用：
   - `BeaconCacheWriteClient.deleteBatch(oldKeys)`
7. 由缓存服务批量删掉这些逻辑 key 对应的物理 key

这一步的意义是：

1. 先把该域当前残留的历史 key 清空
2. 再重新按真源快照写入
3. 避免新旧数据混在一起

### 5.4 `client_business` 如何从 MySQL 拉快照

这一层不直接写 SQL，而是委托给专门的 loader。

当前 loader：

1. `ClientBusinessDomainRebuildLoader`

当前职责：

1. 查询全部未删除客户业务配置
2. 按 `id asc` 返回快照
3. 返回 `List<Object>`，实际元素是 `ClientBusiness`

所以链路是：

```text
ClientBusinessDomainRebuildLoader.loadSnapshot()
    -> ClientBusinessMapper.selectByExample(...)
    -> 返回 List<ClientBusiness>
```

### 5.5 快照是怎样重新写回 Redis 的

当 loader 返回 `List<ClientBusiness>` 后，重建引擎会逐项处理。

对每一个 `ClientBusiness`，当前执行流程是：

1. `buildKey("client_business", payload)`
2. 内部调用 `CacheKeyBuilder.clientBusinessByApiKey(apiKey)`
3. 得到逻辑 key：
   - `client_business:ak_1001`
4. `doUpsert("client_business", key, payload)`
5. `doCurrentMainlineUpsert(...)` 识别到它是 Hash 域
6. `resolveClientBusinessPayload(...)` 转成 Map
7. `BeaconCacheWriteClient.hmset(key, payload)`
8. `beacon-cache` 收到后把逻辑 key 转成物理 key 落 Redis

也就是说，手工重建最终写 Redis 时，仍然和运行时同步使用同一套写法。

---

## 6. 为什么第三层一定要复用第二层的写入规则

如果第三层自己再写一套 Redis 回灌逻辑，会有三个风险：

1. key 构造口径和运行时同步不一致
2. Hash / Set / String 的写法和运行时同步不一致
3. 修复出来的结果和日常写入结果不一致

当前代码避免这个问题的方式是：

1. 运行时同步和重建都走 `buildKey(...)`
2. 运行时同步和重建都走 `doUpsert(...)`
3. 重建不直接接触 RedisTemplate，只通过同一套缓存服务接口落库

---

## 7. 当前四个主线 loader 各自怎么取快照

### 7.1 `ClientBusinessDomainRebuildLoader`

作用：

1. 查询所有有效客户业务配置
2. 返回 `List<ClientBusiness>`

### 7.2 `ChannelDomainRebuildLoader`

作用：

1. 查询所有有效通道
2. 返回 `List<Channel>`

### 7.3 `ClientChannelDomainRebuildLoader`

这是当前最典型的集合型 loader。

它不是返回单条绑定记录，而是：

1. 先查全部有效 `clientId`
2. 再查这些客户的全量路由成员
3. 按 `clientId` 分组
4. 组装成：
   - `clientId`
   - `members`

这样重建引擎拿到的不是零散成员，而是“某个客户完整的一组成员快照”。

### 7.4 `ClientBalanceDomainRebuildLoader`

作用：

1. 查询全部有效余额记录
2. 返回 `List<ClientBalance>`

这说明当前 `client_balance` 已经正式接入第三层重建，而不是只停留在规划阶段。

---

## 8. Redis 机制：为什么第三层需要锁

### 8.1 如果没有锁会怎样

假设 `client_business` 正在重建：

1. 重建流程刚删掉了旧 key
2. 还没全部写回时
3. 另一个实例又开始重建同一域

结果会变成：

1. 一部分 key 来自第一次重建
2. 一部分 key 来自第二次重建
3. 一部分 key 可能又被运行时同步插进来

最终 Redis 状态不可控。

### 8.2 当前锁的实现

实现类：

1. `CacheRebuildCoordinationSupport`

当前机制：

1. 锁 key：`cache:rebuild:{domain}`
2. value：本次重建持有者 token
3. TTL：默认 300 秒
4. 抢锁：`setIfAbsent`
5. 释放：`deleteIfValueMatches`

### 8.3 为什么释放锁还要校验 token

如果只是简单 `delete(lockKey)`，会有风险：

1. A 拿到锁
2. B 误删这把锁
3. C 又能重新进入同域重建

所以当前必须：

1. 只有 value 和自己 token 匹配时才能释放锁

---

## 9. Redis 机制：什么是脏标记补跑

### 9.1 为什么要有脏标记

重建的典型步骤是：

1. 清旧 key
2. 从 MySQL 拉快照
3. 再回灌 Redis

如果这时候运行时同步还继续直接写 Redis，就会把重建过程打断。

### 9.2 当前实现怎么处理

当前做法不是继续写 Redis，而是：

1. 运行时同步检测到同域正在重建
2. 不直接写 Redis
3. 只写一个脏标记：
   - `cache:rebuild:dirty:{domain}`

### 9.3 重建结束后怎么补跑

第一轮重建完成后，当前引擎会调用：

1. `consumeDirty(domain)`

如果发现有脏标记，就说明：

1. 重建期间又有新的业务变更发生

于是当前引擎会：

1. 再执行一次 `executeRebuildPass(...)`

这就是 dirty replay。

---

## 10. 当前第三层的报告结构

当前结果模型：

1. `CacheRebuildReport`

当前关键字段包括：

1. `traceId`
2. `trigger`
3. `domain`
4. `startAt`
5. `endAt`
6. `attemptedKeys`
7. `successCount`
8. `failCount`
9. `failedKeys`
10. `dirtyReplay`
11. `operator`

这意味着第三层不是只打日志，而是会返回一份结构化结果，能被：

1. 控制器直接返回
2. 启动校准复用
3. 单元测试断言

---

## 11. 本层小结

第三层可以总结成 5 句话：

1. 当 Redis 不可信时，它负责按域从 MySQL 全量恢复
2. 它先清旧 key，再拉快照，再复用第二层写法回灌 Redis
3. 它使用 Redis 域级锁避免同域并发重建
4. 它使用脏标记补跑避免重建期间遗漏最新变更
5. 它是手工修复和启动校准共同依赖的底层重建引擎

如果把整套一致性方案比作一栋楼，第三层就是：

> 维修层和修复引擎层。

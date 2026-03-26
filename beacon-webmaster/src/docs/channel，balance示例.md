# channel、balance 示例

文档类型：示例讲解  
适用对象：刚开始理解缓存一致性链路的开发同学  
验证基线：当前仓库代码静态核对  
最后核对日期：2026-03-26

---

## 0. 这份文档解决什么问题

`channel` 和 `client_balance` 是当前主线缓存域里两种非常典型、但复杂度不同的代表：

1. `channel`：最适合入门的普通主线域
2. `client_balance`：最典型的高风险主线域

这份文档不从“四层架构抽象”入手，而是直接从这两个域出发，回答下面几个问题：

1. 它们是如何在规则层被注册出来的
2. 运行时写 MySQL 后，Redis 是如何被刷新的
3. `syncUpsert -> doUpsert -> doCurrentMainlineUpsert` 这几层各自负责什么
4. 手工重建和启动校准又是如何复用同一套引擎的
5. 为什么 `balance` 明显比 `channel` 更特殊

---

## 1. 先记住一个总原则

当前主线缓存一致性链路，统一遵循下面这个骨架：

```text
业务 Service
  -> 先写 MySQL
  -> CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)
  -> CacheSyncServiceImpl.syncUpsert/syncDelete(...)
  -> doUpsert(...)
  -> doCurrentMainlineUpsert(...) / doLegacyCompatibleUpsert(...)
  -> BeaconCacheWriteClient
  -> beacon-cache
  -> Redis
```

不同域的差异主要出现在 3 个地方：

1. 规则层契约不同
2. 业务入口不同
3. payload 与 Redis 写法不同

---

## 2. `channel` 示例

## 2.1 `channel` 先在规则层被注册出来

`channel` 不是在运行时临时决定怎么同步的，而是先在
`CacheDomainRegistry` 中被注册为一个主线缓存域。

静态初始化块会做三类事：

1. 通过 `registerCurrentMainlineContracts(...)` 和 `registerLegacyCompatibleContracts(...)`
   注册当前系统支持的域契约
2. 基于这些契约构建 `CONTRACT_MAP`
3. 再派生出几组范围：
   - `CURRENT_MAINLINE_DOMAIN_CODES`
   - `CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES`
   - `CURRENT_MANUAL_REBUILD_DOMAIN_CODES`
   - `CURRENT_BOOT_RECONCILE_DOMAIN_CODES`

对 `channel` 来说，它属于：

1. 主线域
2. manual 默认域
3. boot 默认域

## 2.2 `channel` 的契约是什么

在 `registerCurrentMainlineContracts(...)` 里，`channel` 会被注册成一个
`CacheDomainContract`，其核心规则是：

1. 域名：`channel`
2. 逻辑 key：`channel:{id}`
3. Redis 结构：`HASH`
4. 真源：`MYSQL`
5. 写入策略：`WRITE_THROUGH`
6. 删除策略：`DELETE_KEY`
7. 允许 boot 重建：`true`

这一步的意义是：

> 先把 `channel` 这个域的规则冻结下来，后面的运行时同步、手工重建、启动校准都按这个契约走。

## 2.3 运行时是谁触发 `channel` 的同步

`channel` 的运行时入口不是通用控制器，而是
`ChannelServiceImpl` 的业务方法：

1. `save(...)`
2. `update(...)`
3. `deleteBatch(...)`

以 `save(...)` 为例，当前链路是：

1. 进入 `ChannelServiceImpl.save(...)`
2. 做空值判断，补齐：
   - `id`
   - `created`
   - `updated`
   - `isDelete`
   - `isAvailable`
3. 调用 `channelMapper.insertSelective(...)` 写 MySQL
4. 写成功后，再查一次最新数据 `channelMapper.findById(...)`
5. 调用：

```java
cacheSyncRuntimeExecutor.runAfterCommitOrNow(
        () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CHANNEL, latest != null ? latest : channel),
        CacheDomainRegistry.CHANNEL,
        "upsert",
        safeEntityId(channel.getId())
);
```

关键点是：

1. 业务层不直接写 Redis
2. 业务层只是把“同步这个域”的动作交给统一同步执行器

## 2.4 `CacheSyncRuntimeExecutor` 在这里干什么

`CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)` 不是直接做缓存写入，它先决定：

1. 当前有没有事务
2. 如果有事务，就挂到 `afterCommit`
3. 如果没有事务，就立即执行

它解决的是：

> 保证 MySQL 真正提交成功后，再去刷 Redis，避免事务回滚导致脏缓存。

## 2.5 `channel` 的统一同步入口是什么

真正的运行时同步入口是：

1. `cacheSyncService.syncUpsert(...)`

进入 `CacheSyncServiceImpl` 后，链路会变成：

```text
ChannelServiceImpl
  -> CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)
  -> CacheSyncServiceImpl.syncUpsert(...)
  -> buildKey(...)
  -> doUpsert(...)
  -> doCurrentMainlineUpsert(...)
  -> cacheWriteClient.hmset(...)
  -> beacon-cache
  -> Redis
```

## 2.6 这几层方法分别干什么

### `syncUpsert(...)`

统一公开入口，负责：

1. 检查同步开关是否开启
2. 规范化 `domain`
3. 查询域契约
4. 构建逻辑 key
5. 调用内部真正写入逻辑

可以把它理解成：

> “我要同步新增或更新，请帮我统一处理。”

### `doUpsert(...)`

内部路由层，负责：

1. 判断当前域属于主线域还是兼容域
2. 主线域走 `doCurrentMainlineUpsert(...)`
3. 兼容域走 `doLegacyCompatibleUpsert(...)`

它解决的是：

> “这次 upsert 属于哪一类域？”

### `doCurrentMainlineUpsert(...)`

主线域具体执行层，负责：

1. 根据不同主线域决定真正的 Redis 写法

对于 `channel` 来说，当前逻辑是：

```java
if (CacheDomainRegistry.CHANNEL.equals(domain)) {
    cacheWriteClient.hmset(key, resolveChannelPayload(entityOrId));
    return;
}
```

这说明：

1. `channel` 在 Redis 里用的是 `HASH`
2. 写入前还会先走 `resolveChannelPayload(...)` 做一层 payload 适配
3. 最终调用 `beacon-cache` 的 `hmset`

因此更准确的说法是：

> `doCurrentMainlineUpsert(...)` 根据主线域名称，决定该域的 Redis 结构写法；对于 `channel`，最终是把数据转换成 Hash payload，再通过 `cacheWriteClient.hmset(...)` 写入 Redis。

## 2.7 为什么不把所有逻辑都塞进 `syncUpsert(...)`

因为这几个层次的职责不一样：

### `syncUpsert(...)`

面向业务层的统一门面，所有业务层都只需要知道它。

### `doUpsert(...)`

按“主线域 / 兼容域”分类，属于内部路由层。

### `doCurrentMainlineUpsert(...)`

按具体主线域做真正写法，属于内部执行层。

这样拆开后的好处是：

1. 业务层永远只依赖 `syncUpsert(...)`
2. 内部可以继续扩域，而不影响业务侧调用方式
3. 主线域和兼容域的处理逻辑能分开维护

## 2.8 `channel` 在重建和 boot 里怎么走

### 手工重建

`channel` 的 loader 是：

1. `ChannelDomainRebuildLoader`

它会：

1. 调用 `channelMapper.findAllActive()`
2. 从 MySQL 拉全部有效通道
3. 把全量快照交给重建引擎

### 启动校准

由于 `channel`：

1. 已注册
2. 属于主线域
3. 允许 boot rebuild
4. 有对应 loader

所以 boot 启动时，如果开启默认校准，它会自动参与。

## 2.9 一句话总结 `channel`

`channel` 先在 `CacheDomainRegistry` 中通过 `CacheDomainContract` 被注册为主线缓存域，同时进入 manual 和 boot 的默认范围。  
当控制层调用 `ChannelServiceImpl.save(...)` 时，业务层先补齐数据并写入 MySQL；写成功后，通过 `CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)` 注册一次运行时同步动作。  
真正执行时，会进入 `CacheSyncServiceImpl.syncUpsert(...)`，再经由 `doUpsert(...)` 判断它属于主线域，最终在 `doCurrentMainlineUpsert(...)` 中识别 `channel` 应按 Hash 写入 Redis，并调用 `cacheWriteClient.hmset(...)` 完成同步。

---

## 3. `client_balance` 示例

## 3.1 `client_balance` 也先在规则层被注册出来

和 `channel` 一样，`client_balance` 也是先在 `CacheDomainRegistry` 中被注册出来的主线域。

它同样属于：

1. 主线域
2. manual 默认域
3. boot 默认域

但它不是普通主线域，而是一个**高风险主线域**。

## 3.2 `client_balance` 的契约有什么特别之处

它的契约核心规则是：

1. 域名：`client_balance`
2. 逻辑 key：`client_balance:{clientId}`
3. Redis 结构：`HASH`
4. 真源：`MYSQL`
5. 写入策略：`MYSQL_ATOMIC_UPDATE_THEN_REFRESH`
6. 删除策略：`OVERWRITE_ONLY`
7. 允许 boot 重建：`true`

这里最关键的两点是：

### 写入策略不是普通 `WRITE_THROUGH`

而是：

1. `MYSQL_ATOMIC_UPDATE_THEN_REFRESH`

它表达的意思是：

> 余额变更不能像普通配置域一样“写完数据库再顺手刷缓存”，而必须先以 MySQL 原子更新为准，再刷新 Redis 镜像。

### 删除策略不是 `DELETE_KEY`

而是：

1. `OVERWRITE_ONLY`

它表达的意思是：

> 余额域默认不允许删 key，避免删 key 后出现缓存空窗。

## 3.3 `balance` 的业务入口不是普通 CRUD

这是它和 `channel` 最大的区别。

`channel` 的业务入口是：

1. `ChannelServiceImpl.save/update/deleteBatch`

而 `balance` 的业务入口是：

1. `InternalBalanceController`
2. `BalanceCommandService`
3. `BalanceCommandServiceImpl`

当前统一命令入口有 3 个：

1. `debitAndSync(...)`
2. `rechargeAndSync(...)`
3. `adjustAndSync(...)`

这说明：

> 余额变更必须收口到专门的命令服务中，不能像普通配置那样直接做随意 CRUD。

## 3.4 为什么 `balance` 必须先写 MySQL

看 `ClientBalanceMapper.xml` 可以发现，余额变更不是“先查再改”，而是直接用原子 SQL：

1. `debitBalanceAtomic`
2. `rechargeBalanceAtomic`
3. `adjustBalanceAtomic`

这说明了几件事：

1. 余额最终正确性必须由 MySQL 保证
2. 不能先读 Redis 再算再写
3. 不能走普通配置域的心态
4. 必须保证原子扣减、原子充值、原子调账

所以 `balance` 的前半段链路不是：

1. 改对象
2. 保存数据库
3. 刷缓存

而是：

1. 发余额命令
2. 执行 MySQL 原子更新
3. 回查最新真源快照
4. 刷 Redis 镜像

## 3.5 `BalanceCommandServiceImpl` 具体怎么走

以 `debitAndSync(...)` 为例：

### 第一步：归一化命令

`normalizeDebitCommand(...)` 负责：

1. 校验 `clientId`
2. 校验 `fee`
3. 补齐默认 `amountLimit`

### 第二步：执行 MySQL 原子扣减

调用：

1. `clientBalanceMapper.debitBalanceAtomic(...)`

如果更新失败：

1. 可能是余额不足
2. 也可能是客户不存在

这时不会去刷 Redis，而是直接走失败分支。

### 第三步：回查最新真源快照

更新成功后，会再查询：

1. `requireLatestClientBalance(clientId)`
2. `requireLatestClientBusiness(clientId)`

这一点特别重要，因为它说明：

> 刷缓存时使用的是 MySQL 提交成功后的最新快照，而不是命令入参。

### 第四步：注册双域刷新

进入：

1. `scheduleBalanceDoubleRefresh(...)`

当前会注册两次运行时同步动作：

1. `syncUpsert(CLIENT_BALANCE, clientBalancePayload)`
2. `syncUpsert(CLIENT_BUSINESS, latestBusiness)`

这说明 `balance` 最特殊的地方在于：

> 余额变化后，不只刷新 `client_balance`，还要一起刷新 `client_business`。

## 3.6 为什么 `balance` 要双域刷新

`channel` 的同步是单域同步：

1. 改 `channel`
2. 刷 `channel`

但 `balance` 是：

1. 改 `client_balance`
2. 刷 `client_balance`
3. 再刷 `client_business`

原因是：

1. `client_balance` 是余额镜像缓存
2. `client_business` 也承载客户整体业务状态视图的一部分

所以余额变化后，系统希望：

1. 余额缓存保持新鲜
2. 客户业务缓存也保持新鲜

## 3.7 进入统一同步框架后，`balance` 又和 `channel` 汇合了

虽然 `balance` 的业务入口和前置 SQL 完全不同，但一旦进入缓存同步，它又重新回到统一框架：

```text
BalanceCommandServiceImpl
  -> scheduleBalanceDoubleRefresh(...)
  -> CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)
  -> CacheSyncServiceImpl.syncUpsert(...)
  -> doUpsert(...)
  -> doCurrentMainlineUpsert(...)
  -> cacheWriteClient.hmset(...)
  -> beacon-cache
  -> Redis
```

也就是说：

1. 前半段特殊：命令服务 + 原子 SQL
2. 后半段统一：复用同一套同步门面和内部路由

## 3.8 `balance` 在 `CacheSyncServiceImpl` 里怎么落到 Redis

当前关键点有两个：

### 构建逻辑 key

在 `buildCurrentMainlineKey(...)` 里，如果是 `CLIENT_BALANCE`，就会走：

1. `cacheKeyBuilder.clientBalanceByClientId(...)`

因此逻辑 key 是：

1. `client_balance:{clientId}`

### 具体写法

在 `doCurrentMainlineUpsert(...)` 里，如果是 `CLIENT_BALANCE`，就会走：

1. `cacheWriteClient.hmset(key, resolveClientBalancePayload(entityOrId))`

这说明：

1. `balance` 最终也是 `HASH`
2. 但 payload 不是随便转 Map
3. 会先经过 `resolveClientBalancePayload(...)` 做更严格的字段整理

## 3.9 为什么 `syncDelete("client_balance", ...)` 会被跳过

在 `CacheSyncServiceImpl.syncDelete(...)` 里，如果某个域的删除策略是：

1. `OVERWRITE_ONLY`

那么删除动作会被直接跳过。

而 `client_balance` 恰好就是这个策略。

这意味着：

1. 普通域可以删 key
2. 余额域默认不删 key
3. 它更适合“用最新真源值覆盖缓存”，而不是“先删掉再说”

## 3.10 `balance` 在读链路里怎么被使用

在 API 侧，余额校验读取的是 Redis 镜像，例如 `FeeCheckFilter` 会读取：

1. `client_balance:{clientId}` 中的 `balance` 字段

但代码里同时明确说明：

1. `client_balance` 的主口径是 MySQL
2. Redis 只是余额镜像

这体现了 `balance` 的整体设计：

1. 读快：走 Redis
2. 写准：靠 MySQL

## 3.11 `balance` 的手工重建和 boot

### 手工重建

loader 是：

1. `ClientBalanceDomainRebuildLoader`

它会：

1. 调用 `selectAllActive()`
2. 从 MySQL 拉全部有效余额记录
3. 把全量快照交给重建引擎

### 启动校准

由于 `client_balance`：

1. 属于主线域
2. 属于 manual 默认域
3. 契约上 `bootRebuildEnabled = true`
4. 有对应 loader

所以它也能进入默认 boot 范围。

这说明当前项目对 `balance` 的态度是：

> 虽然它是高风险域，但它不是排除在重建体系之外的例外，而是以更谨慎的写删策略接入统一重建体系。

## 3.12 一句话总结 `balance`

`client_balance` 先在 `CacheDomainRegistry` 中通过 `CacheDomainContract` 被注册为主线缓存域，同时进入 manual 和 boot 的默认范围；但它和普通主线域不同，写入策略是 `MYSQL_ATOMIC_UPDATE_THEN_REFRESH`，删除策略是 `OVERWRITE_ONLY`。  
运行时入口不是普通 CRUD，而是 `BalanceCommandServiceImpl` 的 `debitAndSync/rechargeAndSync/adjustAndSync` 三个命令方法；业务层先执行 MySQL 原子 SQL，再回查最新余额和客户业务快照，然后通过 `scheduleBalanceDoubleRefresh(...)` 注册两次同步动作，分别刷新 `client_balance` 和 `client_business`。  
真正执行时，链路仍然进入 `CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)`、`CacheSyncServiceImpl.syncUpsert(...)`、`doUpsert(...)`、`doCurrentMainlineUpsert(...)`，最终按 Hash 形式调用 `cacheWriteClient.hmset(...)` 写入 Redis。  
在读链路上，API 会读取 Redis 余额镜像做快速校验，但系统仍明确以 MySQL 作为余额真源。

---

## 4. `channel` 和 `balance` 最本质的区别

把这两个域对比着看，最容易抓住主线与高风险域的区别：

| 对比项 | `channel` | `client_balance` |
| --- | --- | --- |
| 业务性质 | 普通配置域 | 高风险账务镜像域 |
| 业务入口 | CRUD Service | 命令 Service |
| 写 MySQL 方式 | 普通保存/更新 | 原子 SQL |
| 缓存刷新域数 | 单域 | 双域 |
| 删除策略 | `DELETE_KEY` | `OVERWRITE_ONLY` |
| 读链路定位 | 配置缓存 | 余额镜像缓存 |

因此理解这两个域后，你基本就能看清：

1. 普通主线域是什么样
2. 高风险主线域又为什么要额外特殊化

---

## 5. 本文小结

如果只记一句话：

1. `channel` 代表“普通主线域”
2. `client_balance` 代表“高风险主线域”

前者让你理解：

1. 规则注册
2. 运行时入口
3. `syncUpsert -> doUpsert -> doCurrentMainlineUpsert`
4. Hash 写 Redis

后者让你理解：

1. 为什么有些域不能按普通 CRUD 方式处理
2. 为什么有些域必须先 MySQL 原子更新
3. 为什么有些域会做双域刷新
4. 为什么有些域默认不删 key

如果把这两条链路都看懂，后面再去看 `client_business` 和 `client_channel`，就会变成：

1. 它比 `channel` 多了什么
2. 它比 `balance` 少了什么

这会比直接从“四层抽象”硬啃轻松很多。

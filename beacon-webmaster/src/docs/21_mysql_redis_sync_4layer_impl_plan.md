# MySQL -> Redis 四层架构说明（当前实现）

## 0. 文档定位

这份文档不再描述“准备怎么做”，而是描述“当前代码已经怎么做”。

目标读者：

1. 对本项目不熟的人
2. 对 MySQL 和 Redis 同步链路不熟的人
3. 对 Redis 底层机制不熟的人

阅读方式建议：

1. 先看本文件，了解四层总体结构
2. 再按顺序看第一层到第四层文档
3. 遇到 Redis 相关机制时，把它理解成“为了解决并发、隔离、命名冲突而增加的基础设施”

---

## 1. 这套同步架构在解决什么问题

系统里有一批数据最终以 MySQL 为准，但业务高频读取又希望走 Redis。

如果只有 MySQL、没有同步链路，会出现两个问题：

1. MySQL 更新了，但 Redis 还是旧值
2. Redis 能写，但没人保证写法统一，久而久之不同模块会把同一份数据写成不同格式

当前代码通过“四层结构”解决这个问题：

1. 第一层：把规则冻结下来，统一 key、域、命名空间、真源和边界
2. 第二层：在业务正常运行时，把 MySQL 的变化同步到 Redis
3. 第三层：当 Redis 脏了、丢了、或者历史数据不一致时，允许人工从 MySQL 全量重建
4. 第四层：系统启动时自动做一轮有限域校准，减少重启后或环境切换后的脏缓存残留

---

## 2. 当前主线域

当前主线只围绕 4 个域展开：

| 域 | 逻辑 key | MySQL 真源 | Redis 结构 | 当前状态 |
| --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apiKey}` | `client_business` | Hash | 已接入四层 |
| `client_channel` | `client_channel:{clientId}` | 客户通道路由相关表 | Set | 已接入四层 |
| `channel` | `channel:{id}` | `channel` | Hash | 已接入四层 |
| `client_balance` | `client_balance:{clientId}` | `client_balance` | Hash | 已接入四层 |

以下域当前不进入第三层和第四层默认范围：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

说明：

1. 这些域里有些仍有运行时同步代码
2. 但它们不是当前 MySQL 主线重建和启动校准的默认对象

---

## 3. 四层总览

| 层次 | 主要作用 | 主要类 | 解决的问题 |
| --- | --- | --- | --- |
| 第一层：基础层收口 | 定义统一规则和范围 | `CacheKeyBuilder`、`CacheDomainRegistry`、`CacheDomainContract`、`CacheSyncProperties` | 避免不同模块各自定义 key、域和前缀 |
| 第二层：运行时同步 | 正常业务请求里把 MySQL 变化同步到 Redis | `CacheSyncService`、`CacheSyncServiceImpl`、`CacheSyncRuntimeExecutor`、`BalanceCommandServiceImpl` | 避免“数据库更新了但缓存没更新” |
| 第三层：手工重建 | 从 MySQL 全量恢复 Redis | `CacheRebuildService`、`CacheRebuildController`、`DomainRebuildLoader`、`CacheRebuildCoordinationSupport` | 处理历史脏数据、停机改库后缓存错乱 |
| 第四层：启动校准 | 启动时自动做有限域重建 | `CacheBootReconcileRunner` | 降低重启后脏缓存和 Redis 残留问题 |

这四层不是四套彼此独立的系统，而是同一套同步基础设施在不同场景下的四种使用方式。

---

## 4. 一次数据写入是怎么走到 Redis 的

以运行时同步为例，当前主线流程是：

1. 业务代码先改 MySQL
2. 业务层决定应该刷新哪个缓存域
3. 调用 `CacheSyncService` 的 `syncUpsert` 或 `syncDelete`
4. `CacheSyncServiceImpl` 根据域规则决定：
   - key 怎么拼
   - 用 Hash、Set 还是 String
   - 是覆盖写，还是先删再重建
5. `BeaconCacheWriteClient` 调用 `beacon-cache` 服务
6. `beacon-cache` 侧把“逻辑 key”转成“带命名空间前缀的物理 key”
7. 最终由 `CacheController` 写入 Redis

这条链路的关键点是：

1. 业务侧只关心逻辑 key
2. 缓存侧统一补物理前缀
3. 业务侧不直接拼 Redis 真实前缀

这样做的好处是：

1. 代码里不会到处散落 `beacon:dev:...` 这种前缀字符串
2. 环境切换时不需要全局搜前缀
3. 逻辑语义和部署语义被拆开了

---

## 5. 先理解两个 key：逻辑 key 和物理 key

### 5.1 什么是逻辑 key

逻辑 key 是业务代码眼里的 key。

例如：

1. `client_business:ak_1001`
2. `client_balance:1001`
3. `channel:3001`

它只表达“这是谁的数据”，不表达环境、项目或命名空间。

### 5.2 什么是物理 key

物理 key 是 Redis 里最终真正落库的 key。

例如：

1. `beacon:dev:beacon-cloud:cz:client_business:ak_1001`
2. `beacon:dev:beacon-cloud:cz:client_balance:1001`

这里前面的 `beacon:dev:beacon-cloud:cz:` 就是命名空间前缀。

### 5.3 当前是谁负责把逻辑 key 变成物理 key

当前责任划分是：

1. `CacheKeyBuilder` 负责生成逻辑 key
2. `NamespaceKeyResolver` 负责把逻辑 key 变成物理 key
3. `CacheNamespaceProperties` 负责提供物理前缀配置

对应类：

1. [CacheKeyBuilder](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\support\CacheKeyBuilder.java)
2. [NamespaceKeyResolver](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-cache\src\main\java\com\cz\cache\redis\NamespaceKeyResolver.java)
3. [CacheNamespaceProperties](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-cache\src\main\java\com\cz\cache\security\CacheNamespaceProperties.java)

---

## 6. 当前 Redis 相关机制说明

下面几个机制是理解这套同步架构的关键。

### 6.1 Hash、Set、String 分别在这里干什么

在这个项目里：

1. Hash 适合“一条记录对应多个字段”
   - 例如 `client_business`
   - 例如 `client_balance`
   - 例如 `channel`
2. Set 适合“一组成员”
   - 例如 `client_channel`
3. String 主要用于简单值或协调标记
   - 例如分布式锁 token
   - 例如脏标记

### 6.2 为什么 `client_channel` 不是一条条增量改，而是整组重建

`client_channel` 表示“某个客户当前可用的通道路由集合”。

如果只改其中一条成员，很容易把旧成员残留在 Redis 里。

所以当前代码的做法是：

1. 先按 `clientId` 把该客户所有成员查全
2. 先删掉 Redis 里的旧集合
3. 再把全量成员一次性写回去

这就是 `DELETE_AND_REBUILD` 策略。

### 6.3 为什么 `client_balance` 不能默认删 key

余额和普通配置不同，它是高风险数据。

如果直接删掉 `client_balance:{clientId}`，可能出现短时间“缓存里没有余额”的空窗期。

所以当前策略是：

1. 真源永远是 MySQL `client_balance` 表
2. Redis 只是镜像缓存
3. 更新余额后直接覆盖 Redis
4. 不把“删 key”当作默认操作

这在域契约里体现为 `OVERWRITE_ONLY`。

### 6.4 什么是分布式锁

这里说的分布式锁，本质上是“所有应用实例都去 Redis 里争同一个锁 key”。

因为大家连的是同一个 Redis，所以：

1. 第一个抢到锁的实例进入重建
2. 后面的实例看到锁已经存在，就不能同时重建同一个域

当前锁 key 形如：

1. `cache:rebuild:client_business`
2. `cache:rebuild:channel`
3. `cache:rebuild:client_balance`

当前实现类：

1. [CacheRebuildCoordinationSupport](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\rebuild\CacheRebuildCoordinationSupport.java)

它内部做了三件事：

1. 用 `setIfAbsent` 抢锁
2. 用 token 标记锁的持有者
3. 释放锁时用 `deleteIfValueMatches` 校验 token，避免误删别人的锁

### 6.5 为什么锁里还要放 token

原因很简单：

1. 实例 A 抢到锁后，Redis 里记录的是 A 的 token
2. 如果实例 B 误把这把锁删掉，就会破坏并发安全

所以释放锁时不能只按 key 删除，而要判断：

1. 这个 key 现在的 value 是否还是我当初写进去的 token
2. 只有一致时，才允许删除

这就是“安全释放锁”。

### 6.6 为什么锁还要有 TTL

如果某个实例抢到锁后进程崩了，锁如果永不过期，就会变成死锁。

所以当前锁有 TTL，默认是 300 秒。

意思是：

1. 正常情况会主动释放
2. 异常崩溃时，Redis 也会在 TTL 到期后自动清理

### 6.7 什么是脏标记

当某个域正在做全量重建时，运行时同步如果继续直接写 Redis，可能把“正在重建中的数据集”打乱。

当前解决方式不是强行并发写，而是：

1. 运行时同步先发现这个域正在重建
2. 不直接写 Redis
3. 只写一个“脏标记”到 Redis
4. 重建结束后，发现这个标记还在，就立刻补跑一次

这个标记 key 形如：

1. `cache:rebuild:dirty:client_business`
2. `cache:rebuild:dirty:channel`

可以把它理解成一句话：

> “我重建期间又来了新的变更，重建完记得再跑一遍。”

### 6.8 为什么运行时同步经常放到 `afterCommit`

因为 MySQL 是真源。

如果事务还没提交就先写 Redis，会出现一个危险情况：

1. Redis 已经是新值
2. MySQL 最后事务回滚
3. 结果缓存反而比数据库还“更新”

所以当前主线做法是：

1. 如果当前有事务，就在事务提交成功后再同步 Redis
2. 如果没有事务，再立即同步

对应类：

1. [CacheSyncRuntimeExecutor](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\support\CacheSyncRuntimeExecutor.java)

---

## 7. 当前各层之间的关系

当前四层不是上下替代关系，而是职责叠加关系：

1. 第一层定义规则
2. 第二层负责日常写入同步
3. 第三层负责人工全量修复
4. 第四层负责启动时自动修复


---


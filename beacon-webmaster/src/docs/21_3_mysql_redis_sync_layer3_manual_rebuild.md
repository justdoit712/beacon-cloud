# 第三层：手工重建（架构说明）

## 0. 本层在整套架构中的位置

第三层解决的是：

> Redis 已经不可信时，怎样从 MySQL 真源重新把缓存修回来。

常见场景包括：

1. 停机改库后需要全量回灌 Redis
2. 某个域历史数据已经脏了
3. 演示、联调或问题排查前，需要手动做一次校准

第三层不是普通业务写入通道，它更像“维修工具”。

---

## 1. 当前第三层主要类

| 类 | 作用 |
| --- | --- |
| `CacheRebuildService` | 对外暴露重建入口 |
| `CacheRebuildServiceImpl` | 把重建请求转给底层引擎 |
| `CacheRebuildController` | 管理端手工重建接口 |
| `DomainRebuildLoader` | 定义“某个域怎样从 MySQL 拉全量快照” |
| `DomainRebuildLoaderRegistry` | 管理所有 loader |
| `CacheRebuildCoordinationSupport` | 管理重建锁和脏标记 |
| `CacheRebuildReport` | 返回结构化报告 |

当前主线域 loader 包括：

1. `ClientBusinessDomainRebuildLoader`
2. `ChannelDomainRebuildLoader`
3. `ClientChannelDomainRebuildLoader`
4. `ClientBalanceDomainRebuildLoader`

---

## 2. 手工重建到底在做什么

手工重建不是简单地“把 MySQL 查出来再写回 Redis”。

当前完整流程是：

1. 管理员调用重建接口
2. 进入 `CacheRebuildService`
3. 解析域参数，支持单域和 `ALL`
4. 对每个域先获取域级锁
5. 清理该域当前 Redis 里的旧 key
6. 用该域对应的 loader 从 MySQL 拉全量快照
7. 按当前运行时同步同一套规则把快照回灌到 Redis
8. 检查重建期间是否收到“脏标记”
9. 如果收到，立刻再补跑一次
10. 生成结构化报告

这里最重要的一点是：

> 第三层并没有自己再实现一套 Redis 写法，而是复用了第二层已经定义好的 key 规则和写入规则。

---

## 3. `ALL` 在当前代码里是什么意思

`ALL` 不是“注册表里所有域”。

当前 `ALL` 的含义是：

1. 该域属于当前主线范围
2. 该域允许手工重建
3. 该域已经注册了 loader

这就是为什么 legacy 域不会被 `ALL` 带上。

同时，现在 `client_balance` 已经具备前置条件，所以它也已经进入 `ALL` 范围。

---

## 4. 当前第三层 loader 是怎么设计的

### 4.1 `client_business`

当前 loader：

1. 查询有效客户业务数据
2. 直接返回 `ClientBusiness` 实体列表
3. 后续由统一写入引擎按 `client_business:{apiKey}` 写成 Hash

### 4.2 `channel`

当前 loader：

1. 查询有效通道
2. 直接返回 `Channel` 实体列表
3. 后续统一写成 `channel:{id}` 的 Hash

### 4.3 `client_channel`

这是第三层里最典型的“集合型域”。

当前 loader 不返回单条成员，而是：

1. 先查所有有效 `clientId`
2. 再按这些 `clientId` 查全量路由成员
3. 组装成：
   - `clientId`
   - `members`
4. 每个 `clientId` 对应一份全量 payload

这样重建引擎拿到的不是“零散成员”，而是“某个客户的整组路由结果”。

### 4.4 `client_balance`

当前 loader：

1. 查询全部有效余额记录
2. 返回 `ClientBalance` 实体列表
3. 后续统一写成 `client_balance:{clientId}` 的 Hash

这说明 `client_balance` 现在已经不再只是规划中的条件域，而是真正接入了第三层。

---

## 5. Redis 相关机制：为什么第三层需要锁

### 5.1 如果没有锁，会发生什么

假设某个域正在做全量重建：

1. 它先删掉 Redis 旧 key
2. 还没全部写回时
3. 另一个实例或运行时同步又开始写同一个域

就会出现：

1. 一部分 key 是旧值
2. 一部分 key 是新值
3. 一部分 key 被重建覆盖
4. 一部分 key 被运行时同步覆盖

最终 Redis 会变成“混合态”。

### 5.2 当前锁是怎么做的

当前实现类是：

1. [CacheRebuildCoordinationSupport](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\rebuild\CacheRebuildCoordinationSupport.java)

它在 Redis 里使用一个普通字符串 key 充当锁：

1. 锁 key：`cache:rebuild:{domain}`
2. value：当前重建持有者的 token
3. TTL：默认 300 秒

抢锁时的思路是：

1. 用 `setIfAbsent`
2. 如果 Redis 里还没有这个 key，就创建成功
3. 如果已经有了，说明已经有人在重建这个域

因为所有实例都连同一个 Redis，所以这把锁天然可以跨实例生效。

### 5.3 为什么释放锁还要比对 token

如果只按 key 删除锁，会有风险：

1. 实例 A 抢到锁
2. 实例 B 误删这把锁
3. 结果新的实例又能进入重建

所以当前释放锁不是简单 `delete(key)`，而是：

1. 先看 value 是否还是自己的 token
2. 只有匹配时才删除

这就是“安全释放锁”。

---

## 6. Redis 相关机制：什么是脏标记补跑

### 6.1 为什么需要脏标记

重建过程通常分两步：

1. 先清旧数据
2. 再写新数据

如果这时正常业务请求又改了 MySQL，对应的运行时同步如果继续直接写 Redis，就会打断重建。

### 6.2 当前怎么处理

当前不是让运行时同步强行写进去，而是：

1. 发现这个域正在重建
2. 不直接写 Redis
3. 只写一个“脏标记”到 Redis

脏标记 key 形如：

1. `cache:rebuild:dirty:{domain}`

### 6.3 重建结束后怎么处理

重建结束时，当前引擎会检查：

1. 这个域有没有脏标记

如果有，就说明：

> “刚刚重建期间又来了新的业务变更”

于是当前实现会：

1. 立刻再补跑一次这个域的重建

这样就避免了“刚重建完又立刻落后”的问题。

---

## 7. 第三层当前的报告结构

当前重建结果使用：

1. `CacheRebuildReport`

主要字段包括：

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

这意味着第三层不是“只打日志”，而是会返回一份可以继续被控制器、第四层、测试代码使用的结构化结果。

---

## 8. 第三层与第二层、第四层的关系

### 8.1 它对第二层的依赖

第三层虽然是“全量重建”，但真正写 Redis 时仍然依赖第二层已有的基础能力：

1. key builder
2. 域契约
3. 写入策略
4. Redis 客户端

### 8.2 它被第四层复用

第四层启动校准并没有新起一套引擎。

它最终还是通过：

1. `CacheRebuildService`
2. `CacheSyncServiceImpl.rebuildBootDomain(...)`
3. `rebuildSingleDomain(...)`

走到第三层同一套核心重建逻辑。

所以第三层可以理解成：

> “真正负责全量重建的公共引擎层”

---

## 9. 本层小结

第三层的职责可以总结成四句话：

1. Redis 脏了时，可以人工从 MySQL 全量恢复
2. 重建时要用 Redis 锁挡住同域并发进入
3. 运行时同步撞上重建时，不直接写 Redis，而是记脏标记
4. 重建结束会检查脏标记并补跑，尽量把结果收敛到最新状态

如果把整套架构比作一栋楼，第三层就是“维修层”和“应急修复工具层”。

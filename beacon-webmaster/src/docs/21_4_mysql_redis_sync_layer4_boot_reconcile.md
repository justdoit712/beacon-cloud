# 第四层：启动校准（架构说明）

## 0. 本层在整套架构中的位置

第四层处理的是“应用启动时”的缓存修复问题。

它解决的不是日常业务写入，而是下面这种情况：

1. 服务刚启动
2. Redis 里可能残留旧环境、旧版本、旧数据
3. 本机内存状态已经重置，但 Redis 还保留历史内容
4. 如果直接开始对外服务，可能先读到旧缓存

因此第四层会在系统启动后，自动对有限域做一次重建校准。

可以把它理解成：

> “应用开机后的自检和缓存预校准”

---

## 1. 当前第四层主要类

| 类 | 作用 |
| --- | --- |
| `CacheBootReconcileRunner` | Spring Boot 启动完成后自动触发启动校准 |
| `CacheRebuildService` | 对外暴露 boot 重建入口 |
| `CacheRebuildServiceImpl` | 委托到底层重建引擎 |
| `CacheSyncServiceImpl.rebuildBootDomain` | boot 场景专用入口 |
| `DomainRebuildLoaderRegistry` | 判断域是否已具备可执行重建能力 |
| `CacheRebuildCoordinationSupport` | 继续复用第三层的锁与脏标记机制 |

---

## 2. 当前第四层启动后到底会做什么

当前 `CacheBootReconcileRunner` 的启动流程可以拆成 6 步：

1. 检查 `sync.enabled`
2. 检查 `sync.boot.enabled`
3. 解析 `sync.boot.domains`
4. 如果没显式配置域，就走注册表默认 boot 域
5. 过滤出真正允许执行的域
6. 逐域调用 boot 重建入口，并输出单域日志和启动汇总

也就是说，第四层不是“服务一起来就把所有缓存全刷一遍”，而是：

1. 有明确开关
2. 有明确域范围
3. 有明确执行条件

---

## 3. 当前默认 boot 域

当前默认纳入启动校准的主线域是：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

不纳入默认范围的仍然是：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

这套默认范围并不是写死在 runner 里，而是来自 `CacheDomainRegistry`。

这样做的好处是：

1. 手工重建和启动校准不会各自维护一套域清单
2. 业务边界变动时，修改注册表即可

---

## 4. runner 如何筛选真正可执行的域

当前 `CacheBootReconcileRunner` 不是拿到域名就直接执行，而是还会继续过滤。

一个域要进入启动校准执行，当前必须同时满足：

1. 域已注册
2. 域属于当前主线范围
3. 域契约允许 boot rebuild
4. 域已经注册了对应的 `DomainRebuildLoader`

这一步非常重要，因为它保证了：

1. legacy 域不会误进启动校准
2. 没有 loader 的域不会在启动时硬跑
3. boot 范围始终和第三层能力保持一致

---

## 5. 第四层是怎么复用第三层的

第四层没有自己重新实现“全量重建一遍 Redis”的逻辑。

当前调用关系是：

1. `CacheBootReconcileRunner` 逐域调用 `CacheRebuildService.rebuildBootDomain(...)`
2. `CacheRebuildServiceImpl` 再委托给 `CacheSyncServiceImpl.rebuildBootDomain(...)`
3. `CacheSyncServiceImpl.rebuildBootDomain(...)` 继续调用共享的 `rebuildSingleDomain(...)`

也就是说：

1. 第四层有自己的触发入口
2. 但真正的重建引擎仍然是第三层那一套

这就符合一个很重要的架构原则：

> 不允许手工重建和启动校准各做一套重建规则。

---

## 6. 为什么第四层还需要 boot 专用入口

既然第三层已经有手工重建，为什么第四层不直接调 `rebuildDomain(...)`？

原因是这两个场景的“入口语义”不同：

1. 手工重建受 `sync.manual.enabled` 控制
2. 启动校准受 `sync.boot.enabled` 控制
3. 两者虽然复用同一套底层引擎，但开关和日志语义不同

所以当前代码采用的是：

1. 底层引擎共用
2. 入口开关分开
3. 报告 trigger 分开

手工入口是 `MANUAL`，启动入口是 `BOOT`。

---

## 7. Redis 机制：为什么第四层还要复用分布式锁

### 7.1 典型风险

如果系统有多个实例一起启动，就可能出现：

1. 实例 A 启动，开始重建 `channel`
2. 实例 B 几乎同时启动，也开始重建 `channel`

结果就是两个实例同时改同一批 Redis key。

### 7.2 当前处理方式

第四层不自己实现锁，而是继续复用第三层的域级锁：

1. 同一域一次只能有一个实例进入重建
2. 抢锁失败时，当前 boot 重建会被拒绝

这意味着“多实例同时启动”不会把同一域并发重建乱掉。

### 7.3 对不熟 Redis 的读者的理解方式

可以把它想象成：

1. Redis 里有一把“共享钥匙”
2. 谁先拿到钥匙，谁先进去修这个域
3. 其他实例看到钥匙已经被拿走，就不能再同时进入

因为所有实例都连同一个 Redis，这把“钥匙”天然能跨机器生效。

---

## 8. Redis 机制：为什么第四层不阻塞应用启动

第四层的目标是“提高缓存一致性”，不是“把启动变成强依赖重建成功”。

如果启动校准失败就直接让应用启动失败，会产生两个问题：

1. 缓存问题反过来卡死整个服务
2. 启动失败后反而更难排查业务问题

所以当前代码的策略是：

1. 单域失败只记录失败并继续后续域
2. runner 顶层失败也只输出错误日志和失败汇总
3. 不把异常继续往外抛给 Spring Boot

换句话说，第四层的定位是：

> “尽力校准，但不把缓存问题升级成系统不可启动的问题”

---

## 9. 当前日志和汇总长什么样

### 9.1 单域日志

当前每个域执行后都会输出单域报告日志，至少包含：

1. `domain`
2. `status`
3. `startAt`
4. `endAt`
5. `costMs`
6. `successCount`
7. `failCount`
8. `failedKeys`

### 9.2 启动汇总

所有域跑完后，会聚合成一份 `CacheRebuildReport` 汇总报告：

1. `trigger=BOOT`
2. `domain=ALL`
3. `reports`：每个域的报告
4. `attemptedKeys`
5. `successCount`
6. `failCount`
7. `failedKeys`
8. `status`
9. `message`

### 9.3 汇总状态怎么判定

当前汇总状态不是单纯看 key 数量，而是结合“每个域的结果状态”判定：

1. 全部成功域：`SUCCESS`
2. 有成功域，也有失败域：`PARTIAL`
3. 只有失败域：`FAIL`
4. 没有可执行域：`SKIPPED`

这样可以避免出现“某个域虽然执行成功，但本次快照为空，因此成功 key 数仍是 0，结果被误判成失败”的问题。

---

## 10. 第四层与第二层的关系

第四层虽然是启动时执行，但它不是绕开第二层。

它最终仍然依赖第二层已经存在的这些规则：

1. key 怎么生成
2. Hash / Set / String 怎么写
3. `client_channel` 这种集合型域如何重建
4. `client_balance` 为什么只能覆盖写，不能默认删 key

所以第四层做的是：

1. 换一个触发时机
2. 复用已有写入规则
3. 自动把有限域修回正确状态

---



## 11. 本层小结

第四层的职责可以总结成三句话：

1. 应用启动后，先对有限主线域做一次自动校准
2. 校准时不重新发明重建逻辑，而是复用第三层核心引擎
3. 失败可定位、可汇总，但不会阻塞系统启动

如果把整套架构比作一栋楼，第四层就是“开机自检层”。

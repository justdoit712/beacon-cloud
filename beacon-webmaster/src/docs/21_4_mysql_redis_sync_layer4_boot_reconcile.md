# 第四层：启动校准（架构说明）

文档定位：开机自检层 / 自动校准层  
适用对象：开发 / 排障 / 答辩  
验证基线：当前仓库代码静态核对  
最后核对日期：2026-03-24

---

## 0. 本层在整套架构中的位置

第四层处理的是：

> 应用启动完成后，怎样自动对有限缓存域做一次预校准。

它解决的典型问题是：

1. 服务刚启动，但 Redis 还残留旧环境或旧版本 key
2. 本地内存状态重置了，但云 Redis 还长期保存历史内容
3. 如果不先校准，系统一对外服务就可能先读到旧缓存

所以第四层的定位不是日常业务写入，而是：

> 开机后的缓存自检和自动修复。

---

## 1. 当前第四层主要类

| 类 | 作用 |
| --- | --- |
| `CacheBootReconcileRunner` | Spring Boot 启动完成后自动触发 boot 校准 |
| `CacheRebuildService` | 对外暴露 boot 重建入口 |
| `CacheRebuildServiceImpl` | 委托到底层重建引擎 |
| `CacheSyncServiceImpl.rebuildBootDomain` | boot 场景专用入口 |
| `DomainRebuildLoaderRegistry` | 判断某个域是否已具备可执行重建能力 |
| `CacheRebuildCoordinationSupport` | 继续复用第三层的锁和脏标记机制 |

---

## 2. 当前启动校准的总流程

当前 `CacheBootReconcileRunner` 在应用启动后的主流程是：

1. 检查 `sync.enabled`
2. 检查 `sync.boot.enabled`
3. 解析本次要跑的域列表
4. 如果没有显式配置域，就取注册表默认 boot 域
5. 过滤出真正可执行的域
6. 逐域调用 boot 重建入口
7. 输出单域报告和总汇总

整体链路如下：

```text
Spring Boot startup completed
    -> CacheBootReconcileRunner.run(...)
    -> resolveRequestedDomains()
    -> resolveExecutableDomains()
    -> rebuildBootDomain(domain)
    -> CacheSyncServiceImpl.rebuildBootDomain(domain)
    -> rebuildSingleDomain(domain)
    -> 复用第三层重建引擎
```

---

## 3. 当前默认 boot 域

当前代码里的默认 boot 域来自：

1. `CacheDomainRegistry.currentBootReconcileDomainCodes()`

当前默认主线 boot 域包括：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

这说明当前默认启动校准范围和当前主线 manual 范围已经对齐。

不会默认纳入 boot 的兼容域包括：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

---

## 4. runner 是怎样筛选可执行域的

`CacheBootReconcileRunner` 不是拿到域名就直接跑。  
当前还会做一层“是否真正可执行”的过滤。

一个域要进入 boot 执行，必须同时满足：

1. 域已注册
2. 域属于当前主线范围
3. 域契约允许 `bootRebuildEnabled`
4. 域已注册对应的 `DomainRebuildLoader`

这一步的意义是：

1. legacy 域不会误进入启动校准
2. 没有 loader 的域不会在启动时硬跑
3. boot 范围始终和重建能力保持一致

---

## 5. `client_business` 启动校准端到端详解

下面以 `client_business` 为例，说明启动后它是怎样被自动校准的。

### 5.1 选中 `client_business`

当前 runner 启动后：

1. 先读取 `sync.boot.domains`
2. 如果没配置，就取默认 boot 域
3. `resolveExecutableDomains(...)` 逐个过滤
4. `client_business` 满足：
   - 已注册
   - 属于当前主线
   - `bootRebuildEnabled=true`
   - 已注册 `ClientBusinessDomainRebuildLoader`

于是它会进入本次 boot 执行列表。

### 5.2 进入 boot 重建入口

当前调用链：

```text
CacheBootReconcileRunner.run
    -> executeBootReconcile(domains)
    -> rebuildBootDomain("client_business")
    -> CacheRebuildService.rebuildBootDomain("client_business")
    -> CacheSyncServiceImpl.rebuildBootDomain("client_business")
```

### 5.3 为什么这里还要有 `rebuildBootDomain(...)`

虽然底层重建逻辑和手工重建是共用的，但 boot 入口和 manual 入口不能混成一个，原因是：

1. boot 受 `sync.boot.enabled` 控制
2. manual 受 `sync.manual.enabled` 控制
3. 两者的日志语义不同
4. 两者的报告 `trigger` 不同

所以当前设计是：

1. **入口语义分开**
2. **底层引擎复用**

### 5.4 进入共享重建引擎

`CacheSyncServiceImpl.rebuildBootDomain("client_business")` 最终仍然会调用：

1. `rebuildSingleDomain("client_business")`

这意味着对 `client_business` 来说，boot 重建的底层动作和第三层手工重建完全一致：

1. 抢域级锁
2. 清理旧 key
3. 通过 `ClientBusinessDomainRebuildLoader` 从 MySQL 拉快照
4. 复用 `buildKey + doUpsert` 写回 Redis
5. 检查脏标记
6. 必要时补跑
7. 释放锁

所以如果从“数据怎么从 MySQL 走到 Redis”这个角度看：

1. boot 校准不是第二套逻辑
2. 它只是第三层重建引擎的自动触发入口

---

## 6. `client_business` 在 boot 阶段如何从 MySQL 到 Redis

虽然入口是启动 runner，但真正的数据流转和手工重建一致。

完整链路可以写成：

```text
CacheBootReconcileRunner
    -> CacheSyncServiceImpl.rebuildBootDomain("client_business")
    -> rebuildSingleDomain("client_business")
    -> ClientBusinessDomainRebuildLoader.loadSnapshot()
    -> ClientBusinessMapper.selectByExample(...) 读取 MySQL 真源
    -> 对每条 ClientBusiness:
         buildKey("client_business", entity)
         -> CacheKeyBuilder.clientBusinessByApiKey(apiKey)
         -> 逻辑 key: client_business:ak_1001
         doUpsert(...)
         -> BeaconCacheWriteClient.hmset(...)
         -> CacheController.hmset(...)
         -> NamespaceKeyResolver.toPhysicalKey(...)
         -> Redis Hash
```

这说明：

1. 启动校准也不是直接操作 RedisTemplate
2. 启动校准也复用同一套缓存服务接口
3. `client_business` 在 boot 阶段最终 Redis 结果，和运行时同步 / 手工重建是一致的

---

## 7. 为什么第四层还要复用第三层的锁

### 7.1 多实例同时启动的风险

如果系统有多个实例一起启动，就会出现：

1. 实例 A 开始 boot 重建 `client_business`
2. 实例 B 几乎同时也开始 boot 重建 `client_business`

如果不加锁，结果就是：

1. 两个实例同时删旧 key
2. 又同时回灌新 key
3. 过程混乱且不可预测

### 7.2 当前处理方式

当前第四层不自己实现锁，而是继续复用：

1. `CacheRebuildCoordinationSupport`

也就是说：

1. boot 和 manual 共享同一套域级锁
2. 同一域一次只能有一个实例进入重建

这让多实例同时启动时，同一域不会被并发重建。

---

## 8. 为什么第四层也要复用脏标记机制

boot 校准期间，系统可能已经开始接收业务流量。  
这时如果运行时同步还直接写 Redis，就会打断 boot 重建。

所以第四层也继续复用：

1. 运行时同步发现同域重建中 -> 写脏标记
2. boot 重建结束时 -> 发现脏标记 -> 补跑一轮

这样能保证：

1. 启动校准期间产生的新变更不会直接丢掉
2. 启动校准不会因为被运行时写入打断而留下混合态

---

## 9. 为什么 boot 失败不阻塞应用启动

第四层的目标是：

1. 提高缓存一致性
2. 尽量在系统对外服务前修复掉明显脏缓存

它不是：

1. 让缓存问题升级成“应用完全不能启动”

所以当前代码的策略是：

1. 单域失败时，记录失败并继续下一个域
2. 顶层 runner 出错时，输出失败汇总
3. 不把异常继续抛给 Spring Boot

这样做的原因很现实：

1. 否则缓存问题会反过来卡死整个服务
2. 启动失败后，排障成本反而更高

因此第四层是：

> 尽力修，不强卡。

---

## 10. 当前 boot 报告长什么样

第四层最终也使用：

1. `CacheRebuildReport`

但和 manual 不同，它会显式标记：

1. `trigger=BOOT`

当前汇总报告会包含：

1. `domain=ALL`
2. `reports`：各单域报告
3. `attemptedKeys`
4. `successCount`
5. `failCount`
6. `failedKeys`
7. `status`
8. `message`

当前汇总状态大致分为：

1. `SUCCESS`
2. `PARTIAL`
3. `FAIL`
4. `SKIPPED`

---

## 11. 第四层和前 3 层的关系

### 11.1 它依赖第一层

因为它要依赖：

1. 当前默认 boot 域是什么
2. 哪些域属于主线
3. 哪些域允许 boot rebuild

### 11.2 它依赖第二层

因为最终写 Redis 时，它仍然依赖：

1. key 怎么构建
2. Hash / Set / String 怎么写
3. `client_channel` 为什么要删后整组重建
4. `client_balance` 为什么只能覆盖刷新

### 11.3 它复用第三层

因为底层真正执行重建的引擎仍然是：

1. `rebuildSingleDomain(...)`
2. `executeRebuildPass(...)`
3. `CacheRebuildCoordinationSupport`

所以第四层可以理解成：

> 第三层重建引擎的自动触发层。

---

## 12. 本层小结

第四层可以总结成 5 句话：

1. 它在系统启动后自动对有限主线域做一次校准
2. 它先筛选“真正可执行”的域，而不是拿到域名就硬跑
3. 它对 `client_business` 的 MySQL -> Redis 流程和手工重建完全一致
4. 它继续复用第三层的锁和脏标记机制，避免并发重建混乱
5. 它会汇总结果，但不会因为校准失败而阻塞系统启动

如果把整套一致性方案比作一栋楼，第四层就是：

> 开机自检层和自动校准层。

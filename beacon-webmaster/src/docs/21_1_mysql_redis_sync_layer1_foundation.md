# 第一层：基础层收口（架构说明）

文档定位：规则层 / 地基层  
适用对象：开发 / 排障 / 答辩  
验证基线：当前仓库代码静态核对  
最后核对日期：2026-03-24

---

## 0. 本层在整套架构中的位置

第一层不负责直接写 Redis，也不负责执行重建。  
它负责先把缓存一致性里最容易混乱的规则全部定死：

1. 系统到底有哪些缓存域
2. 每个域的逻辑 key 怎么拼
3. Redis 里用什么结构保存
4. MySQL 和 Redis 不一致时到底以谁为准
5. 哪些域允许进入运行时同步、手工重建、启动校准

如果没有这一层，后面会同时出现几类问题：

1. 不同模块自己拼不同的 key
2. 同一个域有人按 Hash 写，有人按 String 写
3. 有人把 Redis 当前值当真源，有人把 MySQL 当真源
4. 手工重建和启动校准各自维护一套域清单

所以第一层的本质是：

> 把“缓存规则”从分散在业务代码里的隐式约定，收口为显式契约。

---

## 1. 当前第一层主要类

### 1.1 业务侧规则类

| 类 | 作用 |
| --- | --- |
| `CacheKeyBuilder` | 统一生成逻辑 key，例如 `client_business:{apikey}` |
| `CacheDomainRegistry` | 统一维护缓存域、主线范围、兼容范围、manual 范围、boot 范围 |
| `CacheDomainContract` | 描述单个缓存域的完整契约 |
| `CacheSyncProperties` | 统一承接 `sync.*` 配置，并校验命名空间一致性 |
| `CacheNamespaceConsistencyGuard` | 显式保证同步侧和缓存侧命名空间配置一致 |

### 1.2 常量与策略枚举

| 类 / 枚举 | 作用 |
| --- | --- |
| `CacheKeyConstants` | 定义逻辑 key 前缀常量 |
| `CacheRedisType` | 定义 Redis 结构类型 |
| `CacheSourceOfTruth` | 定义真源类型 |
| `CacheWritePolicy` | 定义写入策略 |
| `CacheDeletePolicy` | 定义删除策略 |
| `CacheRebuildPolicy` | 定义重建策略 |
| `CacheAuthHeaders` | 定义缓存服务内部调用认证头 |

### 1.3 缓存服务侧配套类

| 类 | 作用 |
| --- | --- |
| `CacheNamespaceProperties` | 定义 Redis 物理命名空间前缀 |
| `NamespaceKeyResolver` | 负责逻辑 key 和物理 key 双向转换 |
| `CacheController` | `beacon-cache` 对外暴露的缓存写删接口 |

---

## 2. 当前最重要的 4 个基础概念

### 2.1 什么是“缓存域”

缓存域就是“一类缓存数据”的统称。

当前主线域包括：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

兼容保留域包括：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

第一层的作用之一，就是先把这些域固定下来，而不是让各层各自维护一份列表。

### 2.2 什么是“真源”

真源表示：

> 当 MySQL 和 Redis 不一致时，最终应该以谁为准。

当前主线域契约里的真源都是 MySQL。

这意味着：

1. Redis 是派生缓存
2. Redis 可以被覆盖、删除、重建
3. 只要 MySQL 还正确，Redis 就总能被修回来

### 2.3 什么是“逻辑 key”

逻辑 key 是业务代码中使用的 key，不带环境前缀。

例如：

1. `client_business:ak_1001`
2. `client_channel:1001`
3. `channel:3001`
4. `client_balance:1001`

### 2.4 什么是“物理 key”

物理 key 是 Redis 里真正落库的 key，带命名空间前缀。

例如：

1. `beacon:dev:beacon-cloud:cz:client_business:ak_1001`
2. `beacon:dev:beacon-cloud:cz:client_channel:1001`
3. `beacon:dev:beacon-cloud:cz:channel:3001`

当前职责分工非常清楚：

1. `CacheKeyBuilder` 只负责逻辑 key
2. `NamespaceKeyResolver` 负责逻辑 key 和物理 key 转换
3. 业务代码不允许自己硬编码物理前缀

---

## 3. 关键常量在整条链路中的作用

### 3.1 `CacheKeyConstants`

`CacheKeyConstants` 是最原始的 key 命名来源。  
它定义的不是 Redis 最终 key，而是逻辑 key 的语义模板。

当前最重要的常量包括：

| 常量 | 含义 | 示例逻辑 key |
| --- | --- | --- |
| `CLIENT_BUSINESS = "client_business:"` | 客户业务配置缓存前缀 | `client_business:ak_1001` |
| `CLIENT_CHANNEL = "client_channel:"` | 客户通道路由集合前缀 | `client_channel:1001` |
| `CHANNEL = "channel:"` | 通道配置缓存前缀 | `channel:3001` |
| `CLIENT_BALANCE = "client_balance:"` | 客户余额镜像缓存前缀 | `client_balance:1001` |
| `CLIENT_SIGN = "client_sign:"` | 客户签名集合前缀 | `client_sign:1001` |
| `CLIENT_TEMPLATE = "client_template:"` | 签名模板集合前缀 | `client_template:2001` |
| `BLACK = "black:"` | 黑名单前缀 | `black:13800000000` |
| `DIRTY_WORD = "dirty_word"` | 敏感词集合固定 key | `dirty_word` |
| `TRANSFER = "transfer:"` | 携号转网前缀 | `transfer:13800000000` |
| `SEPARATE = ":"` | 复合 key 分隔符 | `black:1001:13800000000` |

### 3.2 `CacheAuthHeaders`

`CacheAuthHeaders` 不是缓存规则本身，但它保证了 `webmaster -> cache` 这条同步链路是受控的。

当前 3 个头分别是：

1. `X-Cache-Caller`
2. `X-Cache-Timestamp`
3. `X-Cache-Sign`

它们的作用是：

1. 标识调用方是谁
2. 防止重放
3. 验证请求没有被篡改

### 3.3 策略枚举

#### `CacheRedisType`

它决定某个域在 Redis 里长什么样。

当前主要类型：

1. `HASH`
2. `SET`
3. `STRING`

#### `CacheWritePolicy`

它决定一个域写 Redis 时怎么写。

当前常见策略：

1. `WRITE_THROUGH`
2. `DELETE_AND_REBUILD`
3. `MYSQL_ATOMIC_UPDATE_THEN_REFRESH`

#### `CacheDeletePolicy`

它决定一个域执行删除动作时能不能真的删 key。

当前常见策略：

1. `DELETE_KEY`
2. `OVERWRITE_ONLY`

#### `CacheRebuildPolicy`

它决定一个域能不能参与重建，以及是否允许进入 boot 范围。

---

## 4. 当前主线域契约

当前代码里的主线域契约如下：

| 域 | 逻辑 key 模式 | Redis 结构 | 真源 | 写入策略 | 删除策略 | manual | boot |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apikey}` | `HASH` | `MYSQL` | `WRITE_THROUGH` | `DELETE_KEY` | 是 | 是 |
| `client_channel` | `client_channel:{clientId}` | `SET` | `MYSQL` | `DELETE_AND_REBUILD` | `DELETE_KEY` | 是 | 是 |
| `channel` | `channel:{id}` | `HASH` | `MYSQL` | `WRITE_THROUGH` | `DELETE_KEY` | 是 | 是 |
| `client_balance` | `client_balance:{clientId}` | `HASH` | `MYSQL` | `MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | `OVERWRITE_ONLY` | 是 | 是 |

### 4.1 `client_business`

这个域最典型，最适合讲解整个链路。

它的规则可以总结成：

1. key 由 `apiKey/apikey` 决定
2. Redis 结构是 `HASH`
3. MySQL 是真源
4. 运行时变更后直接覆盖写 Redis
5. 如果 `apiKey` 改了，要先删旧 key 再写新 key
6. 允许手工重建和启动校准

### 4.2 `client_channel`

它是集合型域，不适合做局部增量更新。

当前规则是：

1. 一个客户对应一整个 Set
2. 每次同步都按“整组快照”处理
3. 统一采用“删旧集合 -> 全量重建”

### 4.3 `client_balance`

它是当前最特殊的高风险镜像域。

当前规则是：

1. 真源必须是 MySQL `client_balance`
2. 写入不是普通覆盖，而是“先 MySQL 原子更新，后刷新 Redis 镜像”
3. 删除策略不是删 key，而是 `OVERWRITE_ONLY`
4. 当前代码里它已经进入 manual / boot 范围

---

## 5. `client_business` 在第一层里的完整规则链

如果只看 `client_business` 这一个域，它在第一层里的规则链是：

```text
CacheKeyConstants.CLIENT_BUSINESS
    -> CacheKeyBuilder.clientBusinessByApiKey(apiKey)
    -> 逻辑 key: client_business:ak_1001
    -> CacheDomainRegistry / CacheDomainContract
    -> Redis 类型: HASH
    -> 真源: MYSQL
    -> 写入策略: WRITE_THROUGH
    -> 删除策略: DELETE_KEY
    -> manual: enabled
    -> boot: enabled
```

这说明第一层并不是抽象讨论，而是已经把后续 3 层真正会用到的规则全部定死。

---

## 6. 命名空间为什么必须在第一层就说清楚

### 6.1 当前命名空间规则

业务侧和缓存侧当前都使用完整命名空间前缀：

1. `beacon:dev:beacon-cloud:cz:`

业务侧配置类：

1. `CacheSyncProperties`

缓存侧配置类：

1. `CacheNamespaceProperties`

### 6.2 为什么要强校验一致

如果这两个前缀不一致，会出现：

1. `webmaster` 认为自己写的是当前环境缓存
2. `cache` 实际把 key 落到了另一个前缀

这种错误最危险，因为：

1. 不一定立刻报错
2. 但会静默写错环境

所以当前代码的做法是：

1. `CacheSyncProperties.validate()` 启动时就检查两边前缀是否一致
2. 不一致直接失败，不允许系统带病启动

---

## 7. 当前第一层如何约束后面三层

### 7.1 对运行时同步的约束

运行时同步不再自己决定：

1. 哪些域能同步
2. key 怎么拼
3. 什么域用 Hash，什么域用 Set
4. 哪些域允许删 key

这些都直接从第一层取。

### 7.2 对手工重建的约束

手工重建不再自己维护一套域范围。  
它直接依赖：

1. 当前域是不是主线域
2. 当前域是不是允许进入 manual 范围
3. 当前域有没有契约

### 7.3 对启动校准的约束

启动校准也不自己定义默认域。  
它直接依赖：

1. `CacheDomainRegistry.currentBootReconcileDomainCodes()`
2. 域契约里的 `bootRebuildEnabled`

---

## 8. 本层小结

第一层可以总结成 4 句话：

1. 它先把缓存域、key、结构、真源、策略、范围统一冻结
2. 它让后续各层都不再自己发明规则
3. 它把逻辑 key 和物理 key 明确拆开，避免业务侧污染命名空间逻辑
4. 它是整套缓存一致性设计的地基

如果把整套 MySQL -> Redis 一致性方案比作一栋楼，第一层就是：

> 地基、承重墙和图纸。

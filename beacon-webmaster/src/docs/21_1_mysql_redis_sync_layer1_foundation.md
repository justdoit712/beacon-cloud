# 第一层：基础层收口（架构说明）

## 0. 本层在整套架构中的位置

第一层不是“业务功能层”，而是“规则层”。

它的作用可以理解成一句话：

> 先把哪些数据要同步、key 怎么拼、Redis 用什么结构、谁是真源、哪些域允许进入后续流程，这些最容易混乱的规则全部定死。

如果没有第一层，后面会出现这些问题：

1. 不同模块自己拼不同的 key
2. 同一个域有人当 Hash 写，有人当 String 写
3. 有人把 Redis 当前值当真源，有人把 MySQL 当真源
4. 第三层和第四层自己再维护一套域范围



---

## 1. 当前第一层主要类

### 1.1 业务侧

| 类 | 作用 |
| --- | --- |
| `CacheKeyBuilder` | 统一生成逻辑 key |
| `CacheDomainRegistry` | 统一维护域清单、主线范围、重建范围、boot 范围 |
| `CacheDomainContract` | 描述单个域的完整契约 |
| `CacheSyncProperties` | 统一承接 `sync.*` 配置 |
| `CacheNamespaceConsistencyGuard` | 启动时确保业务侧和缓存侧命名空间一致 |

### 1.2 缓存侧

| 类 | 作用 |
| --- | --- |
| `CacheNamespaceProperties` | 定义物理命名空间前缀 |
| `NamespaceKeyResolver` | 逻辑 key 与物理 key 的双向转换 |
| `CacheController` | `beacon-cache` 对外暴露的 Redis 写删接口 |

### 1.3 调用桥梁

| 类 | 作用 |
| --- | --- |
| `BeaconCacheWriteClient` | `beacon-webmaster` 调用 `beacon-cache` 的 Feign 客户端 |

---

## 2. 先理解第一层最重要的三个概念

### 2.1 什么是“域”

“域”可以理解成“一类缓存数据”。

例如：

1. `client_business` 表示客户业务配置缓存
2. `client_channel` 表示客户的通道路由集合
3. `channel` 表示通道主数据
4. `client_balance` 表示客户余额镜像缓存

第一层的一个核心职责，就是把这些域逐个固定下来。

### 2.2 什么是“真源”

真源指的是：

> 当 MySQL 和 Redis 不一致时，最终到底以谁为准

当前主线域里，真源都是 MySQL。

这意味着：

1. Redis 只是加速读取
2. Redis 出错时，可以从 MySQL 重新恢复
3. 业务正确性最终看 MySQL，不看 Redis

### 2.3 什么是“域契约”

域契约就是把一个域的关键规则写清楚。

当前 `CacheDomainContract` 里固定了这些信息：

1. 域编码
2. 逻辑 key 模式
3. Redis 结构类型
4. 真源类型
5. 写入策略
6. 删除策略
7. 重建策略
8. owner service
9. 是否允许启动阶段重建

也就是说，后面的运行时同步、手工重建、启动校准，本质上都要先查契约，再决定怎么做。

---

## 3. 逻辑 key 和物理 key

### 3.1 逻辑 key

逻辑 key 是业务代码使用的 key。

例如：

1. `client_business:ak_1001`
2. `client_balance:1001`
3. `channel:3001`

它不带环境前缀。

### 3.2 物理 key

物理 key 是 Redis 里真正落库的 key。

例如：

1. `beacon:dev:beacon-cloud:cz:client_business:ak_1001`
2. `beacon:dev:beacon-cloud:cz:client_balance:1001`

### 3.3 当前代码里是谁负责转换

当前职责划分非常明确：

1. `CacheKeyBuilder` 只负责逻辑 key
2. `NamespaceKeyResolver` 负责把逻辑 key 转成物理 key
3. `CacheNamespaceProperties` 提供物理前缀配置

这套设计的好处是：

1. 业务代码不会直接拼物理前缀
2. 前缀切换不需要改业务逻辑
3. 所有环境隔离都集中在缓存侧做

---

## 4. 当前主线域契约

| 域 | 逻辑 key | 真源 | Redis 结构 | 写入策略 | 删除策略 | 手工重建 | 启动校准 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apiKey}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 支持 | 支持 |
| `client_channel` | `client_channel:{clientId}` | MySQL | Set | `DELETE_AND_REBUILD` | `DELETE_KEY` | 支持 | 支持 |
| `channel` | `channel:{id}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 支持 | 支持 |
| `client_balance` | `client_balance:{clientId}` | MySQL | Hash | `MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | `OVERWRITE_ONLY` | 支持 | 支持 |

### 4.1 为什么 `client_channel` 要删后重建

它不是一条记录，而是一组成员。

如果只增量写一个成员，Redis 里容易残留旧成员。

所以现在统一做法是：

1. 先查该客户的全量成员
2. 删除旧集合
3. 再按全量成员重建

### 4.2 为什么 `client_balance` 是 `OVERWRITE_ONLY`

余额是高风险数据。

当前代码不把“删 key”作为余额缓存的默认策略，原因是：

1. 删除会造成短时间空窗
2. 余额更适合用最新真源值直接覆盖 Redis
3. 真源仍是 MySQL，不需要通过删 key 来表达正确性

---

## 5. 当前哪些域不进入主线

这些域当前不进入第三层和第四层默认范围：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

这里的意思不是“代码不存在”，而是：

1. 它们不属于当前 MySQL 主线重建范围
2. 不作为当前架构说明的重点对象
3. 第三层 `ALL` 和第四层默认 boot 范围不会误带它们

---

## 6. 第一层如何影响后面三层

### 6.1 对第二层的影响

第二层做运行时同步时，不再自己定义：

1. 哪些域是主线域
2. key 怎么拼
3. 哪些域允许删 key

第二层直接复用第一层冻结好的规则。

### 6.2 对第三层的影响

第三层做手工重建时：

1. 先看 `CacheDomainRegistry`
2. 再看域契约
3. 最后决定哪些域能进入 `ALL`

因此第三层不会自己再维护第二套域清单。

### 6.3 对第四层的影响

第四层启动校准也同样如此：

1. 默认 boot 域来自注册表
2. boot 是否允许由域契约控制
3. 不会绕开第一层私自扩范围

---


## 7. 本层小结

第一层的职责可以总结成四句话：

1. 统一说清楚有哪些缓存域
2. 统一说清楚每个域在 Redis 里长什么样
3. 统一说清楚谁是真源，谁只是镜像
4. 为第二层、第三层、第四层提供同一套规则入口

如果把整套 MySQL -> Redis 同步架构比作一栋楼，第一层不是住人的那一层，而是地基和结构梁。

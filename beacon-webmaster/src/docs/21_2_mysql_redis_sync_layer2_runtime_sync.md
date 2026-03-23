# 第二层：运行时同步（架构说明）

## 0. 本层在整套架构中的位置

第二层处理的是“系统正常运行时”的同步问题。

简单说，它解决的是：

> 业务代码刚把 MySQL 改完，Redis 应该怎么尽快跟上。

这层不负责全量修复，也不负责启动时校准。

它只处理两种事：

1. 新数据写入 Redis
2. 旧缓存失效或重建

---

## 1. 当前第二层主要类

### 1.1 通用同步入口

| 类 | 作用 |
| --- | --- |
| `CacheSyncService` | 运行时同步总入口接口 |
| `CacheSyncServiceImpl` | 按域路由实际写 Redis |
| `CacheSyncRuntimeExecutor` | 决定“事务提交后再执行”还是“立即执行” |
| `CacheSyncLogHelper` | 统一输出同步日志 |

### 1.2 余额链路

| 类 | 作用 |
| --- | --- |
| `BalanceCommandService` | 统一余额命令服务接口 |
| `BalanceCommandServiceImpl` | 统一处理扣费、充值、调账 |
| `ClientBalanceMapper` | 余额真源访问接口 |
| `ClientBalanceMapper.xml` | 余额原子 SQL |

### 1.3 业务入口适配层

| 类 | 作用 |
| --- | --- |
| `ClientBusinessController` | 管理端充值入口最终委托余额命令服务 |
| `InternalBalanceController` | 内部扣费入口最终委托余额命令服务 |
| `AcountServiceImpl` | 老入口兼容层，内部也委托统一余额命令服务 |

---

## 2. 第二层做的事情，按步骤怎么理解

### 2.1 普通主线域的运行时同步

以 `client_business` 或 `channel` 为例，当前代码步骤是：

1. 业务层先更新 MySQL
2. 业务层决定需要刷新哪个域
3. 调用 `CacheSyncRuntimeExecutor.runAfterCommitOrNow(...)`
4. 如果当前有事务，就把同步动作挂到 `afterCommit`
5. 事务提交成功后，真正调用 `CacheSyncService.syncUpsert(...)`
6. `CacheSyncServiceImpl` 根据域契约判断：
   - key 怎么生成
   - 用 Hash 还是 Set
   - 是覆盖写，还是删后重建
7. 通过 `BeaconCacheWriteClient` 调用 `beacon-cache`
8. `beacon-cache` 把逻辑 key 转成物理 key，再写入 Redis

### 2.2 为什么经常要等事务提交成功后再刷 Redis

因为 MySQL 是真源。

如果事务还没提交，就先把 Redis 改了，会出现这种问题：

1. Redis 已经是新值
2. MySQL 事务最后回滚了
3. 结果缓存反而比数据库还“新”

所以当前策略是：

1. 有事务时，优先 `afterCommit`
2. 没事务时，再立即执行

对应类：

1. [CacheSyncRuntimeExecutor](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\support\CacheSyncRuntimeExecutor.java)

---

## 3. 第二层里最重要的特殊域：`client_balance`

余额和普通配置不同，它有三个特点：

1. 真源必须是 MySQL `client_balance` 表
2. 不允许“先读旧余额，再写新余额”这种非原子逻辑
3. 每次余额变化后，不只要刷新余额域，还要刷新客户主配置域

当前代码已经把这三点都落地了。

### 3.1 统一余额入口

当前余额入口统一落在：

1. `debitAndSync`
2. `rechargeAndSync`
3. `adjustAndSync`

对应类：

1. [BalanceCommandService](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\service\BalanceCommandService.java)
2. [BalanceCommandServiceImpl](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\service\impl\BalanceCommandServiceImpl.java)

### 3.2 原子 SQL

当前余额 SQL 是原子更新，不是“先查后改”：

1. `debitBalanceAtomic`
2. `rechargeBalanceAtomic`
3. `adjustBalanceAtomic`

对应文件：

1. [ClientBalanceMapper.java](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\java\com\cz\webmaster\mapper\ClientBalanceMapper.java)
2. [ClientBalanceMapper.xml](D:\Code\springcloud\beacon-cloud\beacon-cloud\beacon-webmaster\src\main\resources\mapper\ClientBalanceMapper.xml)

### 3.3 双域刷新

余额提交成功后，当前代码会统一刷新两个域：

1. `client_balance:{clientId}`
2. `client_business:{apiKey}`

对应方法在：

1. `BalanceCommandServiceImpl.scheduleBalanceDoubleRefresh(...)`

这么做的原因是：

1. `client_balance` 是余额镜像
2. `client_business` 里有些视图或业务路径也依赖客户整体配置状态

换句话说，余额变了，不只余额缓存可能受影响，客户总配置视图也可能受影响。

---

## 4. 如果只看代码，余额命令一次完整执行是怎样的

以充值为例，当前代码流程是：

1. 控制器收到请求
2. 组装 `ClientBalanceRechargeCommand`
3. 调用 `BalanceCommandService.rechargeAndSync(...)`
4. `BalanceCommandServiceImpl` 先执行 `rechargeBalanceAtomic`
5. SQL 返回成功后，再查最新 `ClientBalance`
6. 再查最新 `ClientBusiness`
7. 调用 `scheduleBalanceDoubleRefresh(...)`
8. 通过 `CacheSyncRuntimeExecutor` 把两个刷新动作挂到事务提交后
9. 提交成功后：
   - `cacheSyncService.syncUpsert(CLIENT_BALANCE, ...)`
   - `cacheSyncService.syncUpsert(CLIENT_BUSINESS, ...)`
10. `CacheSyncServiceImpl` 再把它们路由成真正的 Redis 写入动作

扣费和调账也是同样思路，只是前面的 SQL 不同。

---

## 5. 第二层如何处理不同类型的缓存

### 5.1 Hash 型域

典型域：

1. `client_business`
2. `channel`
3. `client_balance`

当前做法：

1. 生成 key
2. 把实体或 Map 转成字段集合
3. 走 `hmset`

### 5.2 Set 型域

典型域：

1. `client_channel`

当前做法不是增量写单个成员，而是：

1. 先构造全量快照
2. 删除旧 Set
3. 再把全量成员 `sadd` 回去

### 5.3 `client_balance` 为什么不是删后重建

因为余额不适合出现“短暂空值”。

当前策略是：

1. MySQL 成功后直接覆盖 Redis
2. 不默认删除余额 key

这在代码里体现为：

1. 删除策略是 `OVERWRITE_ONLY`
2. `syncDelete("client_balance", ...)` 会被跳过

---

## 6. 第二层与第三层、第四层的关系

第二层是后两层的基础。

原因是：

1. 第三层虽然能全量重建 Redis，但它最终回灌 Redis 时，仍然复用第二层已有的写入规则
2. 第四层虽然能在启动时自动重建，但底层重建引擎仍然复用第三层和第二层

所以可以把第二层理解成：

> “平时怎么写缓存”的标准实现

后面的第三层、第四层，本质上只是把这套“标准写法”放到不同触发场景里去用。

---


## 7. 本层小结

第二层的职责可以总结成三句话：

1. 正常业务请求里，只要 MySQL 改了，Redis 也要跟着改
2. 有事务时要等提交成功后再改 Redis
3. 余额域属于高风险写入，必须统一入口、原子 SQL、双域刷新

如果把整套架构比作一栋楼，第二层就是“日常通行的主楼层”。

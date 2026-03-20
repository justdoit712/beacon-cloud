# 第二层：运行时同步补齐（执行文档）

## 0. 目标

第二层的目标是止住新增漂移，只处理当前真实存在且可维护的主线域。

本层重点不是再扩范围，而是把余额链路补齐成真正可用状态。

### 0.1 文档引用规则

1. 本层沿用第一层冻结后的主线范围、命名空间口径、主线域档案和余额口径。
2. 本层只补齐运行时同步实施内容，不再重新定义第一层公共规则。
3. 若本层描述与第一层或总览矩阵冲突，以第一层和总览文档为准。

### 0.2 评审入口检查

第二层进入设计或代码评审前，至少先检查：

1. 是否仍然只处理 `client_business / client_channel / channel / client_balance`。
2. 是否继续复用 `CacheKeyBuilder`、`CacheSyncService`、`CacheSyncRuntimeExecutor`、`CacheDomainRegistry`。
3. 是否没有把非主线域重新拉回本轮核心范围。
4. 是否继续遵守余额统一入口与双域刷新口径。

---

## 1. 本层范围

### 1.1 纳入域

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

### 1.2 不纳入域

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

说明：

1. `black / dirty_word / transfer` 当前运行时同步继续保留。
2. 但它们不作为本轮 MySQL 主线范围来设计、验收和排期。

---

## 2. 当前状态

### 2.1 已经可用的部分

1. `client_business` 已接入 `save/update/delete` 同步。
2. `client_channel` 已按 `clientId` 查全量成员后删后重建。
3. `channel` 已接入 `save/update/delete` 同步。
4. `client_balance` 的扣费链路已支持 MySQL 提交后刷新 Redis。

### 2.2 当前缺口

1. 充值仍是“先读后写”，不是原子更新。
2. 充值路径当前不会同步刷新 `client_balance`。
3. 余额入口没有完全收口。
4. 旧余额更新路径仍可能绕过统一余额命令服务。

---

## 3. 本层固定决策

### 3.1 触发决策

1. 同步触发点只允许在 Service 层。
2. 有事务时统一 `afterCommit`。
3. 无事务时允许立即执行。
4. 当前阶段同步失败允许主流程继续，但必须保留清晰日志。

### 3.2 集合型 key 决策

1. `client_channel` 等集合型域必须传入全量快照。
2. 不允许用单条成员更新直接覆盖整组 key。

### 3.3 余额链路决策

1. 真源固定为 `client_balance` 表。
2. 所有余额变更统一走专门余额命令服务。
3. 每次余额提交后必须同时刷新：
   - `client_balance:{clientId}`
   - `client_business:{apiKey}`

---

## 4. 本层实施内容

### 4.1 统一余额命令入口

新增或收口到统一余额命令服务，例如：

1. `debitAndSync`
2. `rechargeAndSync`
3. `adjustAndSync`

要求：

1. `/pay` 不再自己读旧余额后拼新余额。
2. 后续新增余额入口不得绕过该服务。
3. 服务内部统一处理事务与缓存刷新。

### 4.2 补齐 MySQL 原子更新

1. 保留现有扣费原子 SQL。
2. 新增充值原子加款 SQL。
3. 如有手工调账需求，新增受控调账 SQL。

### 4.3 补齐双域刷新

余额事务提交成功后，统一 `afterCommit` 刷新：

1. `client_balance`
2. `client_business`

### 4.4 防止旧余额路径误用

统一要求：

1. 不允许再通过 `ClientBusinessService.update` 等通用客户更新路径承担余额修改职责
2. 所有余额变更必须统一委托余额命令服务

推荐优先采用“直接收口到统一余额命令服务”的方式，减少旧路径残留。

---

## 5. 代码改造点

1. `ClientBusinessController`
   - 将 `/pay` 迁移到统一余额命令服务
2. `ClientBalanceDebitServiceImpl`
   - 保留扣费
   - 扩展为统一余额命令服务的一部分或兼容层
3. `ClientBalanceMapper` / `ClientBalanceMapper.xml`
   - 增加充值原子 SQL
   - 按需增加调账 SQL
4. `BalanceCommandService` / `BalanceCommandServiceImpl`
   - 统一实现扣费、充值、调账命令
   - 在提交后补齐双域刷新
5. `CacheSyncServiceImpl`
   - 保持域契约不变
   - 配合余额链路补齐双域刷新

---

## 6. 测试要求

1. `client_business save/update/delete` 继续通过现有回归。
2. `client_channel` 在增删改后仍按全量快照重建。
3. `channel` 在增删改后仍正常刷新。
4. 充值并发下不再出现丢更新。
5. 余额任意变更后，`client_balance` 与 `client_business` 两域都刷新。
6. 旧余额路径无法通过通用客户更新接口绕过统一余额命令服务。

---

## 7. 验收标准

1. `client_business`、`client_channel`、`channel` 运行时同步稳定可用。
2. 余额入口完成统一收口。
3. `/pay` 不再使用“先读后写”方式更新余额。
4. 任一余额变更提交后，同时刷新 `client_balance` 与 `client_business`。
5. `client_balance` 达到进入第三层的前置条件。

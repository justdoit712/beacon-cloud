# 第三层：手工重建（执行文档）

## 0. 目标

第三层提供“可控兜底”能力，用于处理停机改库、历史脏数据和演示前校准。

本层只支持 MySQL 真源域，不处理当前仍是内存真源的 legacy 域。

### 0.1 文档引用规则

1. 本层沿用第一层冻结后的主线范围、主线域基础口径和 `client_balance` 前置条件。
2. `ALL` 的展开范围和 loader 纳入条件必须以总览矩阵为准。
3. 若本层描述与第一层或总览矩阵冲突，以第一层和总览文档为准。

### 0.2 评审入口检查

第三层进入设计或代码评审前，至少先检查：

1. 是否只有允许重建且属于本轮范围的域才会进入 `ALL`。
2. 是否没有把 `black / dirty_word / transfer` 写成 MySQL loader。
3. 是否继续复用第一层域档案与总览矩阵，而不是另起第二套总表。
4. 若纳入 `client_balance`，是否已经满足第二层前置条件。

---

## 1. 本层范围

### 1.1 首批支持域

1. `client_business`
2. `channel`
3. `client_channel`

### 1.2 条件支持域

1. `client_balance`
   - 仅在第二层余额链路完全收口后纳入

### 1.3 明确不支持域

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

---

## 2. 本层前置条件

1. 第一层已冻结公共约束。
2. 第二层已稳定运行主线域同步。
3. `client_balance` 若要纳入，本身必须先完成第二层验收。

---

## 3. 本层固定决策

### 3.1 `ALL` 的语义

`ALL` 不是注册表全部域，而是同时满足以下条件的域集合：

1. 允许重建
2. 已注册 loader
3. 属于本轮范围
4. 具有 MySQL 真源

### 3.2 冲突处理

采用“域级锁 + 脏标记补跑”：

1. 重建锁：`cache:rebuild:{domain}`
2. 脏标记：`cache:rebuild:dirty:{domain}`
3. 重建期间同域运行时同步只记脏标记，不直接写 Redis
4. 重建结束发现脏标记时该域立即补跑一次

### 3.3 报告决策

手工重建必须返回结构化报告，不允许只打日志不返回结果。

---

## 4. 本层新增能力

### 4.1 管理接口

1. `POST /admin/cache/rebuild?domain={domain}`
2. `POST /admin/cache/rebuild?domain=ALL`

### 4.2 核心组件

1. `CacheRebuildService`
2. `CacheRebuildController`
3. `DomainRebuildLoader`
4. 各域 loader 实现
5. `CacheRebuildReport`

---

## 5. 执行流程

1. 校验管理员权限。
2. 校验域参数。
3. 将 `ALL` 展开为实际域列表。
4. 逐域获取分布式锁。
5. 标记域进入重建中状态。
6. 清理当前命名空间下该域旧 key。
7. 从 MySQL 拉取全量快照。
8. 回灌 Redis。
9. 检查该域是否存在脏标记。
10. 若存在脏标记，立即补跑一次。
11. 生成结构化报告。

---

## 6. loader 设计要求

### 6.1 `client_business`

1. 拉取有效业务数据。
2. 输出 `client_business:{apikey}` -> hash。

### 6.2 `channel`

1. 拉取有效通道数据。
2. 输出 `channel:{id}` -> hash。

### 6.3 `client_channel`

1. 必须先按 `clientId` 聚合全量成员。
2. 每个 key 的 payload 必须是全量成员集。

### 6.4 `client_balance`

1. 真源固定为 `client_business.extend4`。
2. 第二层未完成前不允许实现和启用该 loader。

---

## 7. 报告字段

`CacheRebuildReport` 最少包含：

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

---

## 8. 测试要求

1. 单域重建成功。
2. `ALL` 只展开支持域。
3. 重建期间同域运行时同步不会直接覆盖 Redis。
4. 存在脏标记时会补跑一次。
5. 报告字段完整可追踪。
6. `client_balance` 在第二层完成前不会被 `ALL` 带上。

---

## 9. 验收标准

1. 支持单域重建。
2. 支持 `ALL` 且不越界。
3. 重建与运行时同步不会裸冲突。
4. 返回结构化报告，可定位失败 key。
5. 主线域的手工兜底能力可用。

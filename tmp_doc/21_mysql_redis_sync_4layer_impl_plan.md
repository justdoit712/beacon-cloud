# MySQL -> Redis 四层落地实施方案（总览）

## 0. 文档说明

本文件作为四层改造的总览入口，基于当前代码基线维护公共约束、范围边界和执行顺序。

基线时间：2026-03-16

分层执行文档：

1. `21_1_mysql_redis_sync_layer1_foundation.md`
2. `21_1_1_mysql_redis_sync_layer1_execution_checklist.md`
3. `21_2_mysql_redis_sync_layer2_runtime_sync.md`
4. `21_3_mysql_redis_sync_layer3_manual_rebuild.md`
5. `21_4_mysql_redis_sync_layer4_boot_reconcile.md`

执行顺序固定为：

1. 第一层：基础层收口
2. 第二层：运行时同步补齐
3. 第三层：手工重建
4. 第四层：启动校准

### 0.1 第一层定位

四层方案中，第一层的角色固定为“基础层收口”，不是独立功能层，也不是第三层、第四层的简化版。

第一层只负责三件事：

1. 冻结公共规则。
2. 冻结本轮边界。
3. 形成后续三层共同基线。

### 0.2 层间进入规则

为避免后续层建立在不稳定前提上，执行顺序增加以下硬约束：

1. 第一层未完成前，不进入第三层实施。
2. 第一层未完成前，不进入第四层实施。
3. 第二层、第三层、第四层若与第一层口径冲突，必须先回到第一层对齐。
4. 第一层的完成标志以“基线稳定”为准，不以“新增代码数量”为准。

### 0.3 本轮主线范围冻结结果

本轮主线范围在第一层冻结为：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

以下域移出当前主线：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

冻结后的范围约束如下：

1. 非主线域不进入第三层默认重建范围。
2. 非主线域不进入第四层默认启动校准范围。
3. “移出主线”不等于删除既有代码或既有缓存。
4. 若后续层要扩展范围，必须先回到第一层重新冻结边界。

### 0.4 命名空间规则冻结结果

命名空间相关规则在第一层冻结为：

1. 逻辑 key 只表达业务语义，不拼接物理前缀。
2. 物理 key 前缀以 `beacon-cache.cache.namespace.*` 为真源。
3. `beacon-webmaster.sync.redis.namespace` 只作为兼容配置和一致性校验配置。
4. 启用同步时，两边前缀必须一致。
5. 后续层不得新增第二套物理前缀真源。

### 0.5 主线域基础口径冻结结果

第一层冻结后的主线域基础口径如下：

| 域 | 逻辑 key | 真源 | Redis 结构 | 运行时写入策略 | 删除策略 | 第三层 | 第四层 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apiKey}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 纳入 | 纳入 |
| `client_channel` | `client_channel:{clientId}` | MySQL | Set | `DELETE_AND_REBUILD` | `DELETE_KEY` | 纳入 | 纳入 |
| `channel` | `channel:{id}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 纳入 | 纳入 |
| `client_balance` | `client_balance:{clientId}` | MySQL（`client_balance` 表） | Hash | `MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | `OVERWRITE_ONLY` | 第二层完成后纳入 | 第二层完成后纳入 |

说明：

1. 该表是后续层引用主线域档案的总入口。
2. 第二层、第三层、第四层不得在未更新第一层的情况下重定义这些基础口径。

### 0.6 文档统一表达规则

四层文档在第一层完成第 6 步后，统一按以下规则表达：

1. 第一层负责定义规则和边界，不再被写成功能补丁层。
2. 第二、第三、第四层只负责各自层的实施动作，不重定义第一层冻结规则。
3. “主线域”“非主线域”“逻辑 key”“物理 key”“真源”“镜像缓存”均沿用第一层术语定义。
4. 若分层文档与第一层冲突，必须先回到第一层对齐。

### 0.7 域状态矩阵使用规则

本文件中的“域状态矩阵”是本轮唯一总表入口，后续层使用规则如下：

1. 第二层据此判断运行时同步纳入范围。
2. 第三层据此判断 loader 和 `ALL` 展开范围。
3. 第四层据此判断 boot 默认域范围。
4. 后续层不得各自维护第二套总矩阵。

### 0.8 配置与发布检查摘要

第一层冻结后的配置与发布检查摘要如下：

1. `beacon-cache.cache.namespace.*` 是物理前缀真源。
2. `cache.namespace.fullPrefix` 在 `webmaster` 侧保留镜像值，用于启动期一致性校验。
3. `sync.redis.namespace` 是兼容配置、日志口径与校验配置。
4. `sync.enabled=true` 时，`sync.redis.namespace` 与 `cache.namespace.fullPrefix` 规范化后必须一致。
5. `sync.boot.domains` 不得默认带入非主线域。
6. `client_balance` 未完成第二层前，不得被默认加入 boot 域集合。

### 0.9 基础设施边界冻结结果

第一层冻结后的基础设施边界如下：

1. 逻辑 key 统一复用 `CacheKeyBuilder`。
2. 同步统一入口统一复用 `CacheSyncService` / `CacheSyncServiceImpl`。
3. 事务后执行统一复用 `CacheSyncRuntimeExecutor`。
4. 域规则统一复用 `CacheDomainRegistry` / `CacheDomainContract`。
5. Redis 写删通道统一复用 `BeaconCacheWriteClient` 与 `beacon-cache` 写删接口。
6. 物理 key 解析统一复用 `NamespaceKeyResolver` 与 `CacheNamespaceProperties`。
7. 后续层不得再新建第二套同职责基础设施。

### 0.10 后续层评审门槛摘要

第一层建立的后续层评审门槛如下：

1. 第二层先查是否发生范围漂移与余额入口漂移。
2. 第三层先查 `ALL` 语义、loader 纳入范围和非主线域误纳入问题。
3. 第四层先查 boot 默认域是否越界，以及是否复用了第三层能力。
4. 发布前统一检查命名空间配置与 boot 域配置。

### 0.11 第一层完成摘要

当第一层完成时，应同时成立：

1. 基础规则已冻结。
2. 文档体系已统一。
3. 总矩阵已固定。
4. 配置检查项已固定。
5. 基础设施边界已冻结。
6. 后续层评审门槛已建立。

---

## 1. 当前代码基线

### 1.1 已存在的基础能力

以下能力已经存在，不应重复开发：

1. `CacheKeyBuilder`
2. `CacheSyncService` / `CacheSyncServiceImpl`
3. `CacheSyncRuntimeExecutor`
4. `CacheDomainRegistry` / `CacheDomainContract`
5. `beacon-cache` 写删接口
6. `beacon-cache` 命名空间解析：`NamespaceKeyResolver`

### 1.2 已接入运行时同步的域

| 域 | 当前状态 | 说明 |
| --- | --- | --- |
| `client_business` | 已接入 | `save/update/delete` 已接入 |
| `client_channel` | 已接入 | 已按 `clientId` 聚合全量成员后写回 |
| `channel` | 已接入 | `save/update/delete` 已接入 |
| `client_balance` | 部分接入 | 仅扣费链路已接入 |
| `black` | 已接入 | 来自 `LegacyCrudServiceImpl` |
| `dirty_word` | 已接入 | 来自 `LegacyCrudServiceImpl`，按全量词集重建 |
| `transfer` | 已接入 | 来自 `LegacyCrudServiceImpl` |

### 1.3 当前仍未完成的部分

1. `client_sign` 未看到对应 mapper / service 写路径。
2. `client_template` 未看到对应 mapper / service 写路径。
3. `rebuildDomain` 目前仍是骨架实现。
4. `ApplicationRunner` 启动校准尚未落地。
5. `client_balance` 只覆盖了扣费链路。
6. `black / dirty_word / transfer` 当前真源是 `LegacyCrudServiceImpl` 内存存储，不属于本轮 MySQL 重建范围。

---

## 2. 公共固定决策

### 2.1 命名空间决策

1. `CacheKeyBuilder` 只生成逻辑 key，不拼接物理前缀。
2. 物理 key 真源配置在 `beacon-cache.cache.namespace.*`。
3. `beacon-webmaster.sync.redis.namespace` 当前只保留为兼容配置和校验配置，不作为物理 key 真源。
4. 若 `sync.enabled=true`，`beacon-webmaster.sync.redis.namespace` 必须与 `beacon-cache` 实际前缀保持一致。
5. 示例中的 owner 统一写成 `{owner}`，不再写死个人标识。

### 2.2 运行时同步决策

1. 触发点只允许在 Service 层。
2. 有事务时统一 `afterCommit`。
3. 无事务时允许立即执行。
4. 当前阶段同步失败允许主流程继续，但必须保留明确日志。
5. 余额域同步失败时必须输出补偿占位日志。

### 2.3 集合型 key 决策

1. `DELETE_AND_REBUILD` 类型域必须传入全量快照。
2. 不允许用单条增量直接覆盖整组 set。
3. `client_channel` 的“按 `clientId` 查全量成员再重建”模式作为标准模板。

### 2.4 `client_balance` 主口径决策

1. 真源固定为 `client_balance` 表。
2. 所有余额变更必须统一走专门余额命令服务。
3. 余额提交成功后必须同时刷新：
   - `client_balance:{clientId}`
   - `client_business:{apiKey}`

补充约束：

1. Redis `client_balance` 仅作为镜像缓存，不作为主账本。
2. 扣费、充值、退款、人工调账都属于余额变更，统一按同一套高风险写规则处理。
3. 真源写入必须先于镜像刷新。
4. 余额域默认不采用直接删 key 的处理方式。
5. 第三层、第四层只有在第二层余额链路统一完成后，才允许把 `client_balance` 纳入默认流程。

### 2.5 第三四层冲突处理决策

采用“域级锁 + 脏标记补跑”策略：

1. 重建锁：`cache:rebuild:{domain}`
2. 脏标记：`cache:rebuild:dirty:{domain}`
3. 重建期间同域运行时同步不直接写 Redis，而是记录脏标记
4. 重建结束发现脏标记时立即补跑一次

### 2.6 配置与发布检查摘要

发布前至少确认以下事项：

1. `beacon-cache` 侧命名空间配置能解析出唯一物理前缀。
2. `sync.redis.namespace` 与缓存侧实际物理前缀一致。
3. 本次发布所需的 `sync.runtime.enabled` / `sync.manual.enabled` / `sync.boot.enabled` 开关组合正确。
4. `sync.boot.domains` 不会误带非主线域。
5. 第二层未完成前，`sync.boot.domains` 不会误带 `client_balance`。

---

## 3. 域状态矩阵

| 域 | 真源 | Redis 类型 | 第二层 | 第三层 | 第四层 | 当前结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `client_business` | MySQL | Hash | 纳入 | 纳入 | 纳入 | 本轮主线 |
| `client_channel` | MySQL | Set(Map) | 纳入 | 纳入 | 纳入 | 本轮主线 |
| `channel` | MySQL | Hash | 纳入 | 纳入 | 纳入 | 本轮主线 |
| `client_balance` | MySQL(`client_balance` 表) | Hash | 纳入 | 第二层完成后纳入 | 第二层完成后纳入 | 本轮主线，但有前置条件 |
| `client_sign` | 未落地 | Set | 不纳入 | 不纳入 | 不纳入 | 移出当前 P0 |
| `client_template` | 未落地 | Set | 不纳入 | 不纳入 | 不纳入 | 移出当前 P0 |
| `black` | 内存 | String | 维持现状 | 不纳入 | 不纳入 | 暂不进入 MySQL 主线 |
| `dirty_word` | 内存 | Set | 维持现状 | 不纳入 | 不纳入 | 暂不进入 MySQL 主线 |
| `transfer` | 内存 | String | 维持现状 | 不纳入 | 不纳入 | 暂不进入 MySQL 主线 |

---

## 4. 文档导航

### 第一层

文件：`21_1_mysql_redis_sync_layer1_foundation.md`

关注点：

1. 基础能力收口
2. 范围冻结
3. 文档与代码基线对齐
4. 详细实施清单见：`21_1_1_mysql_redis_sync_layer1_execution_checklist.md`

### 第二层

文件：`21_2_mysql_redis_sync_layer2_runtime_sync.md`

关注点：

1. 运行时同步补齐
2. 余额链路统一收口
3. 双域刷新

### 第三层

文件：`21_3_mysql_redis_sync_layer3_manual_rebuild.md`

关注点：

1. 手工重建接口
2. loader 模型
3. 重建报告
4. 锁与脏标记补跑

### 第四层

文件：`21_4_mysql_redis_sync_layer4_boot_reconcile.md`

关注点：

1. 启动校准 runner
2. 域选择规则
3. 启动期保护与失败处理

---

## 5. 建议实施顺序

1. 第 1 天：完成第一层收口，冻结范围与公共决策。
2. 第 2-3 天：完成第二层余额链路补齐，迁移 `/pay`，补齐双域刷新。
3. 第 4-5 天：完成第三层重建基础设施与 `client_business / channel / client_channel` loader。
4. 第 6 天：在第二层通过后补上 `client_balance` rebuild loader。
5. 第 7 天：完成第四层启动校准与回归测试。

---

## 6. 最终 Done

1. 文档与代码状态一致。
2. `client_business`、`client_channel`、`channel` 运行时同步稳定可用。
3. `client_balance` 所有余额变更入口完成统一收口。
4. 任一余额变更提交后，同时刷新 `client_balance` 与 `client_business`。
5. 手工重建支持 `client_business`、`channel`、`client_channel`，并返回结构化报告。
6. `client_balance` 仅在第二层完成后被纳入手工重建与启动校准。
7. 启动校准复用手工重建核心逻辑，支持开关、锁保护与失败定位。
8. `client_sign / client_template / black / dirty_word / transfer` 不会被 `ALL` 或 boot 默认误纳入。

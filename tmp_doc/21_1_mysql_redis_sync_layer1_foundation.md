# 第一层：基础层收口（执行文档）

## 0. 目标

第一层不是重新开发一套基础设施，而是把当前已经存在的能力收口成统一口径，为后续三层提供稳定基线。

本层完成前，不进入第三、四层开发。

第一层详细实施清单见：

1. `21_1_1_mysql_redis_sync_layer1_execution_checklist.md`

### 0.0 当前状态说明

截至当前代码状态，第一层“基础层收口”已经基本落地完成。

当前已完成的关键收口包括：

1. 主线 4 域边界已在代码中显式表达。
2. `ALL` 已从“注册表全部域”收紧为“当前允许范围”。
3. `sync.redis.namespace` 与 `cache.namespace.fullPrefix` 的一致性已升级为启动期代码护栏。

本文件后续章节中的“当前问题”“待办”“执行清单”等内容，主要用于保留治理背景与规则来源；
第一层是否完成，应以本文件冻结口径和当前代码护栏为准，而不再按推进过程字面理解。

### 0.1 第一层角色定义

第一层在四层方案中的角色固定为“基础层收口”，它负责统一以下内容：

1. 规则：统一命名、真源、范围、职责口径。
2. 边界：明确本轮主线纳管对象与非主线对象。
3. 基线：为第二层、第三层、第四层提供共同前提。

第一层不承担以下职责：

1. 不把第三层“手工重建”提前并入本层。
2. 不把第四层“启动校准”提前并入本层。
3. 不把“多写几个基础类”误当成第一层完成。

### 0.2 层间约束

第一层完成前，四层之间的推进约束固定如下：

1. 第一层负责冻结公共规则和公共边界。
2. 第二层只能在第一层冻结口径基础上继续补齐运行时同步。
3. 第三层与第四层不得在第一层未完成时进入实施。
4. 若第二层、第三层、第四层与第一层口径冲突，必须先回到第一层重新对齐。

### 0.3 第一层完成标志

第一层的完成标志不是“新增了多少类”，而是以下三件事成立：

1. 团队对第一层角色没有歧义。
2. 后续层都引用同一套基础口径。
3. 第三层与第四层不会绕开第一层直接推进。

---

## 1. 当前基线

### 1.1 已存在能力

1. `CacheKeyBuilder`
2. `CacheSyncService` / `CacheSyncServiceImpl`
3. `CacheSyncRuntimeExecutor`
4. `CacheDomainRegistry` / `CacheDomainContract`
5. `BeaconCacheWriteClient`
6. `beacon-cache` 的 `/cache/delete/{key}` 与 `/cache/delete/batch`
7. `NamespaceKeyResolver`

### 1.2 当前问题

1. 旧方案仍把部分已存在能力写成“待新增”。
2. `client_sign / client_template` 被写进 P0，但当前看不到实际写路径。
3. `black / dirty_word / transfer` 虽有运行时同步，但不属于 MySQL 重建主线。
4. `beacon-webmaster.sync.redis.namespace` 与 `beacon-cache.cache.namespace.*` 的职责容易混淆。

---

## 2. 本层固定决策

### 2.1 命名空间口径

1. 逻辑 key 由 `CacheKeyBuilder` 生成。
2. 物理前缀以 `beacon-cache.cache.namespace.*` 为真源。
3. `beacon-webmaster.sync.redis.namespace` 只保留为兼容配置和日志校验配置。
4. 若启用同步，两边前缀必须保持一致。

### 2.1.1 逻辑 key 与物理 key 的职责划分

第一层冻结后的职责划分如下：

1. 逻辑 key 只表达业务语义，例如 `client_business:{apiKey}`、`channel:{channelId}`。
2. 逻辑 key 不负责表达环境、项目、owner 等隔离信息。
3. 物理 key 负责在逻辑 key 之前拼接命名空间前缀。
4. 命名空间前缀负责环境隔离、项目隔离和实例间约定，不负责改变业务语义。

### 2.1.2 命名空间真源定义

命名空间相关职责在第一层固定如下：

1. 业务侧只负责生成逻辑 key。
2. 缓存侧负责把逻辑 key 解析为物理 key。
3. `beacon-cache.cache.namespace.*` 是物理前缀真源。
4. `beacon-webmaster.sync.redis.namespace` 不是物理前缀真源。

### 2.1.3 兼容配置的含义

`beacon-webmaster.sync.redis.namespace` 在本轮中的角色固定为：

1. 兼容旧配置。
2. 作为同步链路日志展示口径。
3. 作为与缓存侧前缀进行一致性校验的比对值。

它在本轮中不承担以下职责：

1. 不负责决定最终写入 Redis 的物理前缀。
2. 不允许和 `beacon-cache.cache.namespace.*` 形成双真源。

### 2.1.4 一致性约束

命名空间冻结后，必须遵守以下约束：

1. 启用同步时，业务侧兼容前缀必须与缓存侧物理前缀保持一致。
2. 文档、日志、排查说明必须能区分“逻辑 key”和“物理 key”。
3. 后续层不得再次引入新的物理前缀真源配置。
4. 若后续层发现前缀规则需要调整，必须先回到第一层更新命名空间口径。

### 2.1.5 本步固定结论

完成第 3 步后，命名空间相关结论统一为：

1. `CacheKeyBuilder` 只负责逻辑 key。
2. `beacon-cache.cache.namespace.*` 才是物理 key 真源。
3. `beacon-webmaster.sync.redis.namespace` 只是兼容配置和校验配置。
4. 启用同步时，两边前缀必须一致。
5. 后续所有文档和设计都必须按这套职责边界表达命名空间。

### 2.2 范围口径

当前主线范围只保留：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

当前移出主线：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

说明：

1. `black / dirty_word / transfer` 维持现状，不阻塞本轮主线。
2. 它们不能被写成“第三层从 MySQL 全量重建”的对象。

### 2.2.1 主线范围冻结结果

本轮主线范围正式冻结为以下四个域：

1. `client_business`
2. `client_channel`
3. `channel`
4. `client_balance`

第一层之后，第二层、第三层、第四层默认只围绕这四个域展开，不得在没有重新评审第一层的前提下扩展主线范围。

### 2.2.2 移出主线的含义

以下域正式移出当前主线：

1. `client_sign`
2. `client_template`
3. `black`
4. `dirty_word`
5. `transfer`

“移出主线”在本轮中的含义固定如下：

1. 不纳入本轮主线实施目标。
2. 不作为第三层手工重建默认对象。
3. 不作为第四层启动校准默认对象。
4. 不等于删除既有代码。
5. 不等于删除既有缓存。
6. 不等于否定这些域未来继续治理的可能性。

### 2.2.3 后续层约束

范围冻结后，后续层必须遵守以下边界：

1. 第二层不得把非主线域重新包装为“本轮核心同步对象”。
2. 第三层不得把 `black / dirty_word / transfer` 写成 MySQL loader 或默认 rebuild 域。
3. 第四层不得把 `client_sign / client_template / black / dirty_word / transfer` 写入默认 boot reconcile 范围。
4. 若后续层确实要扩展范围，必须先回到第一层更新范围口径，再继续推进。

### 2.2.4 主线域基础口径总表

| 域 | 逻辑 key | 真源 | Redis 结构 | 运行时写入策略 | 删除策略 | 第三层 | 第四层 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `client_business` | `client_business:{apiKey}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 纳入 | 纳入 |
| `client_channel` | `client_channel:{clientId}` | MySQL | Set（按 `clientId` 聚合后的客户通道绑定集合） | `DELETE_AND_REBUILD` | `DELETE_KEY` | 纳入 | 纳入 |
| `channel` | `channel:{id}` | MySQL | Hash | `WRITE_THROUGH` | `DELETE_KEY` | 纳入 | 纳入 |
| `client_balance` | `client_balance:{clientId}` | MySQL（`client_business.extend4`） | Hash | `MYSQL_ATOMIC_UPDATE_THEN_REFRESH` | `OVERWRITE_ONLY` | 第二层完成后纳入 | 第二层完成后纳入 |

说明：

1. 本表是第一层冻结后的主线域基础档案总表。
2. 后续层文档不得再单独改变上述 key 形态、真源、Redis 结构、写入策略、删除策略和第三四层纳入条件。
3. `client_balance` 的详细业务口径在下一个小节单独展开。

### 2.2.5 `client_business` 基础口径

1. 业务含义：客户总业务配置与策略入口。
2. 逻辑 key：`client_business:{apiKey}`。
3. 真源：MySQL。
4. Redis 结构：Hash。
5. 运行时写入策略：`WRITE_THROUGH`。
6. 删除策略：`DELETE_KEY`。
7. 第三层：纳入手工重建范围。
8. 第四层：纳入启动校准范围。

### 2.2.6 `client_channel` 基础口径

1. 业务含义：按客户聚合后的通道绑定关系集合。
2. 逻辑 key：`client_channel:{clientId}`。
3. 真源：MySQL。
4. Redis 结构：Set。
5. 运行时写入策略：`DELETE_AND_REBUILD`，必须按 `clientId` 拉取全量快照后重建整组集合。
6. 删除策略：`DELETE_KEY`。
7. 第三层：纳入手工重建范围。
8. 第四层：纳入启动校准范围。

### 2.2.7 `channel` 基础口径

1. 业务含义：通道主数据详情。
2. 逻辑 key：`channel:{id}`。
3. 真源：MySQL。
4. Redis 结构：Hash。
5. 运行时写入策略：`WRITE_THROUGH`。
6. 删除策略：`DELETE_KEY`。
7. 第三层：纳入手工重建范围。
8. 第四层：纳入启动校准范围。

### 2.2.8 `client_balance` 基础口径

1. 业务含义：客户余额镜像缓存。
2. 逻辑 key：`client_balance:{clientId}`。
3. 真源：MySQL（`client_business.extend4`）。
4. Redis 结构：Hash。
5. 运行时写入策略：`MYSQL_ATOMIC_UPDATE_THEN_REFRESH`。
6. 删除策略：`OVERWRITE_ONLY`。
7. 第三层：第二层余额链路统一收口完成后纳入。
8. 第四层：第二层余额链路统一收口完成后纳入。

### 2.2.9 主线域引用规则

主线域基础口径冻结后，后续层必须遵守以下规则：

1. 第二层只能补齐写入路径，不得重定义主线域档案。
2. 第三层只能基于本表决定 loader 和 rebuild 范围，不得私自扩展非主线域。
3. 第四层只能基于本表决定 boot reconcile 范围，不得绕开前置条件纳入 `client_balance`。
4. 若要变更主线域档案，必须先回到第一层更新基础口径。

### 2.3 `client_balance` 详细业务口径

1. 真源固定为 `client_business.extend4`。
2. Redis 仅作为镜像缓存。
3. 任何余额变更不能绕过专门余额命令服务。

### 2.3.1 余额真源定义

余额域的真源在第一层固定为：

1. MySQL `client_business.extend4`。
2. Redis `client_balance:{clientId}` 不作为主账本。
3. 任意时刻若 MySQL 与 Redis 显示不一致，以 MySQL 为准。

### 2.3.2 余额变更范围定义

以下动作在本轮中统一视为“余额变更”：

1. 扣费。
2. 充值。
3. 退款。
4. 人工调账。
5. 其他任何会改变客户余额最终值的操作。

这些动作虽然业务语义不同，但在第一层口径上都属于同一类高风险写操作，必须受同一套余额规则约束。

### 2.3.3 统一入口约束

余额域冻结后，必须遵守以下入口规则：

1. 任何余额变更必须统一走专门余额命令服务。
2. 不允许把余额更新散落到通用客户更新路径中。
3. 不允许把 Redis 写入当作余额写入入口。
4. 不允许不同业务动作各自定义第二套余额写法。

### 2.3.4 读写分工

余额域的读写职责在第一层固定如下：

1. 写入决策以 MySQL 结果为准。
2. Redis 只承担热路径读取和镜像展示职责。
3. 运行链路即使先读 Redis 做快速判断，最终余额是否扣减成功仍以 MySQL 命令结果为准。
4. 不允许把“缓存里现在是多少”直接等同为“主账本最终结果”。

### 2.3.5 提交顺序与刷新规则

余额变更的顺序在第一层固定如下：

1. 先写 MySQL 真源。
2. 真源提交成功后，再刷新 Redis 镜像。
3. 余额变更提交成功后，至少要保证 `client_balance:{clientId}` 被纳入刷新范围。
4. 若该余额变更同时影响客户总配置视图，则还必须刷新 `client_business:{apiKey}`。

### 2.3.6 删除与重建约束

余额域在第一层采用更保守的约束：

1. 不把直接删 `client_balance` key 当成默认操作。
2. 余额域删除策略固定为 `OVERWRITE_ONLY`。
3. 第三层、第四层若要纳入余额域，前提是第二层先完成余额入口统一与刷新口径统一。

### 2.3.7 余额域固定结论

完成第 5 步后，余额域相关结论统一为：

1. MySQL 是唯一真源。
2. Redis 是镜像缓存。
3. 所有余额变更属于同一类高风险写操作。
4. 所有余额变更必须走统一余额命令入口。
5. 真源成功后再刷新镜像。
6. 第三层、第四层只有在第二层余额链路统一完成后才允许把余额域纳入默认流程。

### 2.4 文档统一表达口径

第一层完成第 6 步后，整套文档的表达规则固定如下：

1. “第一层：基础层收口”只用于描述规则冻结、边界冻结和基线冻结，不再被写成业务功能层。
2. “主线域”固定指：
   - `client_business`
   - `client_channel`
   - `channel`
   - `client_balance`
3. “非主线域”固定指：
   - `client_sign`
   - `client_template`
   - `black`
   - `dirty_word`
   - `transfer`
4. “逻辑 key”固定指不带命名空间前缀的业务 key。
5. “物理 key”固定指实际写入 Redis、带命名空间前缀的 key。
6. “真源”固定指业务上最终以谁为准。
7. “镜像缓存”固定指跟随真源刷新的 Redis 表达，不等于主账本。

### 2.4.1 文档职责划分

第一层之后，各文档职责固定如下：

1. 总览文档负责公共决策、域状态矩阵和分层顺序。
2. 第一层执行文档负责冻结基础规则和边界。
3. 第一层实施清单负责保留执行步骤、完成判定和过程留档。
4. 第二、第三、第四层文档只负责各自层的实施内容，不再重复定义第一层规则。

### 2.4.2 文档冲突时的优先级

若多份文档对同一问题出现冲突，优先级固定为：

1. 第一层执行文档
2. 总览文档
3. 第二、第三、第四层执行文档
4. 第一层实施清单

说明：

1. 第一层实施清单负责记录“怎么推进”，不覆盖第一层执行文档中的冻结规则。
2. 后续层执行文档若与第一层冲突，必须回到第一层修订后再继续推进。

### 2.5 域状态矩阵引用口径

域状态矩阵在第一层完成第 7 步后，使用规则固定如下：

1. 总览文档中的“域状态矩阵”是本轮唯一总表入口。
2. 第一层执行文档负责解释矩阵背后的范围口径和纳入条件。
3. 第二层、第三层、第四层只引用矩阵，不再生成第二套总表。
4. 若矩阵与任一分层文档冲突，先修正分层文档，再决定是否需要回到第一层调整矩阵。

### 2.6 配置口径与发布检查项

第一层完成第 8 步后，同步相关配置口径固定如下：

| 配置项 | 所属模块 | 角色 | 第一层固定口径 |
| --- | --- | --- | --- |
| `cache.namespace.enabled` | `beacon-cache` | 物理命名空间开关 | 启用同步时应保持可解析出稳定物理前缀 |
| `cache.namespace.fullPrefix` | `beacon-cache` / `beacon-webmaster` | 物理前缀真源配置 / 启动校验镜像配置 | 缓存侧为物理前缀真源；业务侧保留同值镜像，仅用于启动期一致性校验 |
| `sync.enabled` | `beacon-webmaster` | 同步总开关 | 关闭时 runtime/manual/boot 子开关不生效 |
| `sync.redis.namespace` | `beacon-webmaster` | 兼容配置、日志口径与校验配置 | 启用同步时必须非空，且必须与 `cache.namespace.fullPrefix` 规范化后保持一致 |
| `sync.runtime.enabled` | `beacon-webmaster` | 运行时同步开关 | 第二层主线开关 |
| `sync.manual.enabled` | `beacon-webmaster` | 手工重建开关 | 第三层管理入口开关 |
| `sync.boot.enabled` | `beacon-webmaster` | 启动校准开关 | 第四层开关，默认不要求第一层启用 |
| `sync.boot.domains` | `beacon-webmaster` | 启动校准候选域清单 | 不得默认包含非主线域，且 `client_balance` 必须满足第二层前置条件后才可纳入 |

### 2.6.1 发布前检查清单

发布前至少执行以下检查：

1. 确认 `beacon-cache` 侧能解析出唯一物理前缀。
2. 确认 `webmaster` 侧配置了与缓存侧一致的 `cache.namespace.fullPrefix`，用于启动期一致性校验。
3. 确认 `sync.enabled=true` 时，`sync.redis.namespace` 非空且与 `cache.namespace.fullPrefix` 规范化后保持一致。
4. 确认 `sync.runtime.enabled`、`sync.manual.enabled`、`sync.boot.enabled` 的组合符合本次发布目标。
5. 确认 `sync.boot.domains` 未误带 `client_sign / client_template / black / dirty_word / transfer`。
6. 确认 `sync.boot.domains` 在第二层未完成前不会误带 `client_balance`。
7. 确认文档、配置示例和发布说明对命名空间的描述一致。

### 2.7 基础设施边界冻结

第一层完成第 9 步后，基础设施边界固定如下：

1. 逻辑 key 规则统一复用 `CacheKeyBuilder`。
2. 同步统一入口统一复用 `CacheSyncService` / `CacheSyncServiceImpl`。
3. 事务提交后执行与立即执行统一复用 `CacheSyncRuntimeExecutor`。
4. 域契约统一复用 `CacheDomainRegistry` / `CacheDomainContract`。
5. Redis 写删通道统一复用 `BeaconCacheWriteClient` 与 `beacon-cache` 写删接口。
6. 物理 key 解析统一复用 `NamespaceKeyResolver` 与 `CacheNamespaceProperties`。

### 2.7.1 后续层允许新增的能力

在不突破基础设施边界的前提下，后续层允许新增：

1. 第二层的余额命令服务、原子 SQL、保护逻辑。
2. 第三层的 `CacheRebuildService`、`DomainRebuildLoader`、`CacheRebuildReport`。
3. 第四层的 `CacheBootReconcileRunner`。
4. 围绕既有基础设施的控制器、报告模型、日志增强与测试代码。

### 2.7.2 后续层禁止新增的第二套能力

后续层不得再设计第二套：

1. key builder。
2. 同步门面。
3. 域注册表。
4. 事务后执行器。
5. 物理前缀真源。
6. 与第一层同职责但不同入口的同步框架。

### 2.7.3 边界冻结后的判断规则

若某个新设计同时满足以下任一情况，应视为越界：

1. 它在重新定义逻辑 key 生成职责。
2. 它在绕过统一同步门面直连形成第二条主同步链路。
3. 它在重新维护第二套域规则来源。
4. 它在第一层已定义职责之外再次决定物理前缀。

### 2.8 后续层评审门槛

第一层完成第 10 步后，后续层评审必须先通过以下入口检查。

### 2.8.1 第二层评审入口

第二层评审至少先检查：

1. 是否仍然只处理主线四个域。
2. 是否复用了第一层冻结的 key 规则、同步门面、执行器和域契约。
3. 是否把所有余额变更继续收口到统一余额命令入口。
4. 是否没有把非主线域重新包装成“本轮核心同步对象”。

### 2.8.2 第三层评审入口

第三层评审至少先检查：

1. `ALL` 是否仍然不是“注册表全部域”。
2. loader 是否只面向允许重建且属于本轮范围的域。
3. 是否没有把 `black / dirty_word / transfer` 写成 MySQL loader。
4. 是否复用了第一层域档案与总览矩阵，而不是重新维护第二套域总表。

### 2.8.3 第四层评审入口

第四层评审至少先检查：

1. 是否复用了第三层重建核心逻辑。
2. 默认 boot 域是否与总览矩阵一致。
3. 是否未在第二层完成前默认纳入 `client_balance`。
4. 是否没有重新设计第二套 loader 或第二套重建规则。

### 2.8.4 发布前总检查入口

发布前至少先检查：

1. 第一层冻结口径是否仍然有效。
2. 总览矩阵与第二、第三、第四层文档是否一致。
3. 命名空间前缀与 boot 域配置是否符合第一层检查清单。

### 2.9 第一层完成标准

第一层完成不是指“清单 11 步都写过”，而是指以下结果已经稳定成立：

1. 第一层定位、范围、命名空间、主线域档案、余额规则、矩阵、配置检查项已冻结。
2. 总览文档与第二、第三、第四层文档已经引用同一套第一层规则。
3. 基础设施边界已经明确，后续层不会再重复建设第二套框架。
4. 后续层评审门槛已经建立，可以据此卡住范围漂移和职责漂移。
5. 团队对“哪些能力已存在、哪些仍待实现”没有歧义。

### 2.9.1 第一层完成后的阅读入口

第一层完成后，文档阅读入口固定如下：

1. 看基础规则，优先看本文件。
2. 看公共决策和总矩阵，优先看总览文档。
3. 看执行过程与历史推进记录，查看第一层实施清单。

---

## 3. 本层产出

1. 公共约束在总览文档冻结。
2. 第一层执行文档冻结基础口径。
3. 第二、三、四层文档基于同一基线拆分。
4. 域状态矩阵与当前代码状态一致。
5. 主线四个域的基础档案在第一层冻结。
6. `client_balance` 的详细业务口径在第一层冻结。
7. 整套文档表达口径完成统一。
8. 域状态矩阵完成统一引用。
9. 配置口径与发布检查项完成冻结。

---

## 4. 本层待办

### 4.1 文档整理

1. 不再把已存在类写成待开发。
2. 不再把未落地的域写成当前 P0。
3. 不再把 legacy 内存域写成 MySQL 重建域。

### 4.2 配置口径整理

1. 明确 `beacon-cache` 才是物理 key 真源。
2. 明确 `beacon-webmaster.sync.redis.namespace` 只是兼容配置。
3. 部署时增加“一致性检查项”。

### 4.3 代码边界冻结

1. 基础层不重复创建新的 key builder / sync facade。
2. 后续实现统一复用：
   - `CacheKeyBuilder`
   - `CacheSyncService`
   - `CacheSyncRuntimeExecutor`
   - `CacheDomainRegistry`

---

## 5. 执行清单

1. 确认所有后续任务都引用本层固定口径。
2. 评审第二层设计时，先检查是否有范围漂移。
3. 评审第三四层设计时，先检查是否把 legacy 域误纳入重建。
4. 发布前检查两边命名空间配置是否一致。

---

## 6. 验收标准

1. 第一层定位、范围、命名空间、主线域档案、余额口径已经冻结。
2. 域状态矩阵已经形成唯一总表入口。
3. 配置口径和发布检查项已经形成明确清单。
4. 基础设施边界已经明确，后续开发不再重复建设第二套同步框架。
5. 第二层、第三层、第四层都有明确的评审入口检查。
6. 团队对“哪些能力已存在、哪些仍待实现”没有歧义。
7. 所有后续方案都以本层冻结口径为准。

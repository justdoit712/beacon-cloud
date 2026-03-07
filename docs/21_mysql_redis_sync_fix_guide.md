# MySQL 到 Redis 同步修复方案（快速修 + 稳健修）

## 0. 结论先行

当前项目确实存在核心问题：**MySQL 到 Redis 不是实时自动同步，而是依赖 `beacon-test` 下测试类手动执行“导数脚本”**。  
这会导致业务配置在 MySQL 已变更但 Redis 未更新，从而出现接口校验、策略执行、路由下发与实际配置不一致的问题。

建议采用“两阶段落地”：

1. **快速修（止血）**：在现有写业务流程中补齐同步 Redis，先解决线上一致性风险。
2. **稳健修（彻底）**：引入事件驱动同步链路（Outbox/CDC + MQ + 消费者 + 重试/幂等/监控），形成长期可治理能力。

说明：下文将“文件修”按“稳健修”理解。

---

## 1. 项目当前问题

## 1.1 现状

当前主业务模块（`beacon-api`、`beacon-strategy`、`beacon-smsgateway`）主要是**读 Redis**，并不负责把 MySQL 变更实时同步到 Redis。  
MySQL 到 Redis 的写入动作主要在 `beacon-test/src/test/java` 的测试类中完成，例如：

1. `ClientBusinessMapperTest`：`client_business:{apikey}`  
2. `ClientBalanceMapperTest`：`client_balance:{clientId}`  
3. `ClientSignMapperTest`：`client_sign:{clientId}`  
4. `ClientTemplateMapperTest`：`client_template:{signId}`  
5. `ClientChannelMapperTest`：`client_channel:{clientId}`  
6. `ChannelMapperTest`：`channel:{id}`  
7. `DirtyWordMapperTest`：`dirty_word`  
8. `MobileBlackMapperTest`：`black:{mobile}` / `black:{clientId}:{mobile}`  
9. `MobileTransferMapperTest`：`transfer:{mobile}`

这是一种“脚本化预热”模式，不是“业务写入即自动同步”模式。

## 1.2 风险

1. **强一致性缺失**：MySQL 和 Redis 数据容易漂移，业务判断基于过期缓存。  
2. **运维依赖人工**：变更后要人手触发脚本，容易漏、晚、错。  
3. **问题难审计**：缺少同步流水、失败重试与监控，排障成本高。  
4. **高风险业务受影响**：余额、黑名单、签名、模板、路由等关键配置可能失真。

## 1.3 影响链路

1. API 校验链：`apikey/ip/sign/template/fee` 依赖 Redis。  
2. 策略链：黑名单、敏感词、限流、扣费、路由依赖 Redis。  
3. 网关回调：回调开关和 URL 依赖 Redis。

---

## 2. 快速修实施方案（止血方案，建议 1~2 周）

## 2.1 目标

在不大改架构的前提下，实现“**写 MySQL 后立即同步 Redis**”，快速消除手工脚本依赖。

## 2.2 核心思路

1. 在后台管理写路径（新增/修改/删除）中统一接入缓存同步。  
2. DB 事务提交成功后再执行缓存更新，避免“DB 回滚但缓存已更新”。  
3. 采用“写穿 + 删除失效”策略：
   - 可直接覆盖的数据（如 `client_business`、`channel`）：写穿到 Redis。
   - 难以局部精确更新的数据（如集合关系变更）：删除对应 key，触发后续重建或立即重建。
4. 增加“一键全量重建缓存”任务，处理历史脏数据与紧急兜底。

## 2.3 代码落地建议

### 2.3.1 新增统一缓存同步服务

建议在 `beacon-webmaster` 新增：

1. `service/CacheSyncService`（接口）  
2. `service/impl/CacheSyncServiceImpl`（实现）  
3. 通过 Feign 调 `beacon-cache`（复用鉴权头）

职责：

1. 按业务实体生成 Redis key。  
2. 执行 `hmset/set/sadd/saddstr/delete`。  
3. 屏蔽上层业务对缓存细节感知。

### 2.3.2 在关键写业务中接入同步

优先改造数据域（按风险排序）：

1. `client_business`  
2. `client_balance`  
3. `client_sign`  
4. `client_template`  
5. `client_channel`  
6. `channel`  
7. `mobile_black`  
8. `mobile_transfer`  
9. `mobile_dirtyword`

接入点：`beacon-webmaster` 各 Service 的 `save/update/delete` 成功路径。  
建议使用事务提交回调触发缓存同步，避免脏写。

### 2.3.3 增加全量重建入口

建议新增管理任务（可手工触发）：

1. 按数据域从 MySQL 全量拉取。  
2. 重建对应 Redis key。  
3. 产出执行报告（成功数、失败数、失败 key）。

## 2.4 验收标准

1. 管理端改一个客户配置后，Redis 对应 key 秒级更新。  
2. 不再需要运行 `beacon-test` 测试类进行日常同步。  
3. 关键链路（API 校验、策略路由）对配置变更实时生效。  
4. 提供一次全量重建能力并验证可用。

## 2.5 快速修能否解决当前问题

**能。**  
快速修可以直接解决当前最核心的问题：不再依赖手工脚本，同步由业务流程自动完成。

但快速修的边界是：同步失败重试、可追踪性、统一治理能力仍然较弱。

---

## 3. 稳健修实施方案（彻底方案，建议 4~8 周）

## 3.1 目标

把“缓存同步”从业务代码中的附属逻辑，升级为“事件驱动的数据同步系统”，做到可重试、可回放、可审计、可监控。

## 3.2 推荐架构

`MySQL 事务` -> `Outbox 事件表` -> `事件投递器` -> `MQ` -> `Cache Sync Consumer` -> `Redis`

关键点：

1. **Outbox 同事务写入**：保证 DB 数据和同步事件原子落库。  
2. **异步消费更新 Redis**：削峰、解耦、可水平扩展。  
3. **幂等消费**：按 `eventId + entityType + entityId + version` 防重。  
4. **重试 + 死信**：失败可自动重试，超过阈值进死信队列。  
5. **可观测性**：事件积压、失败率、延迟、重试次数可监控告警。

## 3.3 落地步骤

### 阶段 A：事件模型

新增 `cache_sync_event`（或 outbox）表，字段建议：

1. `id`  
2. `event_type`（UPSERT/DELETE/REBUILD）  
3. `entity_type`（client_business/client_sign/...）  
4. `entity_id`  
5. `payload`（JSON）  
6. `version`  
7. `status`（NEW/SENT/FAILED）  
8. `retry_count`  
9. `created_at/updated_at`

### 阶段 B：投递器

1. 新增定时/常驻投递器扫描 `NEW` 事件并发送 MQ。  
2. 发送成功改 `SENT`，失败记重试计数。  
3. 支持批量拉取与限流。

### 阶段 C：消费器

1. 新增 `cache-sync-consumer`（可独立模块或放 `beacon-cache`）。  
2. 按事件类型更新 Redis。  
3. 写入消费幂等记录，保证至少一次投递下结果仍正确。  
4. 异常按策略重试并进入死信。

### 阶段 D：监控与运维

1. 指标：积压量、失败量、平均延迟、DLQ 数。  
2. 工具：按实体重放、按时间段重放、失败事件导出。  
3. 灰度：先双写（快速修 + 事件链路）观测稳定后切主。

## 3.4 稳健修能否彻底解决问题

**能在工程层面彻底解决。**  
它不止“自动同步”，还提供了可靠性与治理能力：可追踪、可重试、可补偿、可扩展。

---

## 4. 快速修 vs 稳健修对比

| 维度 | 快速修 | 稳健修 |
| --- | --- | --- |
| 目标 | 快速止血 | 长期治理 |
| 改造量 | 小~中 | 中~大 |
| 上线周期 | 1~2 周 | 4~8 周 |
| 是否解决当前痛点 | 是 | 是 |
| 失败重试能力 | 弱 | 强 |
| 可观测/审计 | 基础 | 完整 |
| 可扩展性 | 一般 | 高 |

---

## 5. 推荐实施顺序

1. **第一阶段（立刻）**：完成快速修，停用手工脚本同步作为常规手段。  
2. **第二阶段（并行设计）**：设计 Outbox + MQ + Consumer 事件模型。  
3. **第三阶段（灰度）**：双轨运行（快速修 + 稳健修），验证一致性。  
4. **第四阶段（切换）**：以稳健修为主，同步保留全量重建作灾备工具。

---

## 6. 本次问题对应的交付定义（Done）

满足以下条件可视为“当前问题已被修复”：

1. MySQL 配置变更后，Redis 自动同步，不依赖手工脚本。  
2. 提供全量重建缓存工具，能一键修复历史脏数据。  
3. 有同步失败日志与告警，不再“静默失败”。  
4. （稳健修阶段）具备事件重试、死信、重放能力。


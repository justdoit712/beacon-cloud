# MySQL 到 Redis 同步专题文档

文档类型：专题方案  
适用对象：开发 / 答辩 / 演示  
验证基线：文档方案整理  
关联模块：beacon-webmaster / beacon-cache / beacon-common  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `21_mysql_redis_sync_fix_guide.md`
2. `增强_第五层AOP适用点说明.md`

## 0. 文档目的

本方案用于当前项目的**毕业设计场景**：

1. 服务经常关闭/重启
2. Redis 在云服务器中长期运行，不会随着本地服务重启而清空。
3. 需要低成本、可落地、可演示的 MySQL -> Redis 同步能力。
4. 不追求生产级复杂度（如完整 Outbox/CDC 平台化治理）。



---

## 1. 当前核心问题

当前系统的读链路高度依赖 Redis，而管理写路径主要更新 MySQL。  
如果没有自动同步，MySQL 与 Redis 会发生漂移，导致配置变更无法实时生效。

结合当前工程现状，痛点可归纳为：

1. **频繁重启导致脏缓存残留**：服务停了，云 Redis 还在，历史 key 不会自动清理。
2. **运行中写库后未必同步缓存**：管理端变更后，策略/校验模块仍读旧缓存。
3. **手工改库不可感知**：直接在数据库改数据时，应用逻辑不会触发同步。
4. **脚本化同步不可持续**：依赖手工执行测试类或脚本，容易漏、晚、错。

---



## 2. 目标

1. 项目运行中，MySQL 配置变更可自动同步到 Redis。
2. 项目启动后，能够快速校准 Redis，避免历史残留污染。
3. 提供手工一键重建能力，处理停机改库、历史脏数据、演示前校准。
4. 实现成本低、改造范围可控，适配毕设周期。

## 3. 推荐总体方案（三件套）

采用轻量混合方案：

1. **启动校准（Boot Reconcile）**：服务启动时按配置执行一次“清理 + 重建”。
2. **运行时同步（Runtime Sync）**：业务 `save/update/delete` 成功后自动同步 Redis。
3. **手工重建（Manual Rebuild）**：提供管理入口，一键重建指定数据域或全量数据域。

该方案的定位：

1. 启动校准：处理“服务重启 + Redis 残留”问题。
2. 运行时同步：处理“运行中数据变更”问题。
3. 手工重建：处理“停机期间改库/应急修复”问题。

---

## 4. 痛点与方案映射

| 痛点 | 启动校准 | 运行时同步 | 手工重建 |
| --- | --- | --- | --- |
| 重启后 Redis 残留旧数据 | 强覆盖 | 无 | 可补救 |
| 运行中配置修改不生效 | 无 | 强覆盖 | 可补救 |
| 停机期间手工改库 | 无 | 无 | 强覆盖 |
| 演示前快速校准环境 | 强覆盖 | 无 | 强覆盖 |

---

## 5. 详细实施设计

## 5.1 Key 命名空间

建议把“逻辑 key”和“物理 key 前缀”分开理解：

1. `CacheKeyBuilder` 只负责生成逻辑 key，例如 `client_business:{apikey}`、`client_channel:{clientId}`。
2. 物理前缀由缓存侧命名空间配置提供。
3. `NamespaceKeyResolver` 在 `beacon-cache` 侧负责把逻辑 key 转为物理 key，并在查询结果中还原回逻辑 key。
4. `beacon-webmaster.sync.redis.namespace` 更适合作为同步侧的对齐/校验配置，应与缓存侧前缀保持一致。

示例：

1. 逻辑 key：`client_business:{apikey}`
2. 物理 key：`beacon:dev:beacon-cloud:cz:client_business:{apikey}`
3. 逻辑 key：`dirty_word`
4. 物理 key：`beacon:dev:beacon-cloud:cz:dirty_word`

建议配置项：

1. `cache.namespace.fullPrefix=beacon:dev:beacon-cloud:cz:`
2. `sync.redis.namespace=beacon:dev:beacon-cloud:cz:`
3. 业务代码继续只传逻辑 key，禁止手工硬编码物理前缀。

---

## 5.2 数据域优先级与同步策略

## P0（优先完成）

1. `client_business:{apikey}`（Hash）
2. `client_sign:{clientId}`（Set<Map>）
3. `client_template:{signId}`（Set<Map>）
4. `client_channel:{clientId}`（Set<Map>）
5. `channel:{id}`（Hash）
6. `black:{mobile}` / `black:{clientId}:{mobile}`（String）
7. `dirty_word`（Set<String>）
8. `transfer:{mobile}`（String）

## P1（谨慎处理）

1. `client_balance:{clientId}`（Hash）

说明：当前代码已改为“MySQL 原子扣减 + 事后刷新 Redis 镜像缓存”，若启动时把 `client_balance` 当普通域全量重建，仍可能覆盖运行态镜像。  
建议继续把 `client_balance` 作为特殊域处理（详见 5.6），不要纳入普通 boot rebuild。

## 策略原则

1. **可直接覆盖结构**：`hmset/set` 写穿。
2. **集合关系结构**：优先“删 key -> 全量重建”，避免残留脏成员。
3. **删除操作**：同步删除对应 key 或标记不可用字段后覆盖。

---

## 5.3 启动校准流程（Boot Reconcile）

触发方式：

1. 在 `ApplicationRunner` 中执行。
2. 通过配置开关控制是否启用。

执行步骤：

1. 获取分布式锁（`sync:boot:lock`，TTL 120s），防止重复执行。
2. 按数据域扫描命名空间下 key。
3. 删除对应域旧 key（需要缓存服务支持通用 delete）。
4. 从 MySQL 全量拉取域数据。
5. 重新写入 Redis。
6. 输出校准报告（域名、耗时、成功数、失败数、失败 key）。

建议配置：

1. `sync.boot.enabled=true`
2. `sync.boot.domains=client_business,client_sign,client_template,client_channel,channel,black,dirty_word,transfer`
3. `sync.boot.run-on-startup=true`

注意：

1. 启动校准不等于每次都做“全业务全量重建”。
2. 可按域启停，默认只跑 P0。

---

## 5.4 运行时同步流程（Runtime Sync）

触发位置：

1. `beacon-webmaster` 写业务的 `save/update/delete` 成功路径。
2. 采用**事务提交后回调**触发同步，避免 DB 回滚引发脏写。

建议流程：

1. 业务 Service 完成 DB 事务写入。
2. 在 `afterCommit` 回调中调用 `CacheSyncService`。
3. `CacheSyncService` 按实体类型路由同步策略并执行写 Redis。
4. 记录结果日志，失败时写告警日志（至少输出实体、主键、key、异常）。

关键约束：

1. 不在 Controller 层直接写 Redis。
2. 不在事务未提交时写 Redis。
3. 同步失败不得静默吞掉。

---

## 5.5 手工一键重建（Manual Rebuild）

新增管理接口（仅管理员可用）：

1. `POST /admin/cache/rebuild?domain=client_sign`
2. `POST /admin/cache/rebuild?domain=ALL`

执行逻辑：

1. 选定域（或全部域）。
2. 删除旧 key。
3. 从 MySQL 重拉全量。
4. 批量回灌 Redis。
5. 返回执行报告（成功数、失败数、失败明细、耗时）。

适用场景：

1. 演示前统一校准。
2. 停机期间手工改库后修复。
3. 出现脏缓存后快速恢复。

---

## 5.6 关于 `client_balance` 的建议（重点）

建议明确采用“**MySQL 为主账本，Redis 为镜像缓存**”的口径：

1. 余额变更优先落 MySQL。
2. Redis `client_balance` 作为镜像缓存刷新。
3. `client_balance` 不应被纳入普通 delete/boot rebuild 流程。

---

## 6. 代码改造清单（按模块）

## 6.1 `beacon-webmaster`

新增：

1. `service/CacheSyncService`
2. `service/impl/CacheSyncServiceImpl`
3. `client/BeaconCacheWriteClient`（调用 `beacon-cache` 写接口）
4. `runner/StartupCacheReconcileRunner`
5. `controller/CacheRebuildController`（手工重建入口）
6. `support/CacheKeyBuilder`（统一 key 生成）

改造：

1. `ClientBusinessServiceImpl`：`save/update/deleteBatch` 后触发同步。
2. `ClientChannelServiceImpl`：`save/update/deleteBatch` 后触发同步。
3. `ChannelServiceImpl`：`save/update/deleteBatch` 后触发同步。
4. 后续按优先级扩展黑名单、敏感词、模板、签名等写路径。

## 6.2 `beacon-cache`

补充接口能力：

1. 通用删除接口：`DELETE /cache/delete/{key}`
2. 批量删除接口：`POST /cache/delete/batch`

可选增强：

1. 前缀扫描删除（严格白名单限制）。

## 6.3 `beacon-common`

1. 增加同步配置类 `CacheSyncProperties`。
2. 统一同步日志模型 `CacheSyncLog`（便于打印结构化日志）。

---

## 7. 配置建议

```yaml
sync:
  enabled: true
  redis:
    namespace: "beacon:dev:beacon-cloud:"
  boot:
    enabled: true
    run-on-startup: true
    lock-key: "sync:boot:lock"
    lock-ttl-seconds: 120
    domains:
      - client_business
      - client_sign
      - client_template
      - client_channel
      - channel
      - black
      - dirty_word
      - transfer
  rebuild:
    allow-manual: true
```

---

## 8. 验收标准（Done）

满足以下条件可认为“毕设场景下目标达成”：

1. 管理端修改客户、通道、签名、模板、黑名单后，Redis 秒级可见。
2. 项目重启后，P0 数据域不出现历史脏 key 干扰。
3. 停机手工改库后，执行一次手工重建即可恢复一致。
4. 同步失败会产生日志，可定位实体与 key。
5. 不再依赖测试类手工导数作为日常同步手段。

---

## 9. 推荐实施顺序（适配毕设节奏）

1. 第 1 天：完成 `CacheKeyBuilder` + `CacheSyncService` + `beacon-cache` 删除接口。
2. 第 2 天：接入 `ClientBusiness/ClientChannel/Channel` 运行时同步（afterCommit）。
3. 第 3 天：完成启动校准 Runner + 手工重建接口 + 验收脚本。
4. 第 4 天（可选）：补充黑名单/敏感词/转网等域同步与日志完善。

---

## 10. 答辩展示建议

建议准备三段演示：

1. **运行时同步演示**：后台改配置 -> Redis key 立即变化 -> 接口行为同步变化。
2. **重启校准演示**：先制造脏 key -> 重启服务 -> 校准后恢复正确。
3. **手工改库补救演示**：停机改 MySQL -> 启动后手工重建 -> 结果恢复一致。

以上三段能完整说明该方案如何覆盖“频繁停启 + 云 Redis”的实际痛点。

---

## 11. 同步门面层 AOP 适用点摘要

当前缓存同步体系里，最适合被 AOP 增强的不是规则层、key 层，也不是 Redis 落库层，而是**同步门面层**。

适用对象：

1. `CacheSyncService`
2. `CacheSyncServiceImpl`

适合交给 AOP 的横切点：

1. 统一入口日志
2. 统一耗时统计
3. 统一异常包装与 traceId 补充
4. 统一指标埋点

为什么适合：

1. 门面方法边界稳定
2. 调用入口集中
3. 这些逻辑不依赖具体域细节，天然属于横切关注点

不适合交给 AOP 的内容：

1. 域契约判断
2. 逻辑 key 构建
3. 写删路由选择
4. 删除保护与具体业务分支

原因：

1. 这些逻辑属于同步编排核心，不是通用横切能力。
2. 如果强行交给 AOP，会把业务流程拆散，降低可读性与调试性。

一句话建议：

1. 用 AOP 增强“门面入口的观测性”
2. 不要用 AOP 改写“同步编排本身的业务决策”

# beacon-common 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-common  
最后核对日期：2026-03-17

---


## 1. 目标与范围

本次文档聚焦 `beacon-common` 模块，目标是把它从“可用的工具集合”升级成“稳定、可演进、对外契约清晰”的基础模块。

范围包括：

1. 依赖和构建配置（`pom.xml`）
2. 公共常量（`constant`）
3. 公共模型（`model`/`vo`）
4. 异常与错误码（`exception`/`enums`）
5. 工具类（`util`）
6. 兼容策略与迁移步骤

---

## 2. 当前问题总览（按优先级）

### P0

1. 雪花算法实现存在可读性和健壮性问题  
    文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

### P1

1. `CMPP*MapUtil` 仍仅限进程内缓存，缺少跨实例共享与重启恢复能力
2. 注释与字符集混乱（文件内容出现乱码）

---

## 3. 需要重构的代码、原因与改造方式

---

## 3.1 SnowFlakeUtil：收敛剩余运行时风险

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

```java
private static final long TIME_START = 1668096000000L;

private long tilNextMillis(long lastTimestamp) {
    long timestamp = timeGen();
    while (timestamp <= lastTimestamp) {
        timestamp = timeGen();
    }
    return timestamp;
}

public synchronized long nextId() { ... }
```

### 原因

1. 参数越界校验、日志替换和位运算括号问题已经修复，但仍有剩余运行时风险。
2. `TIME_START` 仍然是硬编码，跨环境调整和长期演进不够灵活。
3. `tilNextMillis(...)` 采用忙等，自增序列在同一毫秒耗尽时会空转占用 CPU。
4. `nextId()` 为 `synchronized`，单实例高峰下吞吐会被串行化。

### 如何重构

1. 保留现有越界校验、时间回拨校验和测试覆盖。
2. 将 `TIME_START` 改为可配置项，保留当前默认值作为兜底。
3. 在等待下一毫秒时增加轻量让步策略，避免纯忙等。
4. 在压测结果支持的前提下，再评估是否需要进一步拆分实例维度或引入独立 ID 服务。

### 目标代码（建议）

```java
@Value("${snowflake.timeStart:1668096000000}")
private long timeStart;

private long tilNextMillis(long lastTimestamp) {
    long timestamp = timeGen();
    while (timestamp <= lastTimestamp) {
        LockSupport.parkNanos(100_000L);
        timestamp = timeGen();
    }
    return timestamp;
}

long id = ((timestamp - timeStart) << TIMESTAMP_SHIFT)
        | (machineId << machineIdShift)
        | (serviceId << serviceIdShift)
        | sequence;
return id & Long.MAX_VALUE;
```

## 3.2 CMPP 临时缓存：避免进程内状态成为长期瓶颈

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java`
2. `beacon-common/src/main/java/com/cz/common/util/CMPPDeliverMapUtil.java`

当前问题的重点不再是“无界 `ConcurrentHashMap`”，而是“进程内缓存天生不具备重启恢复和多实例共享能力”。

### 原因

1. 进程重启后上下文仍会丢失。
2. 多实例部署下状态仍不能共享。
3. 监控指标与外部化恢复能力仍然不足。

### 如何重构

1. 为本地缓存补充命中率、淘汰量、未匹配回执率等指标。
2. 评估迁移到 Redis/持久化状态机的长期方案。

### 目标代码（建议，Caffeine）

```java
// 方向一：继续增强本地缓存治理
// 方向二：迁移到 Redis / 状态表
```

## 4. 分阶段重构顺序（建议）

## Phase 1（低风险，先做）

1. `SnowFlakeUtil` 参数校验和日志改造

## Phase 2（兼容迁移）

1. `StandardSubmit` 字段标准化（保留兼容方法）
2. 常量类新旧并行（新类 + 旧类 `@Deprecated`）
3. enum 查询能力内聚，util 委托过渡

## Phase 3（结构收敛）

1. 抽象 `BizException`
2. 三类异常迁移继承
3. 为 `CMPP*MapUtil` 补监控指标并评估外部化方案

## Phase 4（清理）

1. 删除兼容层（旧字段访问器、旧常量类、旧 util 逻辑）
2. 全仓替换引用
3. 增加回归测试

---

## 5. 建议新增测试

1. `StandardSubmitCompatTest`  
验证关键契约字段和兼容命名的序列化/反序列化行为。

2. `SnowFlakeUtilTest`  
验证并发唯一性、递增趋势、回拨异常。

3. `ExceptionMappingTest`  
验证 `ExceptionEnums -> 异常 -> ResultVO` 映射一致性。

4. `CmppStoreExpiryTest`  
验证缓存条目生命周期、容量约束与未匹配回执场景。

---

## 6. 影响评估与风险

1. **高风险点**：模型字段名改动（`StandardSubmit`）  
处理：先兼容后替换，至少一个版本周期。

2. **中风险点**：异常基类重构  
处理：构造器签名保持一致，ControllerAdvice 无需同步大改。

3. **中风险点**：常量类迁移  
处理：保留旧类常量，IDE 批量替换并自动化校验。

4. **低风险点**：依赖清理和工具类改造  
处理：CI 构建 + 单元测试即可兜底。

---

## 7. 交付物清单（建议）

1. 重构代码 PR（按 Phase 拆分）
2. 兼容迁移说明（本文件）
3. 新增测试用例
4. 回滚方案（保留旧字段/旧常量至少一个版本）


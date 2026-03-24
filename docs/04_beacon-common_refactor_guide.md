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

2. 工具类与调用方的异常处理边界仍需统一  
    文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`  
    需要统一序列化异常的接收与记录策略。

### P1

1. `CMPP*MapUtil` 为无界内存容器，无过期策略
2. 注释与字符集混乱（文件内容出现乱码）
3. `ResultVO` 缺少泛型，接口契约表达能力弱

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

---

## 3.2 JsonUtil：统一序列化异常边界

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`

```java
public static String toJson(Object obj){
    try {
        return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("serialize object to json failed", e);
    }
}
```

### 原因

1. 工具类当前统一抛 `IllegalStateException`，异常语义过于宽泛。
2. 搜索写 ES 和推送客户回调都依赖 `JsonUtil`，但两边对序列化失败的处理策略不同。
3. 排障时难以第一时间区分“对象序列化失败”和“下游 IO / HTTP 调用失败”。

### 如何重构

1. 为 JSON 序列化失败定义更明确的异常语义，如 `JsonSerializeException`。
2. 调用方显式决定失败策略：
   搜索链路将其视为消息处理失败，回调链路将其视为一次推送失败并记录上下文。
3. 在日志中统一补足对象类型、主键和关键业务字段，减少排障成本。

### 目标代码（建议）

```java
public static String toJson(Object obj) {
    try {
        return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
        throw new JsonSerializeException("serialize object to json failed", e);
    }
}
```

```java
try {
    String body = JsonUtil.toJson(report);
    ...
} catch (JsonSerializeException ex) {
    log.warn("push callback serialize failed, sequenceId={}, callbackUrl={}",
            report.getSequenceId(), report.getCallbackUrl(), ex);
}
```

---

## 3.3 CMPP 临时缓存：避免进程内状态成为长期瓶颈

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

---

## 3.4 注释与字符集：统一 UTF-8 与最小必要注释

### 现状代码（需要重构）

范围：`beacon-common/src/main/java/com/cz/common/**`

当前仍存在以下情况：

1. 注释风格不一致，既有正式说明，也有口语化或历史残留说明。
2. 个别注释在代码完成重构后没有同步更新，容易误导维护者。
3. 编码规范依赖人工约定，缺少一次性清理和持续约束。

### 原因

1. 公共模块会被多个子模块引用，误导性注释会放大理解偏差。
2. 历史遗留注释和命名变更混在一起，增加 review 成本。
3. 字符集不统一时，文档与注释容易在不同 IDE/终端中出现显示异常。

### 如何重构

1. 优先清理已经过时的注释和与现状不符的示例。
2. 保留“为什么这样做”的注释，删除“代码做了什么”的重复性注释。
3. 统一 `UTF-8` 编码约束，并在团队规范中明确注释风格。

### 目标代码（建议）

```java
/**
 * 雪花算法生成全局唯一 ID。
 * 当前实现保留本地生成方案，需依赖 machineId/serviceId 配置避免冲突。
 */
public class SnowFlakeUtil { ... }
```

---

## 3.5 ResultVO：补齐泛型表达能力

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/vo/ResultVO.java`

```java
public class ResultVO {
    private Integer code;
    private String msg;
    private Object data;
    private Long total;
    private Object rows;
}
```

### 原因

1. `data` 和 `rows` 都是 `Object`，缺少编译期约束。
2. 调用方和前端契约只能依赖约定，容易出现隐式类型漂移。
3. 列表响应和普通响应复用同一个对象，表达力不够清晰。

### 如何重构

1. 将普通响应调整为 `ResultVO<T>`。
2. 为分页场景补一个单独的分页响应对象，避免 `data/rows/total` 混杂。
3. 同步调整 `Result` 工具类和接口层返回值声明。

### 目标代码（建议）

```java
public class ResultVO<T> {
    private Integer code;
    private String msg;
    private T data;
}

public class PageResultVO<T> {
    private Integer code;
    private String msg;
    private Long total;
    private List<T> rows;
}
```

---

## 4. 分阶段重构顺序（建议）

## Phase 1（低风险，先做）

1. `JsonUtil` 改造
2. `SnowFlakeUtil` 参数校验和日志改造
3. 注释统一 UTF-8，修复乱码

## Phase 2（兼容迁移）

1. `StandardSubmit` 字段标准化（保留兼容方法）
2. 常量类新旧并行（新类 + 旧类 `@Deprecated`）
3. enum 查询能力内聚，util 委托过渡

## Phase 3（结构收敛）

1. 抽象 `BizException`
2. 三类异常迁移继承
3. `ResultVO` 泛型化（可选）
4. `CMPP*MapUtil` 引入过期策略

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


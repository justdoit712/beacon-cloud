# beacon-common 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-common  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `01_beacon-common_module_analysis.md`
2. `02_beacon-common_refactor_guide.md`

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

### P0（必须先做）

1. `pom.xml` 存在重复依赖声明  
文件：`beacon-common/pom.xml`

2. 跨对象命名仍存在不一致，影响序列化与维护  
文件：`beacon-common/src/main/java/com/cz/common/model/StandardSubmit.java`  
典型问题：`StandardSubmit.apiKey` vs `StandardReport.apikey`

3. 雪花算法实现存在可读性和健壮性问题  
文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

4. 工具类与调用方的异常处理边界仍需统一  
文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`  
需要统一序列化异常的接收与记录策略。

### P1（建议紧随其后）

1. 异常类高度重复（`ApiException`/`StrategyException`/`SearchException`）
2. 常量接口命名与值不统一（如 `IS_CALLBACK = "is_Callback:"`）
3. 枚举与映射工具类重复职责（`OperatorUtil`、`CMPP2ResultUtil`、`CMPP2DeliverUtil`）

### P2（中期优化）

1. `CMPP*MapUtil` 为无界内存容器，无过期策略
2. 注释与字符集混乱（文件内容出现乱码）
3. `ResultVO` 缺少泛型，接口契约表达能力弱

---

## 3. 需要重构的代码、原因与改造方式

## 3.1 依赖配置：删除重复依赖，统一版本来源

### 现状代码（需要重构）

文件：`beacon-common/pom.xml`

```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.12.5</version>
</dependency>
```

### 原因

1. 重复声明增加维护成本
2. 同时存在“父 BOM 管理版本”和“子模块显式版本”会造成依赖漂移风险

### 如何重构

1. 保留一份依赖即可
2. 优先使用父工程 BOM 统一版本（除非必须覆盖）

### 目标代码（建议）

```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

---

## 3.2 模型字段命名：统一风格并保持兼容

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/model/StandardSubmit.java`
2. `beacon-common/src/main/java/com/cz/common/model/StandardReport.java`

```java
// 当前主字段（StandardSubmit）
private String realIp;
private Long signId;

// 当前仍存在的跨对象命名差异（StandardReport）
private String apikey;
```

### 原因

1. `StandardSubmit` 内部命名已基本规范，但跨对象仍存在 `apiKey/apikey` 风格不一致。
2. 关键链路仍有 `BeanUtils.copyProperties(...)`，命名差异会带来静默映射风险。
3. 契约层一旦继续演进，兼容窗口和测试成本仍然较高。

### 如何重构

采用“平滑迁移”而非一次性断裂：

1. 将“字段命名治理”的重点从 `SignId/realIP` 转为 `apiKey/apikey` 统一。
2. 对跨对象复制链路改为显式映射，避免依赖反射式同名复制。
3. 为兼容字段建立契约测试，确保老消息与老索引数据仍可读。

### 目标代码（建议）

```java
// 示例：优先统一 apiKey/apikey 命名，或至少显式映射
report.setApikey(submit.getApiKey());
```

---

## 3.3 异常体系：提取基类，减少重复代码

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/exception/ApiException.java`
2. `beacon-common/src/main/java/com/cz/common/exception/StrategyException.java`
3. `beacon-common/src/main/java/com/cz/common/exception/SearchException.java`

三者结构几乎一致，仅类名不同。

### 原因

1. 重复代码高
2. 无法集中扩展（如 traceId、errorType、httpStatus）
3. 调用方难以形成统一异常处理规范

### 如何重构

1. 新增基类 `BizException`
2. 三个业务异常继承 `BizException`
3. 保持原构造方法签名，避免外部调用改动过大

### 目标代码（建议）

```java
public abstract class BizException extends RuntimeException {
    private final Integer code;

    protected BizException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    protected BizException(ExceptionEnums enums) {
        super(enums.getMsg());
        this.code = enums.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
```

```java
public class ApiException extends BizException {
    public ApiException(String message, Integer code) {
        super(message, code);
    }

    public ApiException(ExceptionEnums enums) {
        super(enums);
    }
}
```

---

## 3.5 SnowFlakeUtil：参数校验与位运算表达修复

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

```java
if(machineId > maxMachineId || serviceId > maxServiceId){
    System.out.println("机器ID或服务ID超过最大范围值！！！");
    throw new ApiException(ExceptionEnums.SNOWFLAKE_OUT_OF_RANGE);
}
...
return  ((timestamp - timeStart) << timestampShift) |
        (machineId << machineIdShift) |
        (serviceId << serviceIdShift) |
        sequence &
                Long.MAX_VALUE;
```

### 原因

1. 未校验负值（`machineId < 0`、`serviceId < 0`）
2. `System.out.println` 不符合服务端日志规范
3. 位运算表达式可读性差，易误解掩码作用范围

### 如何重构

1. 增加负值校验
2. 替换为 `log.error`
3. 明确位运算括号，避免歧义
4. `timeStart` 支持配置项注入，减少硬编码

### 目标代码（建议）

```java
if (machineId < 0 || machineId > maxMachineId || serviceId < 0 || serviceId > maxServiceId) {
    log.error("snowflake config out of range, machineId={}, serviceId={}", machineId, serviceId);
    throw new ApiException(ExceptionEnums.SNOWFLAKE_OUT_OF_RANGE);
}
...
long id = ((timestamp - timeStart) << timestampShift)
        | (machineId << machineIdShift)
        | (serviceId << serviceIdShift)
        | sequence;
return id & Long.MAX_VALUE;
```

---

## 3.6 常量定义：从 interface 常量迁移到 final class

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/constant/CacheKeyConstants.java`
2. `beacon-common/src/main/java/com/cz/common/constant/RabbitMQConstants.java`
3. `beacon-common/src/main/java/com/cz/common/constant/SmsConstant.java`
4. `beacon-common/src/main/java/com/cz/common/constant/ApiConstant.java`

当前全部采用 `interface` 常量形式。

### 原因

1. `interface` 常量会产生“常量接口反模式”
2. 无法约束实例化和继承语义
3. 命名有不一致项（如 `IS_CALLBACK` 的 value 与其他字段风格不一致）

### 如何重构

1. 使用 `final class + private constructor`
2. 按域拆分为更清晰的常量类（`CacheKeys`、`MqTopics`、`SmsStatus`）
3. 保留旧常量类并标记 `@Deprecated`，迁移后再删除

### 目标代码（建议）

```java
public final class MqTopics {
    private MqTopics() {}

    public static final String SMS_PRE_SEND = "sms_pre_send_topic";
    public static final String SMS_WRITE_LOG = "sms_write_log_topic";
}
```

---

## 3.7 枚举映射：让 enum 自带查询，替代 util map

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/util/OperatorUtil.java`
2. `beacon-common/src/main/java/com/cz/common/util/CMPP2ResultUtil.java`
3. `beacon-common/src/main/java/com/cz/common/util/CMPP2DeliverUtil.java`

### 原因

1. `enum + util` 双轨维护，改一个漏一个
2. `Map` 可变，存在被误改风险
3. 查询逻辑散落

### 如何重构

把查询能力直接放到枚举里，util 类保留兼容方法并委托到 enum。

### 目标代码（建议）

```java
@Getter
public enum MobileOperatorEnum {
    CHINA_MOBILE(1, "移动"),
    CHINA_UNICOM(2, "联通"),
    CHINA_TELECOM(3, "电信"),
    UNKNOWN(0, "未知");

    private static final Map<String, MobileOperatorEnum> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(MobileOperatorEnum::getOperatorName, Function.identity()));

    public static Integer idByName(String operatorName) {
        MobileOperatorEnum operator = BY_NAME.get(operatorName);
        return operator == null ? UNKNOWN.operatorId : operator.operatorId;
    }
}
```

---

## 3.8 CMPP 临时缓存：避免进程内状态成为长期瓶颈

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

## 4. 分阶段重构顺序（建议）

## Phase 1（低风险，先做）

1. 清理 `pom.xml` 重复依赖
2. `JsonUtil` 改造
3. `SnowFlakeUtil` 参数校验和日志改造
4. 注释统一 UTF-8，修复乱码

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


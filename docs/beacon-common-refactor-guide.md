# beacon-common 重构设计文档

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

2. 公共模型字段命名不一致，影响序列化与维护  
文件：`beacon-common/src/main/java/com/cz/common/model/StandardSubmit.java`  
典型字段：`SignId`、`realIP`

3. 雪花算法实现存在可读性和健壮性问题  
文件：`beacon-common/src/main/java/com/cz/common/util/SnowFlakeUtil.java`

4. 工具类异常处理方式不规范  
文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`  
当前有 `printStackTrace()`。

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

文件：`beacon-common/src/main/java/com/cz/common/model/StandardSubmit.java`

```java
private String realIP;
private Long SignId;
```

### 原因

1. Java 字段命名不符合统一规范（驼峰 + 首字母小写）
2. 与其他模块字段名（如 `apiKey`）风格不一致
3. 序列化和前端字段映射容易混乱

### 如何重构

采用“平滑迁移”而非一次性断裂：

1. 新增规范字段：`realIp`、`signId`
2. 通过 Jackson 注解兼容旧字段名
3. 保留旧 getter/setter（标记 `@Deprecated`）一个版本周期

### 目标代码（建议）

```java
@Data
public class StandardSubmit implements Serializable {

    @JsonAlias({"realIP"})
    private String realIp;

    @JsonAlias({"SignId"})
    private Long signId;

    @Deprecated
    public String getRealIP() {
        return realIp;
    }

    @Deprecated
    public void setRealIP(String realIP) {
        this.realIp = realIP;
    }
}
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

## 3.4 JsonUtil：移除 printStackTrace，标准化异常

### 现状代码（需要重构）

文件：`beacon-common/src/main/java/com/cz/common/util/JsonUtil.java`

```java
public static String obj2JSON(Object obj){
    try {
        return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
        e.printStackTrace();
        throw new RuntimeException("转换JSON失败！");
    }
}
```

### 原因

1. `printStackTrace` 不可控，污染日志
2. 抛出的 `RuntimeException` 丢失原始异常上下文
3. `ObjectMapper` 未明确配置 Java Time 等序列化行为

### 如何重构

1. 使用日志框架记录错误（或直接封装异常链）
2. 保留原始异常 `cause`
3. 固化 `ObjectMapper` 配置，避免跨模块行为不一致

### 目标代码（建议）

```java
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {}

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize to json failed", e);
        }
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

1. `beacon-common/src/main/java/com/cz/common/constant/CacheConstant.java`
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

## 3.8 CMPP 临时缓存：增加过期策略，避免内存无界

### 现状代码（需要重构）

文件：

1. `beacon-common/src/main/java/com/cz/common/util/CMPPSubmitRepoMapUtil.java`
2. `beacon-common/src/main/java/com/cz/common/util/CMPPDeliverMapUtil.java`

当前实现为 `static ConcurrentHashMap`，没有容量限制或过期清理。

### 原因

1. 高峰期或异常情况下可能累积对象导致内存风险
2. 缺少监控指标（当前 map 大小不可观测）

### 如何重构

方案 A（推荐）：引入 Caffeine，设置 `expireAfterWrite + maximumSize`  
方案 B（保守）：保留 `ConcurrentHashMap`，增加时间戳 + 定时清理线程

### 目标代码（建议，Caffeine）

```java
public final class CmppSubmitStore {
    private static final Cache<Integer, StandardSubmit> CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500_000)
            .build();

    public static void put(int sequence, StandardSubmit submit) {
        CACHE.put(sequence, submit);
    }

    public static StandardSubmit remove(int sequence) {
        return CACHE.asMap().remove(sequence);
    }
}
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
验证 JSON 中 `realIP`/`SignId` 仍可反序列化到新字段。

2. `SnowFlakeUtilTest`  
验证并发唯一性、递增趋势、回拨异常。

3. `ExceptionMappingTest`  
验证 `ExceptionEnums -> 异常 -> ResultVO` 映射一致性。

4. `CmppStoreExpiryTest`  
验证缓存条目可自动过期、容量限制生效。

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


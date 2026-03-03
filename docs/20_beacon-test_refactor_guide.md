# beacon-test 重构文档

## 1. 模块定位

`beacon-test` 当前更接近“数据初始化/联调工具模块”，而不是业务服务模块。  
从代码结构看，它主要做两件事：

1. 通过 MyBatis Mapper 从 MySQL 读取基础数据（客户、余额、签名、模板、通道、黑名单、携转等）
2. 通过 Feign 调用 `beacon-cache` 把数据写入 Redis

核心入口与关键代码：

1. 启动类：`beacon-test/src/main/java/com/cz/test/TestStarter.java`
2. 缓存客户端：`beacon-test/src/main/java/com/cz/test/client/CacheClient.java`
3. 读库 Mapper：`beacon-test/src/main/java/com/cz/test/mapper/*.java`
4. 写缓存逻辑（当前放在测试类）：`beacon-test/src/test/java/com/cz/test/mapper/*Test.java`

这意味着当前模块存在“功能在测试类中实现”的结构性问题，维护成本和运行风险都偏高。

---

## 2. 现状概览

## 2.1 当前实现特征

1. `src/main` 基本只有实体、Mapper、Feign 接口，没有业务服务层
2. `src/test` 的 `@SpringBootTest` 测试类承担了“缓存预热脚本”职责
3. 大量硬编码 ID 与缓存 key 约定，缺少参数化
4. 配置中存在明文数据库凭据和固定 Nacos 地址

## 2.2 主要风险

1. 配置安全风险（明文账号密码 + 老旧驱动）
2. 测试执行即改写外部系统（MySQL + Redis），不具备可控性
3. 代码契约弱类型（`Object`、裸 `Map`、`select *`）
4. 测试体系失真（几乎无断言，偏脚本化）

---

## 3. 重构项明细（含代码、原因、方案）

## 3.1 P0：敏感配置与依赖基线

### 现状代码（需要重构）

文件：`beacon-test/src/main/resources/application.yml:9`、`beacon-test/src/main/resources/application.yml:12`、`beacon-test/src/main/resources/application.yml:14`、`beacon-test/src/main/resources/application.yml:15`

```yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.88.128:8848
  datasource:
    driver-class-name: org.gjt.mm.mysql.Driver
    username: root
    password: 123
```

文件：`beacon-test/pom.xml:49`

```xml
<artifactId>mysql-connector-java</artifactId>
<version>5.1.49</version>
```

### 原因

1. 凭据明文存储，泄露风险高。
2. `org.gjt.mm.mysql.Driver` 与 MySQL 5.x 驱动版本均偏旧，升级成本被持续推迟。
3. 无 profile 分层，测试环境和联调环境边界不清晰。

### 如何重构

1. 用环境变量/Nacos 配置中心承载 `datasource`、`nacos`、Redis 等敏感参数。
2. 驱动切换 `com.mysql.cj.jdbc.Driver`，并统一依赖版本治理。
3. 至少拆分 `application-local.yml`、`application-dev.yml`、`application-seed.yml`。

### 目标代码（建议）

```yml
spring:
  profiles:
    active: seed
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${BEACON_TEST_DB_URL}
    username: ${BEACON_TEST_DB_USERNAME}
    password: ${BEACON_TEST_DB_PASSWORD}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR}
```

---

## 3.2 P0：把“写缓存脚本”从测试类迁出

### 现状代码（需要重构）

文件：`beacon-test/src/test/java/com/cz/test/mapper/ClientBusinessMapperTest.java:18`、`beacon-test/src/test/java/com/cz/test/mapper/ClientBusinessMapperTest.java:39`

```java
@SpringBootTest
...
cacheClient.hmset("client_business:" + cb.getApikey(),map);
```

文件：`beacon-test/src/test/java/com/cz/test/mapper/ClientBalanceMapperTest.java:33`

```java
cacheClient.hmset("client_balance:1",map);
```

文件：`beacon-test/src/test/java/com/cz/test/mapper/MobileBlackMapperTest.java:30`

```java
cacheClient.set("black:" + mobileBlack.getBlackNumber(),"1");
```

### 原因

1. 执行测试会直接写外部 Redis，副作用不可控。
2. 测试无法稳定复现，不适合放入 CI 默认测试集。
3. 功能与测试职责耦合，模块语义混乱。

### 如何重构

1. 在 `src/main` 新建 `CacheSeedService` + `SeedRunner`，显式触发。
2. 通过命令参数指定 seed 类型：`--seed=client,sign,template,blacklist`。
3. 测试仅验证转换逻辑与 key 规则，不直接写外部系统。
4. CI 中默认禁用 seed（只有手工任务或专用流水线执行）。

### 目标代码（建议）

```java
@Component
@ConditionalOnProperty(name = "beacon.seed.enabled", havingValue = "true")
public class CacheSeedRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
        seedService.seedAll();
    }
}
```

---

## 3.3 P0：测试框架混用（JUnit4/JUnit5）需统一

### 现状代码（需要重构）

文件：`beacon-test/src/test/java/com/cz/test/mapper/MobileBlackMapperTest.java:6`、`beacon-test/src/test/java/com/cz/test/mapper/MobileBlackMapperTest.java:17`

```java
import org.junit.jupiter.api.Test;
...
@RunWith(SpringRunner.class)
```

### 原因

1. JUnit4 Runner 与 JUnit5 注解混用，执行行为不稳定。
2. 团队协作时容易出现“本地通过/流水线不执行”的隐患。

### 如何重构

1. 统一到 JUnit5（推荐）：
   - `@ExtendWith(SpringExtension.class)`
   - `@SpringBootTest`
2. 移除 JUnit4 依赖与 Runner 用法。
3. 建立测试风格约束（Checkstyle/ArchUnit/Spotless 规则）。

---

## 3.4 P1：SQL 与数据过滤规则需要收敛

### 现状代码（需要重构）

文件：`beacon-test/src/main/java/com/cz/test/mapper/ClientBusinessMapper.java:10`

```java
@Select("select * from client_business where id = #{id}")
```

文件：`beacon-test/src/main/java/com/cz/test/mapper/ClientBalanceMapper.java:8`

```java
@Select("select * from client_balance where client_id = #{clientId}")
```

文件：`beacon-test/src/main/java/com/cz/test/mapper/ClientSignMapper.java:11`

```java
@Select("select * from client_sign where client_id = #{clientId}")
```

### 原因

1. `select *` 对表结构变更敏感，字段漂移会引入隐式问题。
2. 软删除条件（`is_delete = 0`）并非所有查询都统一处理。
3. 可读性和治理性较弱，不利于后续缓存增量同步。

### 如何重构

1. 显式列出字段，建立统一 SQL 常量或 XML mapper。
2. 对软删除表统一加过滤条件。
3. 给所有查询补“按更新时间增量同步”能力，避免全量扫表。

### 目标代码（建议）

```java
@Select("""
select id, client_id, balance, updated
from client_balance
where client_id = #{clientId}
  and is_delete = 0
""")
ClientBalance findActiveByClientId(@Param("clientId") Long clientId);
```

---

## 3.5 P1：实体与接口类型不规范

### 现状代码（需要重构）

文件：`beacon-test/src/main/java/com/cz/test/entity/ClientBusiness.java:19`

```java
private Object id;
```

文件：`beacon-test/src/main/java/com/cz/test/entity/ClientBusiness.java:114`

```java
public List<String> getIpAddress() {
    ...
}
```

文件：`beacon-test/src/main/java/com/cz/test/entity/Channel.java:10`

```java
private Integer channelProtocal;
```

### 原因

1. `Object id` 破坏类型安全，增加序列化/映射歧义。
2. `ipAddress` 字段语义与 getter 类型不一致（字段 `String`，getter 返回 `List<String>`）。
3. `channelProtocal` 命名错误，长期影响可读性和接口对齐。

### 如何重构

1. 所有主键与外键统一 `Long`。
2. 把 `ipAddress` 拆为：
   - `private String ipAddressRaw`
   - `public List<String> parseIpAddressList()`
3. 修复 `channelProtocol` 命名，并通过 JSON 注解兼容历史字段。

---

## 3.6 P1：CacheClient 契约过于宽松

### 现状代码（需要重构）

文件：`beacon-test/src/main/java/com/cz/test/client/CacheClient.java:18`、`beacon-test/src/main/java/com/cz/test/client/CacheClient.java:21`

```java
void set(..., @RequestParam(value = "value") Object value);
void sadd(..., @RequestBody Map<String,Object>... maps);
```

### 原因

1. `Object` 与可变参数 `Map` 导致序列化行为不清晰。
2. 调用端几乎无法做编译期约束。
3. `void` 返回值不利于失败检测与重试。

### 如何重构

1. 定义明确 DTO（如 `CacheSetCommand`、`CacheHashPutCommand`）。
2. 返回统一结果对象（如 `ResultVO`），并记录失败原因。
3. 加入超时、重试、幂等控制。

---

## 3.7 P2：测试代码质量与可维护性

### 现状代码（需要重构）

文件：`beacon-test/src/test/java/com/cz/test/mapper/ClientBalanceMapperTest.java:31`、`beacon-test/src/test/java/com/cz/test/mapper/ClientBusinessMapperTest.java:38`

```java
Map map = objectMapper.readValue(..., Map.class);
```

文件：`beacon-test/src/test/java/com/cz/test/mapper/ClientSignMapperTest.java:49`

```java
e.printStackTrace();
```

文件：`beacon-test/src/test/java/com/cz/test/mapper/MobileTransferMapperTest.java:31`

```java
System.out.println("【排查】从数据库查出的条数：" + list.size());
```

### 原因

1. 裸 `Map`、重复 `ObjectMapper` 转换，代码重复且缺少约束。
2. `System.out/printStackTrace` 不适合工程化日志。
3. 测试缺少断言，无法衡量正确性。

### 如何重构

1. 抽取 `EntityToCacheValueConverter`，统一转换逻辑。
2. 全部替换为 `Slf4j` 日志并保留必要上下文。
3. 增加断言：key 规则、value 字段完整性、调用次数验证。

---

## 4. 推荐重构顺序（执行计划）

## 阶段 1：安全与执行边界（1 周）

1. 配置外置化，去除明文凭据。
2. 将缓存写入逻辑从测试迁到主代码可控入口（`seed` 模式）。
3. 禁止 CI 默认执行外部副作用测试。

## 阶段 2：契约与类型治理（1 周）

1. 修正实体类型和命名（`Object id`、`channelProtocal`）。
2. 重构 `CacheClient` 强类型 DTO + 统一返回值。
3. SQL 从 `select *` 改为显式字段 + 软删除过滤。

## 阶段 3：测试体系重建（1 周）

1. 统一 JUnit5。
2. 单元测试覆盖转换器与 key 规则。
3. 集成测试只在专用环境执行，加入 smoke 标签。

## 阶段 4：运维化与可观测（0.5~1 周）

1. seed 执行日志结构化（耗时、写入量、失败量）。
2. 支持 dry-run 与增量模式（按更新时间）。
3. 输出 seed 报表（成功/失败 key 列表）。

---

## 5. 测试与验收清单

1. 配置安全
   - 缺少 DB/Nacos 参数时应启动失败。
2. Seed 幂等性
   - 连续执行两次，缓存结果一致且无异常放大。
3. 契约测试
   - `beacon-test` 到 `beacon-cache` 的 DTO 契约可反序列化。
4. 数据正确性
   - 关键 key 的字段完整性与值类型正确。
5. 执行控制
   - 非 `seed` profile 下不触发缓存写入。
6. 回归验证
   - 历史 key 命名（`client_balance:*`、`black:*`）保持兼容。

---

## 6. 跨模块联动建议

1. 与 `beacon-cache` 对齐接口契约，避免 `Object/Map` 弱类型调用。
2. 与 `beacon-common` 统一返回对象和异常码定义。
3. 与 `beacon-api/beacon-strategy` 对齐缓存 key 命名规范与 TTL 策略。
4. 与运维流程对齐：把 seed 改成可审计的发布步骤，而非“手工跑测试”。

---

## 7. 优先改造文件清单（建议从上到下）

1. `beacon-test/src/main/resources/application.yml`
2. `beacon-test/pom.xml`
3. `beacon-test/src/test/java/com/cz/test/mapper/MobileBlackMapperTest.java`
4. `beacon-test/src/test/java/com/cz/test/mapper/ClientBusinessMapperTest.java`
5. `beacon-test/src/main/java/com/cz/test/client/CacheClient.java`
6. `beacon-test/src/main/java/com/cz/test/entity/ClientBusiness.java`
7. `beacon-test/src/main/java/com/cz/test/entity/Channel.java`
8. `beacon-test/src/main/java/com/cz/test/mapper/ClientBalanceMapper.java`
9. `beacon-test/src/main/java/com/cz/test/mapper/ClientSignMapper.java`
10. `beacon-test/src/main/java/com/cz/test/mapper/ClientTemplateMapper.java`


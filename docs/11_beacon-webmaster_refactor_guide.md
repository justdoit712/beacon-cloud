# beacon-webmaster 模块总文档

文档类型：重构指南  
适用对象：开发 / 重构  
验证基线：代码静态核对  
关联模块：beacon-webmaster  
最后核对日期：2026-03-17

---

原始来源（已合并）：

1. `16_beacon-webmaster_module_analysis.md`
2. `11_beacon-webmaster_refactor_guide.md`

## 1. 模块定位

`beacon-webmaster` 是短信平台的运营后台模块，负责：

1. 后台登录与权限（Shiro + 验证码）
2. 客户、通道、账号、菜单、角色、用户管理
3. 短信查询与图表统计（通过 Feign 调用 `beacon-search`）
4. 后台手工发短信（通过 Feign 调用 `beacon-api`）
5. 定时任务管理（Quartz）
6. 一批历史兼容接口（`/sys/{family}/...`）

从代码结构看，该模块是“多职责聚合”的管理面，现阶段主要风险集中在：安全基线、数据一致性、跨模块契约稳定性、以及接口风格一致性。

## 2. 现状概览（按风险分层）

### P0（必须先做）

1. 明文配置与基础依赖版本老旧（`application.yml`、`pom.xml`）
2. 认证授权链路安全性不足（MD5 密码、授权返回 `null`）
3. 部分业务是内存存储实现（重启丢数据、多实例不一致）
4. 定时任务反射调用过于宽松（可被错误配置放大风险）

### P1（应在 P0 后紧跟）

1. 跨模块 Feign 契约大量 `Map<String,Object>`
2. 多个列表接口是“查全量 + 内存分页”
3. 短信管理链路校验与 token 策略可用性高于稳健性
4. 控制器返回模型风格不统一

### P2（持续优化）

1. 验证码与登录风控偏弱
2. 命名与历史包袱（如 `Acount`、`stragety`）影响维护性
3. 自动化测试覆盖不足

---

## 3. 重构项明细（含代码、原因、方案）

## 3.1 P0：配置安全与依赖升级

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/resources/application.yml:4`、`beacon-webmaster/src/main/resources/application.yml:6`、`beacon-webmaster/src/main/resources/application.yml:7`、`beacon-webmaster/src/main/resources/application.yml:20`

```yml
spring:
  datasource:
    driver-class-name: org.gjt.mm.mysql.Driver
    url: jdbc:mysql://192.168.88.128:3306/duanxin_pingtai...
    username: root
    password: 123
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.88.128:8848
```

文件：`beacon-webmaster/pom.xml:26`、`beacon-webmaster/pom.xml:32`、`beacon-webmaster/pom.xml:37`

```xml
<artifactId>shiro-spring-boot-web-starter</artifactId>
<version>1.4.0</version>

<artifactId>mysql-connector-java</artifactId>
<version>5.1.49</version>

<artifactId>druid-spring-boot-starter</artifactId>
<version>1.1.10</version>
```

### 原因

1. 凭据明文入库，泄露风险高，且难以做环境隔离。
2. `org.gjt.mm.mysql.Driver` 已是历史驱动写法，升级兼容成本被延后。
3. 安全相关依赖版本偏旧，存在已知漏洞与维护风险。

### 如何重构

1. 将数据库、Nacos、内部 token 等敏感配置统一迁移到 Nacos/环境变量。
2. 替换驱动为 `com.mysql.cj.jdbc.Driver`，并验证时区与字符集行为。
3. 统一父 POM 管理版本，做一次依赖治理（含 CVE 扫描）。

### 依赖升级落地建议（2026-03-24 补充）

1. 当前建议先落文档，不直接修改依赖代码。
   - 原因一：当前全仓库执行 `mvn -DskipTests compile` 可以通过，直接跨代升级的收益暂时低于回归风险。
   - 原因二：根工程基线仍是 `Spring Boot 2.3.12.RELEASE`、`Spring Cloud Hoxton.SR12`、`Spring Cloud Alibaba 2.2.6.RELEASE`，`beacon-webmaster` 同时使用 `Java 8`、`javax.*`、`Shiro 1.x`，不适合直接跳到 `Boot 3.x` 或 `Shiro 2.x`。
2. 第一阶段建议只做“同代对齐”，并在有专门回归窗口时执行。
   - 根 `spring.cloud.alibaba-version`：`2.2.6.RELEASE -> 2.2.9.RELEASE`，先与 `Boot 2.3.12 / Hoxton.SR12` 对齐。
   - `shiro-spring-boot-web-starter`：`1.4.0 -> 1.13.0`。
   - `druid-spring-boot-starter`：`1.1.10 -> 1.2.28`。
   - `hibernate-validator`：从旧坐标 `org.hibernate:hibernate-validator` 调整为 `org.hibernate.validator:hibernate-validator`，或直接改为 `spring-boot-starter-validation` 由父依赖统一管理。
3. 第二阶段将 MySQL 驱动升级作为独立变更执行，不与其它版本治理混在同一提交中。
   - 依赖坐标从 `mysql:mysql-connector-java:5.1.49` 切到 `com.mysql:mysql-connector-j`。
   - `driver-class-name` 同步切到 `com.mysql.cj.jdbc.Driver`。
   - `mybatis-generator-maven-plugin` 中的驱动依赖一并调整，避免生成器与运行时驱动分裂。
4. 当前窗口不建议直接做以下升级。
   - `Spring Boot 2.7/3.x`
   - `Spring Cloud 202x`
   - `Spring Cloud Alibaba 202x`
   - `Shiro 2.x`
   - `MyBatis Spring Boot Starter 2.3+/3.x`
   - 原因：会同时引入 `bootstrap.yml` / Nacos 配置加载机制变化、`javax -> jakarta` 迁移、Java 版本要求变化，以及安全框架行为变化，超出本次 `P0` 快速整改范围。
5. 每次依赖升级前后都要执行最小验收清单。
   - `mvn -DskipTests compile`
   - 后台登录、验证码、Shiro 会话链路
   - MySQL 连接、Mapper 查询、MyBatis Generator
   - Nacos 注册与配置拉取
   - 调用 `beacon-search`、`beacon-api` 的 Feign 链路

### 目标代码（建议）

```yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${BEACON_WEBMASTER_DB_URL}
    username: ${BEACON_WEBMASTER_DB_USERNAME}
    password: ${BEACON_WEBMASTER_DB_PASSWORD}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR}
```

---

## 3.2 P0：认证与授权体系需要补强

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java:30`、`beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java:31`、`beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java:74`

```java
credentialsMatcher.setHashAlgorithmName("MD5");
credentialsMatcher.setHashIterations(1024);
...
protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    return null;
}
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsUserServiceImpl.java:71`、`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsUserServiceImpl.java:148`

```java
user.setPassword("123456");
...
return new SimpleHash("MD5", password, ByteSource.Util.bytes(salt), 1024).toHex();
```

### 原因

1. MD5 不再适合作为密码哈希方案。
2. 授权返回 `null`，意味着权限模型并未在 Realm 层生效。
3. 默认口令策略风险高（弱口令）。

### 如何重构

1. 引入 `BCrypt/Argon2`，并设计“旧 MD5 登录后自动升级”的平滑迁移。
2. 完整实现 `doGetAuthorizationInfo`，加载角色与权限标识。
3. 控制器层补齐 `@RequiresPermissions` 或服务层授权切面。
4. 移除默认密码逻辑，改为“创建用户必须显式设置初始密码 + 首次登录强制改密”。

### 目标代码（建议）

```java
public interface PasswordCodec {
    String encode(String raw);
    boolean matches(String raw, String encoded);
}

protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    SmsUser user = (SmsUser) principals.getPrimaryPrincipal();
    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
    info.setRoles(roleService.getRoleName(user.getId()));
    info.setStringPermissions(permissionService.findPermsByUserId(user.getId()));
    return info;
}
```

---

## 3.3 P0：内存存储实现不适用于生产

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/AcountServiceImpl.java:23`

```java
private final ConcurrentMap<Long, Map<String, Object>> dataStore = new ConcurrentHashMap<>();
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/PhaseServiceImpl.java:42`

```java
private final ConcurrentMap<Long, Map<String, Object>> dataStore = new ConcurrentHashMap<>();
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/LegacyCrudServiceImpl.java:89`

```java
private final ConcurrentMap<String, ConcurrentMap<Long, Map<String, Object>>> store = new ConcurrentHashMap<>();
```

### 原因

1. 服务重启即丢数据。
2. 多实例部署时，节点间数据不一致。
3. 运维排障与审计不可追踪（无持久化来源）。

### 如何重构

1. 为 `acount/phase/legacy family` 建立持久化模型（MySQL 表 + Mapper + Service）。
2. 将 `Map<String,Object>` 输入输出替换为 DTO/Entity。
3. 对“历史兼容接口”做分层：兼容控制器 + 新服务内核，逐步收敛。
4. 灰度阶段增加双写或数据回填脚本。

### 目标代码（建议）

```java
@Transactional(rollbackFor = Exception.class)
public boolean save(PhaseSaveCommand cmd, Long operatorId) {
    PhaseEntity entity = phaseConverter.toEntity(cmd, operatorId);
    return phaseMapper.insertSelective(entity) > 0;
}
```

---

## 3.4 P0：定时任务反射调用边界过宽

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java:61`、`beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java:71`、`beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java:85`

```java
Object bean = SpringContextHolder.getBean(job.getBeanName());
method = ReflectionUtils.findMethod(bean.getClass(), methodName, String.class);
method.invoke(bean, params);
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/ScheduleJobController.java:51`

```java
return R.error(e.getMessage());
```

### 原因

1. 任务元数据来自数据库，反射执行可调用范围过大，存在误配置/恶意配置风险。
2. 直接返回异常消息给前端，存在信息泄露。

### 如何重构

1. 任务执行改为“白名单注册式”：
   - DB 存 `jobType`
   - 代码中 `Map<jobType, ScheduleJobHandler>` 映射
2. 参数采用 JSON schema 校验，而不是自由字符串。
3. 控制器返回统一错误码，不回传底层异常细节。

### 目标代码（建议）

```java
public interface ScheduleJobHandler {
    void execute(String params);
}

ScheduleJobHandler handler = handlerRegistry.get(job.getJobType());
if (handler == null) {
    throw new IllegalArgumentException("unsupported jobType");
}
handler.execute(job.getParams());
```

---

## 3.5 P1：跨模块契约弱类型（`Map`）导致维护成本高

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/client/SearchClient.java:23`、`beacon-webmaster/src/main/java/com/cz/webmaster/client/SearchClient.java:26`

```java
Map<String,Object> findSmsByParameters(@RequestBody Map<String,Object> parameters);
Map<String, Integer> countSmsState(@RequestBody Map<String, Object> parameters);
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SearchController.java:50`、`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SearchController.java:106`

```java
public ResultVO list(@RequestParam Map<String, Object> params) { ... }
BeanUtils.copyProperties(vo, (Map<?, ?>) item);
```

### 原因

1. 字段名全靠字符串约定，变更难发现。
2. 类型转换分散在控制器，代码脆弱。
3. 与 `beacon-search` 的契约演进困难。

### 如何重构

1. 定义共享请求/响应 DTO（建议放 `beacon-common` 的 `contract` 包）。
2. Feign 客户端使用强类型泛型分页响应。
3. 将权限拼装、查询参数规范化下沉到 Service 层，控制器只做协议转换。

### 目标代码（建议）

```java
@PostMapping("/search/sms/list")
PageResult<SearchSmsItemDTO> findSms(@RequestBody SearchSmsQueryDTO query);
```

---

## 3.6 P1：多处列表接口存在“查全量再内存分页”

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysClientController.java:45`、`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysClientController.java:52`

```java
List<ClientBusiness> list = clientBusinessService.findByKeyword(keyword);
...
for (ClientBusiness cb : list.subList(fromIndex, toIndex)) { ... }
```

同类问题还出现在：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/ClientBusinessController.java:57`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysUserController.java:44`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysMenuController.java:43`
4. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SystemRoleController.java:56`

### 原因

1. 数据量上来后，内存和响应时间会快速上升。
2. DB 端索引与分页优势没有利用。

### 如何重构

1. 服务层提供 `findPage(keyword, offset, limit)`。
2. Mapper 使用 `limit`/`offset`（或 PageHelper）。
3. Controller 直接返回分页结果，去掉 `subList`。

### 目标代码（建议）

```java
PageResult<ClientBusiness> result = clientBusinessService.findPage(keyword, offset, limit);
return R.ok(result.getTotal(), converter.toViewList(result.getRows()));
```

---

## 3.7 P1：短信管理链路还需稳健性增强

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:37`、`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:252`、`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java:254`

```java
@Value("${sms.internal.token:}")
private String internalToken;

private String buildUid(Long operatorId, String mobile, int index) {
    String prefix = operatorId == null ? "wm" : String.valueOf(operatorId);
    return prefix + "_" + System.currentTimeMillis() + "_" + index + "_" + mobile;
}
```

### 原因

1. `internalToken` 空值不 fail-fast，运行期才暴露问题。
2. `uid` 规则依赖毫秒时间戳，可观测性和全局唯一性都一般。
3. 校验逻辑主要是手工字符串返回，难复用且不利于统一错误模型。

### 如何重构

1. 改为 `@ConfigurationProperties + @Validated`，启动阶段强校验 token。
2. `uid` 使用 Snowflake/UUID（必要时追加业务前缀）。
3. `SmsSendForm` 使用 Bean Validation，`@ControllerAdvice` 统一报错。
4. 为 Feign 调用增加超时、熔断和错误分类（网络/业务）。

### 目标代码（建议）

```java
@ConfigurationProperties(prefix = "sms.internal")
@Validated
public class SmsInternalProperties {
    @NotBlank
    private String token;
}
```

---

## 3.8 P1：接口返回风格不统一

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SystemRoleController.java:42`

```java
public Map<String, Object> list(...) { ... }
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysMenuController.java:37`

```java
public ResultVO list(...) { ... }
```

### 原因

1. 前端需要兼容多种响应结构，增加联调复杂度。
2. 错误码体系不统一，不利于监控告警聚合。

### 如何重构

1. 统一为 `ResultVO`（或统一 `ApiResponse<T>`）；
2. 历史结构通过 adapter 过渡，给前端明确切换窗口；
3. 统一异常处理，移除控制器中重复 try/catch。

---

## 3.9 P2：验证码与登录防护策略需要补齐

### 现状代码（需要重构）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/config/KaptchaConfig.java:26`

```java
properties.setProperty(Constants.KAPTCHA_TEXTPRODUCER_CHAR_LENGTH,"4");
```

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SmsUserController.java:43`、`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SmsUserController.java:63`

```java
@Value("${system.test-kaptcha:}") String testKaptcha
boolean isTestKaptcha = StringUtils.hasText(testKaptcha) && testKaptcha.equals(userDTO.getCaptcha());
```

### 原因

1. 验证码策略简单，缺少有效期与防重放策略说明。
2. 测试验证码配置如果误入生产，会削弱登录防护。

### 如何重构

1. 将 `test-kaptcha` 仅限 `local/dev` profile 生效；
2. 验证码保存到 Redis，设置 TTL 与一次性消费；
3. 增加登录限流（按用户名/IP），失败阈值告警。

---

## 4. 推荐重构顺序（执行计划）

## 阶段 1：安全止血（1~2 周）

1. 移除明文凭据、升级驱动与关键依赖。
2. 下线生产环境 `test-kaptcha`。
3. 统一隐藏内部异常信息（先从 `Schedule*Controller` 开始）。

交付物：可上线的安全基线版本（不改变主要业务行为）。

## 阶段 2：认证授权重构（1~2 周）

1. 引入新密码编码方案并实现平滑迁移。
2. 实现 Realm 授权信息加载。
3. 给关键写接口补齐权限注解与权限测试。

交付物：可审计、可验证的 RBAC 主链路。

## 阶段 3：替换内存实现为持久化（2~3 周）

1. 重构 `AcountServiceImpl`、`PhaseServiceImpl`、`LegacyCrudServiceImpl`。
2. 新建/补齐表结构与 Mapper。
3. 完成历史接口兼容策略（路径不变、内核替换）。

交付物：重启不丢数据，多节点一致。

## 阶段 4：契约与性能治理（1~2 周）

1. `SearchClient` 强类型化，去 `Map` 契约。
2. 清理“全量查询 + subList”。
3. 统一分页模型与返回模型。

交付物：接口稳定性提升，列表性能可控。

## 阶段 5：稳态收口（1 周）

1. 短信管理链路 fail-fast、超时熔断、uid 优化。
2. 登录风控、验证码策略完善。
3. 命名修复（`Acount -> Account` 等）与文档同步。

交付物：长期可维护版本。

---

## 5. 测试清单（重构必须补齐）

1. 认证授权测试
   - 登录成功/失败、密码迁移、权限拒绝路径。
2. 配置安全测试
   - 缺失关键配置启动失败（token、DB 配置）。
3. 持久化一致性测试
   - `acount/phase/legacy` 写入后重启不丢失。
4. 定时任务安全测试
   - 非白名单 jobType 拒绝执行，异常不泄露内部细节。
5. 契约测试
   - `beacon-webmaster` 与 `beacon-search`、`beacon-api` 的 Feign DTO 契约。
6. 性能回归
   - 列表分页接口在万级数据量下的响应时间与内存占用。

---

## 6. 跨模块联动建议

1. 与 `beacon-common` 对齐统一响应模型、分页对象、异常码。
2. 与 `beacon-search` 协同升级查询 DTO，避免双边各自解析 `Map`。
3. 与 `beacon-api` 协同内部调用鉴权策略（token 轮换与失效机制）。
4. 与 `beacon-monitor` 对齐后台关键指标：
   - 登录失败率
   - 定时任务失败率
   - Feign 调用异常率
   - 后台关键写操作成功率

---

## 7. 优先改造文件清单（建议从上到下）

1. `beacon-webmaster/src/main/resources/application.yml`
2. `beacon-webmaster/pom.xml`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java`
4. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsUserServiceImpl.java`
5. `beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java`
6. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/AcountServiceImpl.java`
7. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/PhaseServiceImpl.java`
8. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/LegacyCrudServiceImpl.java`
9. `beacon-webmaster/src/main/java/com/cz/webmaster/client/SearchClient.java`
10. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SearchController.java`


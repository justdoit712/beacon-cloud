# beacon-webmaster 模块详细分析

文档类型：模块分析  
适用对象：开发 / 排障 / 重构  
验证基线：代码静态核对  
关联模块：beacon-webmaster  
最后核对日期：2026-03-17

---

## 1. 模块定位

`beacon-webmaster` 是短信平台的后台管理中台，承担“管理端入口 + 权限认证 + 配置维护 + 运营查询 + 手工发送 + 调度任务管理”职责。

在整体链路中的位置：

`运营人员浏览器` -> `beacon-webmaster` -> `MySQL`（主数据）  
`beacon-webmaster` -> `beacon-api`（内部发短信）  
`beacon-webmaster` -> `beacon-search`（检索与统计）

---

## 2. 模块结构

源码目录：`beacon-webmaster/src/main/java/com/cz/webmaster`

按职责划分：

| 分层 | 代表类 | 说明 |
|---|---|---|
| 启动层 | `WebMasterStarterApp` | SpringBoot 启动、Mapper 扫描、Feign 启用 |
| 鉴权配置层 | `ShiroConfig`, `ShiroRealm`, `KaptchaConfig`, `KaptchaController` | 登录认证、验证码、会话控制 |
| 管理控制层 | `SysUserController`, `SystemRoleController`, `SysMenuController`, `SysClientController`, `ClientBusinessController`, `SysChannelController`, `SysClientChannelController` | 用户/角色/菜单/客户/通道等主数据维护 |
| 业务操作层 | `SysSmsController`, `SearchController`, `EchartsController`, `SysAcountController`, `SysPhaseController`, `SysLegacyCrudController` | 发送、检索、统计、充值、号段、历史配置接口 |
| 服务层 | `SmsManageServiceImpl`, `ClientBusinessServiceImpl`, `ChannelServiceImpl`, `ClientChannelServiceImpl` 等 | 业务规则与数据写入 |
| 调度子系统 | `ScheduleJobServiceImpl`, `ScheduleQuartzJob`, `ScheduleInvokeServiceImpl`, `QuartzUtils` | 定时任务管理与执行 |
| 数据访问层 | `mapper/*.java` + `resources/mapper/*.xml` | MyBatis/注解 SQL 访问数据库 |
| 外部调用层 | `ApiSmsClient`, `SearchClient` | 调用 `beacon-api` 和 `beacon-search` |

---

## 3. 核心业务流程

## 3.1 登录认证流程（Shiro + 验证码）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/KaptchaController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SmsUserController.java`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java`
4. `beacon-webmaster/src/main/java/com/cz/webmaster/config/ShiroConfig.java`

流程：

1. 前端获取验证码图片 `/sys/auth/captcha.jpg`，验证码写入 Session。
2. 登录请求 `/sys/login` 校验验证码（支持 `system.test-kaptcha` 万能码）。
3. 使用 Shiro `UsernamePasswordToken` 做认证。
4. `ShiroRealm` 从库查询用户，并按 `MD5 + salt + 1024` 校验密码。
5. 认证通过后所有 `/**` 资源默认只要求 `authc`（已登录）。

## 3.2 管理端手工发短信流程

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysSmsController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsManageServiceImpl.java`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/client/ApiSmsClient.java`

流程：

1. 管理端提交 `SmsSendForm`（clientId/mobile/content/state）。
2. 服务端校验手机号格式与批量上限（500）。
3. 按登录人权限确认可操作客户（ROOT 角色可跨客户）。
4. 逐手机号调用 `beacon-api` 内部接口 `/sms/internal/single_send`。
5. 聚合结果为 `SmsBatchSendVO` 返回前端。

## 3.3 查询与图表流程

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SearchController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/EchartsQueryService.java`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/client/SearchClient.java`

流程：

1. 管理端请求查询条件（内容、手机号、客户、时间等）。
2. 按登录人角色裁剪可见客户范围。
3. 转发给 `beacon-search` 查询列表和状态聚合。
4. 管理端做结果转换（VO/图表结构）后返回。

## 3.4 配置管理流程

主要接口：

1. 客户业务：`ClientBusinessController` + `ClientBusinessServiceImpl`
2. 客户基础信息：`SysClientController` + `ClientBusinessServiceImpl`
3. 通道：`SysChannelController` + `ChannelServiceImpl`
4. 客户通道绑定：`SysClientChannelController` + `ClientChannelServiceImpl`

特征：

1. 主体数据存 MySQL，普遍采用软删除（`is_delete`）。
2. 多数表单通过 Converter 把页面字段映射到实体 `extend1~extend4`。

## 3.5 调度任务流程（Quartz）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/ScheduleJobController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/ScheduleJobServiceImpl.java`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java`

流程：

1. 管理端维护任务元数据（beanName/methodName/cron/params/status）。
2. 保存或更新时同步到 Quartz 调度器。
3. 触发执行时由 `ScheduleQuartzJob` 按 `jobId` 找到任务。
4. `ScheduleInvokeServiceImpl` 反射调用目标 Bean 方法并记录执行日志。

---

## 4. 数据与接口契约特征

## 4.1 强类型与弱类型混用

1. 用户、角色、菜单、客户、通道模块大多使用实体 + DTO + VO。
2. `AcountService`、`PhaseService`、`LegacyCrudService` 主要用 `Map<String,Object>`，字段语义弱、类型约束弱。

## 4.2 历史字段复用明显

1. `ClientBusiness` 与 `SmsUser` 大量业务语义塞在 `extend1~extend4`。
2. Converter 层承担字段语义映射，维护成本高，易出现跨页面语义冲突。

## 4.3 返回协议不统一

1. 一部分接口返回 `ResultVO`（`R.ok/R.error`）。
2. 一部分接口直接返回 `Map`（如 `SystemRoleController` 使用 `status/msg`）。
3. 同模块内存在多种响应风格，前端与文档维护成本高。

---

## 5. 依赖与配置现状

## 5.1 依赖侧

文件：`beacon-webmaster/pom.xml`

关键依赖：

1. `spring-boot-starter-web`
2. `shiro-spring-boot-web-starter`
3. `mybatis-spring-boot-starter` + `druid` + `mysql`
4. `spring-boot-starter-quartz`
5. `spring-cloud-starter-openfeign`（调用 API/搜索）
6. `kaptcha`（验证码）

## 5.2 配置侧

文件：`beacon-webmaster/src/main/resources/application.yml`

现状：

1. 数据库连接、账号、密码直接写在配置文件中。
2. Nacos 地址固定写死。
3. 包含 `system.test-kaptcha=1111`（开发便利配置）。

---

## 6. 主要风险与技术债

## 6.1 鉴权模型过宽，缺少授权校验（高风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/config/ShiroConfig.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/relam/ShiroRealm.java`

1. 全局主要是“登录即通过”（`/** -> authc`），缺少基于角色/权限的 URL 级拦截。
2. `ShiroRealm.doGetAuthorizationInfo` 返回 `null`，授权能力未启用。

## 6.2 密码与认证安全性不足（高风险）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsUserServiceImpl.java`

1. 使用 `MD5`（即便加盐迭代）已不符合现代密码存储基线。
2. 新用户默认密码固定 `123456`，若流程管理不严，极易被弱口令利用。

## 6.3 生产敏感信息明文配置（高风险）

文件：`beacon-webmaster/src/main/resources/application.yml`

1. 数据库地址、账号和密码明文存在。
2. 运维泄漏面大，且不符合密钥治理实践。

## 6.4 万能验证码配置风险（高风险）

文件：

1. `beacon-webmaster/src/main/resources/application.yml`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SmsUserController.java`

1. `system.test-kaptcha` 启用时可绕过真实验证码。
2. 若误带到生产，登录面防护强度显著下降。

## 6.5 账号/号段/历史配置模块为内存存储（高风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/AcountServiceImpl.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/PhaseServiceImpl.java`
3. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/LegacyCrudServiceImpl.java`

1. 依赖 `ConcurrentMap` 作为主存储，重启即丢失。
2. 多实例部署下节点间状态不一致。
3. 这类配置管理不具备持久化与审计能力。

## 6.6 余额更新存在并发覆盖风险（高风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/ClientBusinessController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/AcountServiceImpl.java`

1. 余额变更采用读-改-写模式，缺少原子更新与版本控制。
2. 并发充值下可能发生丢更新。

## 6.7 调度执行模型过于开放（高风险）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/schedule/ScheduleInvokeServiceImpl.java`

1. 任务配置可指定任意 Bean + Method，运行时反射执行。
2. 若任务管理权限被滥用，存在高危运维操作面。

## 6.8 多实例下 Quartz 可能重复执行（中风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/ScheduleJobServiceImpl.java`
2. `beacon-webmaster/src/main/resources/application.yml`

1. 未见 Quartz 集群配置与节点协调信息。
2. 多实例部署时可能重复初始化和重复触发任务。

## 6.9 登录失败日志包含敏感入参（中风险）

文件：`beacon-webmaster/src/main/java/com/cz/webmaster/controller/SmsUserController.java`

1. 多处日志打印 `userDTO={}`，对象包含密码与验证码字段。
2. 日志泄漏将直接扩大安全风险。

## 6.10 用户-角色关系维护链路不完整（中风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysUserController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/service/impl/SmsUserServiceImpl.java`

1. 用户新增/更新流程未覆盖 `sms_user_role` 关系维护。
2. 实际权限分配依赖人工或外部流程，容易出现权限漂移。

## 6.11 API 契约风格不统一（中风险）

文件：

1. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SystemRoleController.java`
2. `beacon-webmaster/src/main/java/com/cz/webmaster/controller/SysUserController.java`

1. 同类管理接口返回结构混用 `ResultVO` 与普通 `Map`。
2. 前端与网关层对齐成本增加，长期演进困难。

## 6.12 自动化测试覆盖薄弱（中风险）

文件：`beacon-webmaster/src/test/java/...`

1. 现有测试覆盖面仍偏窄。
2. 认证、手工发送、调度、并发充值等核心链路仍缺少有效回归用例。

---

## 7. 与上下游模块契约关系

## 7.1 对 `beacon-api` 契约

1. `SmsManageServiceImpl` 通过 `ApiSmsClient.singleSend(...)` 调用内部发送接口。
2. 依赖 `X-Internal-Token` 配置与 `ApiInternalSingleSendForm` 字段契约稳定。

## 7.2 对 `beacon-search` 契约

1. `SearchController` 与 `EchartsQueryService` 依赖 `findSmsByParameters` / `countSmsState`。
2. 参数键名（如 `clientID/starttime/stoptime`）与搜索模块约定紧耦合。

## 7.3 对数据库契约

1. 用户、角色、菜单、客户、通道、任务等数据依赖 MySQL。
2. 多表仍采用 `extend` 字段承载业务语义，需靠应用层解释。

---

## 8. 改造建议（按优先级）

## P0（优先）

1. 启用真正的授权模型：在 Shiro 层补齐角色/权限校验与接口级鉴权。
2. 迁移密码算法到 `bcrypt/argon2`，废弃 MD5 与默认弱口令。
3. 移除明文敏感配置，接入环境变量或密钥中心。
4. 下线生产万能验证码能力，至少按环境强制禁用。
5. 将 `Acount/Phase/Legacy` 内存存储迁移到持久层（MySQL/Redis+持久化），保障可恢复与可审计。
6. 余额更新改成原子 SQL（如 `set balance = balance + ?`）并增加事务边界。

## P1（中期）

1. 统一 API 响应规范（状态码、message、data 结构一致）。
2. 为用户管理补齐用户-角色关系维护接口与审计日志。
3. 对调度执行增加白名单机制（限制可调用 Bean/Method）和高危操作审批。
4. 增加关键日志脱敏（尤其登录与远程调用异常）。
5. 建立配置变更后的缓存刷新策略，避免下游读取旧配置。

## P2（长期）

1. 重构 `extend1~extend4` 模型为显式业务字段，降低语义耦合。
2. 对 Quartz 引入集群化与幂等执行策略，支持多实例稳定运行。
3. 建立分层测试体系：单测（规则）、集成测（DB+Feign）、回归测（权限+调度+发送链路）。
4. 推动管理端接口契约文档化与版本化，减少前后端隐式耦合。

---

## 9. 推荐治理指标

1. `webmaster.auth.fail.count`：登录失败次数（脱敏后）。
2. `webmaster.sms.send.success.rate`：管理端手工发送成功率。
3. `webmaster.schedule.job.fail.count`：调度任务失败次数与耗时分位。
4. `webmaster.permission.denied.count`：越权访问拦截次数。
5. `webmaster.config.change.audit.count`：配置变更审计覆盖率。

---

## 10. 结论

`beacon-webmaster` 功能覆盖面广，已经形成管理后台的主工作台能力，并打通了“配置维护-发送-查询-调度”核心路径。  
当前核心风险不在功能缺失，而在“安全基线、一致性基线、工程化基线”三个层面存在明显短板。  
优先完成 P0 后，可以显著提升系统安全性与数据可靠性，再通过 P1/P2 逐步完成管理平台长期演进。

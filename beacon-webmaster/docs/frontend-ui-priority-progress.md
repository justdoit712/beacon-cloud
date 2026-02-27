# 前端 UI 优先优化进度记录

- 记录日期：2026-02-27
- 执行分支：`front`
- 关联文档：`docs/frontend-ui-priority-task-board.md`

## 阶段 0（S0）执行状态

| Step | 状态 | 结论 |
|---|---|---|
| S0-01 建分支与初始化进度文档 | 已完成 | 已创建并使用 `front` 分支；本文件已初始化。 |
| S0-02 基线截图采集 | 已完成 | 已完成登录/首页/用户/客户/活动五页基线截图并登记路径。 |
| S0-03 验证范围固化 | 已完成 | 已固化浏览器版本、查验范围与最小冒烟口径。 |
| S0-04 业务边界确认落档 | 已完成 | 已确认仅改样式与页面结构，不改接口与后端逻辑。 |

## S0-01 建分支与进度文档初始化

- 看板建议分支：`feature/frontend-ui-priority-plan`
- 当前执行分支：`front`
- 说明：按当前任务指令，后续前端更新统一在 `front` 分支进行。

## S0-02 基线截图采集登记

- 截图目录：`docs/screenshots/frontend-ui-priority/baseline-2026-02-27/`
- 命名规范（固定）：
  - `01-login.png`
  - `02-index.png`
  - `03-user.png`
  - `04-client.png`
  - `05-activity.png`
- 页面口径：
  - 登录页：`login.html`
  - 首页：`index.html`
  - 用户管理：`sys/user.html`
  - 客户管理：`client/client.html`
  - 活动管理：`activity/activity.html`
- 采集方式：Edge Headless（`1366x768`），基于本地静态页面 `file://` 路径采集。
- 说明：本轮用于 UI 基线对比；如需覆盖登录态与动态数据展示，可在应用启动后补采一版运行态截图。

## S0-03 验证范围固化

### 浏览器版本基线（本机）
- Chrome：`145.0.7632.110`
- Edge：`145.0.3800.65`

### 验证口径（每步最少）
- `login.html`：登录成功/失败提示可见且状态正确。
- `index.html`：菜单跳转与 `iframe` 加载正常。
- 当前改动页：新增/修改/删除/返回流程正常。

### 运行口径
- IDE 方式：重启 `WebMasterStarterApp` 后再做冒烟验证。
- Maven 方式（可选）：`mvn -pl beacon-webmaster -am spring-boot:run`

## S0-04 业务边界确认

### 本轮允许改动
- 仅改前端样式：`theme.css`、页面样式与类结构。
- 仅改页面结构：查询区/按钮区/表格区/编辑区布局统一。
- 可做可用性修复：按钮状态、表单提示、可访问性标签关联。

### 本轮禁止改动
- 不改后端接口、请求参数、响应结构。
- 不改数据库结构、权限模型、核心业务规则。
- 不将无关代码混入同一步骤。

## 阶段 0 记录模板（后续复用）

```text
Step: Sx-xx
Commit: <none for now>
Changed Files: <file1>, <file2>, <file3>
Smoke Test:
- login: pass/fail
- index+iframe: pass/fail
- current page CRUD/back: pass/fail
Result: pass/fail
Screenshots: <path>
Notes: <optional>
```



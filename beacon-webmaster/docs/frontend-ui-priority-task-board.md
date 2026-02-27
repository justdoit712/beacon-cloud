# 前端 UI 优先优化任务看板

## 1. 说明
本看板由 `frontend-ui-priority-implementation-plan.md` 拆分而来，用于按最小改动步骤执行并留存可回滚锚点。

执行约束：
- 每步最多改动 1~3 个紧密关联文件。
- 每步只实现一个明确目标。
- 每步必须完成：改动前检查 -> 改动 -> 重启冒烟 -> 提交 -> 记录。
- 严禁使用 `git reset --hard` 做回退。

## 2. 标准执行模板（每步固定）
1. 改动前检查：`git status --short`
2. 执行当前步骤变更（不混入无关内容）
3. 重启应用并冒烟验证：
   - `login.html` 登录成功/失败提示
   - `index.html` 菜单跳转 + iframe 加载
   - 当前改动页面的新增/修改/删除/返回
4. 提交步骤：`git add <files> && git commit -m "<step-id>: <说明>"`
5. 记录结果：步骤编号、提交号、验证结论、截图路径

## 3. 任务清单
| ID | 阶段 | 任务 | 目标文件（每步 1~3 个） | 完成标准 | 预估 |
|---|---|---|---|---|---|
| S0-01 | 阶段 0 | 建分支与初始化进度文档 | `beacon-webmaster/docs/frontend-ui-priority-progress.md` | 分支 `feature/frontend-ui-priority-plan` 创建，进度文档初始化 | 0.5h |
| S0-02 | 阶段 0 | 基线截图采集 | 无代码改动 | 登录/首页/用户/客户/活动截图齐全并登记路径 | 1h |
| S0-03 | 阶段 0 | 验证范围固化 | `beacon-webmaster/docs/frontend-ui-priority-progress.md` | Chrome/Edge 版本与验证口径明确 | 0.5h |
| S0-04 | 阶段 0 | 业务边界确认落档 | `beacon-webmaster/docs/frontend-ui-priority-progress.md` | 明确仅改样式与页面结构，不改接口 | 0.5h |
| S1-01 | 阶段 1 | 新建主题文件与令牌 | `src/main/resources/static/public/css/theme.css` | CSS 变量覆盖主色/语义色/圆角/阴影/间距 | 2h |
| S1-02 | 阶段 1 | 统一基础组件样式 | `src/main/resources/static/public/css/theme.css` | 按钮/输入框/面板/表格/分页/标签/弹层标题可用 | 3h |
| S1-03 | 阶段 1 | 门面页接入主题 | `src/main/resources/static/index.html`, `src/main/resources/static/login.html` | 两页引入 `theme.css` 且无回归 | 1h |
| S1-04 | 阶段 1 | 高频页首批接入主题 | `src/main/resources/static/sys/user.html`, `src/main/resources/static/client/client.html`, `src/main/resources/static/client/clientbusiness.html` | 首批页面视觉统一且无错位 | 1.5h |
| S1-05 | 阶段 1 | 高频页次批接入主题 | `src/main/resources/static/channel/channel.html`, `src/main/resources/static/activity/activity.html` | 次批页面视觉统一且无错位 | 1h |
| S1-06 | 阶段 1 | 收敛 `main.css` 全局污染（第一批） | `src/main/resources/static/public/css/main.css`, `src/main/resources/static/sys/user.html`, `src/main/resources/static/client/client.html` | `.row/.col-sm-*/.form-horizontal` 改局部作用域，页面正常 | 3h |
| S1-07 | 阶段 1 | 收敛 `main.css` 全局污染（第二批） | `src/main/resources/static/client/clientbusiness.html`, `src/main/resources/static/channel/channel.html`, `src/main/resources/static/activity/activity.html` | 全局污染收敛，无跨页副作用 | 1.5h |
| S2-01 | 阶段 2 | 登录页视觉升级（背景+卡片） | `src/main/resources/static/login.html`, `src/main/resources/static/public/css/theme.css` | 首屏层次、留白、卡片观感明显提升 | 2h |
| S2-02 | 阶段 2 | 登录交互统一 + 密码类型修复 | `src/main/resources/static/login.html`, `src/main/resources/static/public/css/theme.css` | 输入/按钮状态统一，`user.password` 为 `type="password"` | 2h |
| S2-03 | 阶段 2 | 首页框架优化（去行内样式） | `src/main/resources/static/index.html`, `src/main/resources/static/public/css/theme.css` | 顶栏/侧栏/面包屑/iframe 容器规范，路由逻辑不变 | 3h |
| S2-04 | 阶段 2 | 控制台页卡片化改造 | `src/main/resources/static/sys/main.html`, `src/main/resources/static/public/css/theme.css` | 欢迎区+指标占位+常用入口，支持基础响应式 | 3h |
| S3-01 | 阶段 3 | 用户页结构统一 + 密码字段复核 | `src/main/resources/static/sys/user.html`, `src/main/resources/static/public/css/theme.css` | 查询/按钮/表格/编辑分区统一，密码输入正确 | 2.5h |
| S3-02 | 阶段 3 | 客户与客户业务页结构统一 | `src/main/resources/static/client/client.html`, `src/main/resources/static/client/clientbusiness.html`, `src/main/resources/static/public/css/theme.css` | 布局、按钮顺序、表格样式一致 | 3h |
| S3-03 | 阶段 3 | 渠道与活动页结构统一 + 桥接 | `src/main/resources/static/channel/channel.html`, `src/main/resources/static/activity/activity.html`, `src/main/resources/static/public/css/theme.css` | Element 与 Bootstrap 间距桥接统一 | 3h |
| S3-04 | 阶段 3 | 弹层和按钮规范收口 | `src/main/resources/static/public/css/theme.css`, `src/main/resources/static/sys/user.html`, `src/main/resources/static/activity/activity.html` | layer 标题/按钮风格统一，按钮顺序统一 | 2h |
| S4-01 | 阶段 4 | 主要操作态增强 | `src/main/resources/static/public/css/theme.css` | hover/active/disabled/loading 状态清晰 | 2h |
| S4-02 | 阶段 4 | 校验提示与空状态文案统一 | `src/main/resources/static/public/css/theme.css`, `src/main/resources/static/sys/user.html`, `src/main/resources/static/client/client.html` | 校验提示位置一致，空状态文案一致 | 2h |
| S4-03 | 阶段 4 | 三视口适配 | `src/main/resources/static/public/css/theme.css`, `src/main/resources/static/index.html`, `src/main/resources/static/login.html` | 375/768/1366 无横向滚动，核心流程可操作 | 3h |
| S4-04 | 阶段 4 | 常见可访问性修正 | `src/main/resources/static/login.html`, `src/main/resources/static/sys/user.html`, `src/main/resources/static/public/css/theme.css` | `label-for` 正确，焦点可见 | 2h |
| R-01 | 收尾 | 上线前全量回归 | 无代码或文档更新 | 功能/视觉/安全/兼容清单全部通过 | 2h |
| R-02 | 收尾 | 交付记录归档 | `beacon-webmaster/docs/frontend-ui-priority-progress.md` | 每步提交号、截图、结论完整可追踪 | 1h |

## 4. 建议排期（10 个工作日）
1. Day 1：S0-01 ~ S0-04
2. Day 2~3：S1-01 ~ S1-07
3. Day 4~5：S2-01 ~ S2-04
4. Day 6~8：S3-01 ~ S3-04
5. Day 9：S4-01 ~ S4-04
6. Day 10：R-01 ~ R-02

## 5. Commit Message 模板
统一格式：
```text
<step-id>: <scope> <change-summary>
```

示例：
```text
S1-01: theme add design tokens for color radius shadow spacing
S1-03: index/login include theme.css and verify no route regression
S2-02: login unify input/button states and fix password input type
S3-03: channel/activity unify layout and bridge element bootstrap spacing
S4-03: responsive adapt key pages for 375 768 1366 viewports
R-01: qa run full smoke and regression checklist
```

## 6. 回滚标准动作
- 回退未提交文件：`git restore -- <file>`
- 回退已提交步骤：`git revert <commit_sha>`
- 仅回退某文件到指定版本：`git restore --source <commit_sha> -- <file>`

回滚优先级：
1. 优先回滚当前异常页面
2. 其次回滚 `theme.css` 接入点
3. 避免整分支回滚，减少无关影响

## 7. 每步验收记录模板
```text
Step: Sx-xx
Commit: <sha>
Changed Files: <file1>, <file2>, <file3>
Smoke Test:
- login: pass/fail
- index+iframe: pass/fail
- current page CRUD/back: pass/fail
Result: pass/fail
Screenshots: <path>
Notes: <optional>
```

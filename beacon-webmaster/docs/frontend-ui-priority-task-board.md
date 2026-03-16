# 前端 UI 优先优化任务看板

## 1. 说明
本看板已按当前代码基线重排，仅保留尚未收口的活跃步骤；已落地内容已从任务清单中移除，不再重复排期。

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
   - 当前改动页面核心流程：
     - CRUD 页面：新增 / 修改 / 删除 / 返回
     - 特殊页面：按步骤定义验证查询 / 发送 / 提交 / 图表渲染等主链路
4. 提交步骤：`git add <files> && git commit -m "<step-id>: <说明>"`
5. 记录结果：步骤编号、提交号、验证结论、截图路径

## 2.1 当前进度（截至 2026-03-16）
- 当前阶段：阶段 3（页面收口），下一步 `P1-01`
- 活跃步骤：`P1-01 ~ P3-02`、`R-01 ~ R-02`（8 步）
- 已从活跃看板移除：`login.html`、`index.html`、`sys/main.html`、`sys/user.html` 及其对应通用样式改造
- 详细过程记录：`docs/frontend-ui-priority-progress.md`

## 3. 任务清单
| ID | 阶段 | 状态 | 任务 | 目标文件（每步 1~3 个） | 完成标准 | 预估 |
|---|---|---|---|---|---|---|
| P1-01 | 同模板页 | 下一步 | 客户与客户业务页结构收口 | `src/main/resources/static/client/client.html`, `src/main/resources/static/client/clientbusiness.html`, `src/main/resources/static/public/css/theme.css` | 布局、按钮顺序、表格/表单样式一致，CRUD/back 通过 | 3h |
| P1-02 | 同模板页 | 待开始 | 渠道与活动页结构收口 + Element 桥接复核 | `src/main/resources/static/channel/channel.html`, `src/main/resources/static/activity/activity.html`, `src/main/resources/static/public/css/theme.css` | 列表/表单视觉一致，Element 与 Bootstrap 间距、输入高度、按钮风格统一 | 3h |
| P2-01 | 标准管理页 | 待开始 | 角色与菜单页纳入统一模板 | `src/main/resources/static/sys/role.html`, `src/main/resources/static/sys/menu.html`, `src/main/resources/static/public/css/theme.css` | 列表、表单、树弹层、按钮顺序统一，CRUD/back 通过 | 3h |
| P2-02 | 标准管理页 | 待开始 | 号段与充值页纳入统一模板 | `src/main/resources/static/phase/phase.html`, `src/main/resources/static/acount/acount.html`, `src/main/resources/static/public/css/theme.css` | 下拉、表单、空状态、按钮区一致，CRUD/back 通过 | 3h |
| P3-01 | 特殊页 | 待开始 | 搜索与短信发送页收口 | `src/main/resources/static/client/search.html`, `src/main/resources/static/client/smssend.html`, `src/main/resources/static/public/css/theme.css` | 非 CRUD 验收标准明确，查询/发送主链路可操作，表单与结果区风格统一 | 3h |
| P3-02 | 特殊页 | 待开始 | 充值入口与短信饼图页收口 | `src/main/resources/static/client/userpay.html`, `src/main/resources/static/echarts/smspie.html`, `src/main/resources/static/public/css/theme.css` | 入口、筛选、提交、图表渲染流程可用，页面风格统一 | 3h |
| R-01 | 收尾 | 待开始 | 上线前全量回归 | 无代码或文档更新 | 功能 / 视觉 / 兼容 / 可访问性清单全部通过 | 2h |
| R-02 | 收尾 | 待开始 | 交付记录归档 | `beacon-webmaster/docs/frontend-ui-priority-progress.md` | 每步提交号、截图、结论完整可追踪 | 1h |

## 4. 建议排期（与当前进度同步）
1. 待执行：Day 1~2，`P1-01 ~ P1-02`
2. 待执行：Day 3~4，`P2-01 ~ P2-02`
3. 待执行：Day 5~6，`P3-01 ~ P3-02`
4. 待执行：Day 7，`R-01 ~ R-02`

## 5. Commit Message 模板
统一格式：
```text
<step-id>: <scope> <change-summary>
```

示例：
```text
P1-01: client unify client and clientbusiness page structure
P1-02: channel activity align layout and bridge element spacing
P2-01: sys role menu align management page template
P3-01: special pages unify search and sms send flows
R-01: qa run full smoke and regression checklist
```

## 6. 回滚标准动作
- 回退未提交文件：`git restore -- <file>`
- 回退已提交步骤：`git revert <commit_sha>`
- 仅回退某文件到指定版本：`git restore --source <commit_sha> -- <file>`

回滚优先级：
1. 优先回滚当前异常页面
2. 其次回滚 `theme.css` 对应接入点
3. 避免整分支回滚，减少无关影响

## 7. 每步验收记录模板
```text
Step: Px-xx
Commit: <sha>
Changed Files: <file1>, <file2>, <file3>
Smoke Test:
- login: pass/fail
- index+iframe: pass/fail
- current page main flow: pass/fail
Result: pass/fail
Screenshots: <path>
Notes: <optional>
```

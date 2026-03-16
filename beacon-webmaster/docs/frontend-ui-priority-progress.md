# 前端 UI 优先优化进度记录

## 1. 基线重排（2026-03-16）
本次记录用于把活跃看板与当前代码状态重新对齐，后续步骤从该基线开始继续追踪。

### 1.1 已确认落地并从活跃看板移除的内容
- `src/main/resources/static/login.html` + `src/main/resources/static/public/css/theme.css`
  - 登录页视觉层级、卡片化、密码输入类型、基础响应式、焦点可见已落地
- `src/main/resources/static/index.html` + `src/main/resources/static/public/css/theme.css`
  - 首页框架、顶栏 / 侧栏 / 面包屑 / iframe 容器样式已落地
- `src/main/resources/static/sys/main.html` + `src/main/resources/static/public/css/theme.css`
  - 控制台欢迎区、指标卡、快捷入口、基础响应式已落地
- `src/main/resources/static/sys/user.html` + `src/main/resources/static/public/css/theme.css`
  - 用户页查询 / 列表 / 编辑区结构统一、密码字段复核、基础校验提示已落地
- `src/main/resources/static/public/css/theme.css`
  - 通用 token、按钮态、表格、面板、弹层按钮、表单校验、基础响应式样式已接入

### 1.2 当前活跃步骤
- 下一步：`P1-01`
- 活跃范围：`P1-01 ~ P3-02`、`R-01 ~ R-02`

### 1.3 说明
- 历史已完成步骤未在本文件中补录旧提交号，避免对既有提交关系做不准确追溯
- 从 `2026-03-16` 起，后续步骤按活跃看板逐步记录

## 2. 活跃步骤清单
| ID | 状态 | 任务 | 目标文件 |
|---|---|---|---|
| P1-01 | 下一步 | 客户与客户业务页结构收口 | `client/client.html`, `client/clientbusiness.html`, `public/css/theme.css` |
| P1-02 | 待开始 | 渠道与活动页结构收口 + Element 桥接复核 | `channel/channel.html`, `activity/activity.html`, `public/css/theme.css` |
| P2-01 | 待开始 | 角色与菜单页纳入统一模板 | `sys/role.html`, `sys/menu.html`, `public/css/theme.css` |
| P2-02 | 待开始 | 号段与充值页纳入统一模板 | `phase/phase.html`, `acount/acount.html`, `public/css/theme.css` |
| P3-01 | 待开始 | 搜索与短信发送页收口 | `client/search.html`, `client/smssend.html`, `public/css/theme.css` |
| P3-02 | 待开始 | 充值入口与短信饼图页收口 | `client/userpay.html`, `echarts/smspie.html`, `public/css/theme.css` |
| R-01 | 待开始 | 上线前全量回归 | 无代码或文档更新 |
| R-02 | 待开始 | 交付记录归档 | `docs/frontend-ui-priority-progress.md` |

## 3. 执行记录模板
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

## 4. 首条记录
```text
Step: baseline-reset
Commit: N/A
Changed Files: docs/frontend-ui-priority-task-board.md, docs/frontend-ui-priority-progress.md
Smoke Test:
- login: not run
- index+iframe: not run
- current page main flow: not applicable
Result: documentation only
Screenshots: N/A
Notes: 活跃看板已删除已完成项，并按剩余页面重排执行顺序
```

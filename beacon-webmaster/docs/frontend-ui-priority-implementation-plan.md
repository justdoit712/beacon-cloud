# 前端优化落地文档（分阶段执行）

## 1. 文档目的
本文件用于把前端优化需求落地为可执行计划，适用于当前项目的技术栈（`AdminLTE + Bootstrap + jQuery + Vue2 + iframe`）。

目标是：在不重写前端架构的前提下，快速提升观感、一致性和可用性，并可分阶段上线。

## 2. 当前现状（基线）

### 2.1 技术与页面结构
- 前端形态：多静态 HTML 页面 + iframe 主框架。
- 主样式：`src/main/resources/static/public/css/main.css`。
- 首页框架：`src/main/resources/static/index.html`。
- 登录页：`src/main/resources/static/login.html`。
- 控制台页：`src/main/resources/static/sys/main.html`。

### 2.2 当前问题（本次优化范围内）
- 全局样式污染：`main.css` 直接覆盖 `.row`、`.col-sm-10`、`.col-sm-2` 等通用类，容易导致跨页面布局异常。
- 表单布局固定：`.form-horizontal` 固定宽度，不适配小屏幕。
- 门面页面过旧：首页与控制台信息密度低，缺少“管理后台”的现代感。
- 交互细节不统一：按钮、表格、输入框、提示样式缺乏一致的视觉规范。
- 存在明文密码输入框：部分密码字段为 `type="text"`，需要修正。

## 3. 目标

### 3.1 目标
- 统一视觉风格（颜色、按钮、卡片、表格、表单、间距）。
- 提升登录页、首页、控制台的第一印象。
- 统一高频业务页的结构模板，提高可读性和操作效率。
- 保持接口与业务逻辑不变，确保低风险上线。

## 4. 总体执行策略

### 4.1 视觉统一与基础可用性（先落地）
- 核心思路：新增主题层样式，不动业务逻辑，优先处理视觉一致性与基础可用性。
- 输出重点：`theme.css` + 门面页面升级 + 样式污染收敛。

### 4.2 页面结构统一（分批推进）
- 核心思路：统一“查询区 - 按钮区 - 表格区 - 编辑区”页面骨架。
- 输出重点：先覆盖高频页面，再逐步复制到其他页面。

### 4.3 执行铁律（强制）
- 每一个最小改动步骤都必须可回退。
- 每一个最小改动步骤完成后都必须重启项目并查验。
- 禁止把多个不相关改动混在同一次提交中。
- 禁止使用 `git reset --hard` 这类破坏性回退方式。

#### 4.3.1 最小改动步骤定义
- 最多改动 1~3 个紧密关联文件。
- 只实现一个清晰目标（例如“登录页按钮样式统一”）。
- 每步结束都要形成独立提交，作为回退锚点。

#### 4.3.2 回退保障标准（每步必做）
- 改动前执行：`git status --short`，确认当前工作区状态。
- 改动后执行：`git add <本步骤文件>` + `git commit -m "<step-id>: <改动说明>"`。
- 回退方式（按场景）：
- 未提交时回退文件：`git restore -- <file>`
- 已提交时回退步骤：`git revert <commit_sha>`
- 仅回退某个文件到指定版本：`git restore --source <commit_sha> -- <file>`

#### 4.3.3 重启与查验标准（每步必做）
- 必须“停止应用 -> 重新启动 -> 手工冒烟验证”，不能只依赖热更新。
- IDE 方式（推荐）：停止并重新运行 `WebMasterStarterApp`。
- Maven 方式（可选）：在仓库根目录执行 `mvn -pl beacon-webmaster -am spring-boot:run`。
- 每步最少验证：
- `login.html` 登录成功/失败提示是否正常
- `index.html` 菜单跳转与 iframe 加载是否正常
- 当前改动页面的新增/修改/返回是否正常
- 验证通过后，记录“步骤编号 + 结果 + 截图”。

## 5. 分阶段计划（建议 2 周）

### 阶段 0：准备与基线（0.5 天）
- [ ] 建立分支：`feature/frontend-ui-priority-plan`
- [ ] 记录基线截图（登录页、首页、用户管理、客户管理、活动管理）
- [ ] 明确浏览器验证范围：Chrome/Edge 最新版本
- [ ] 与业务方确认“仅样式和页面结构调整，不改接口”

交付物：
- 截图目录（本地即可）  
- 优化前问题清单
- 阶段提交记录（至少 1 个可回退提交）

### 阶段 1：主题与全局收敛（2 天）

#### 1) 新增主题样式文件
- [ ] 新建 `src/main/resources/static/public/css/theme.css`
- [ ] 定义设计令牌（CSS 变量）：主色、成功色、危险色、背景色、边框色、圆角、阴影、间距
- [ ] 定义统一组件样式：按钮、输入框、面板、表格、分页、标签、弹层标题

#### 2) 接入主题文件
- [ ] 在 `src/main/resources/static/index.html` 引入 `theme.css`
- [ ] 在 `src/main/resources/static/login.html` 引入 `theme.css`
- [ ] 在所有引用 `main.css` 的业务页补充引入 `theme.css`（按批次做，首批高频页必须覆盖）

#### 3) 收敛 `main.css` 的全局污染
- [ ] 将 `main.css` 中对 `.row`、`.col-sm-*`、`.form-horizontal` 的全局规则改为局部作用域类
- [ ] 新增局部容器类（示例：`.page-form-layout`）并只在需要的页面使用
- [ ] 避免影响 bootstrap 原生栅格

验收标准：
- 页面无明显错位、遮挡、换行异常  
- 新旧页面能并存，不出现“局部改动影响其他页面”
- 本阶段内每个步骤都完成“提交锚点 + 重启查验”

### 阶段 2（高优先级 P1）：门面页面升级（1.5 天）

#### 1) 登录页优化
目标文件：`src/main/resources/static/login.html`
- [ ] 背景升级（渐变或轻纹理，不使用纯平色）
- [ ] 登录卡片视觉优化（圆角、阴影、标题层次）
- [ ] 输入框与按钮统一尺寸与状态反馈（hover/focus/disabled）
- [ ] 修复密码输入类型：`user.password` 改为 `type="password"`
- [ ] 错误提示样式统一（与主题一致）

#### 2) 首页框架优化
目标文件：`src/main/resources/static/index.html`
- [ ] 去除主要行内样式，转为类样式
- [ ] 优化顶部导航、侧边菜单、面包屑层次与间距
- [ ] 规范 iframe 容器视觉（留白、圆角、背景）
- [ ] 保持菜单和路由逻辑不变

#### 3) 控制台内容页改造
目标文件：`src/main/resources/static/sys/main.html`
- [ ] 从“单图片页”升级为控制台卡片布局（欢迎语 + 指标占位 + 常用入口）
- [ ] 支持桌面和移动端基础响应式

验收标准：
- 首屏观感达到“现代后台”水平  
- 登录/进入系统流程无回归
- 本阶段内每个步骤都完成“提交锚点 + 重启查验”

### 阶段 3（中优先级 P2）：高频业务页结构统一（3 天）

首批页面（建议优先）：
- `src/main/resources/static/sys/user.html`
- `src/main/resources/static/client/client.html`
- `src/main/resources/static/client/clientbusiness.html`
- `src/main/resources/static/channel/channel.html`
- `src/main/resources/static/activity/activity.html`

统一规范（必须一致）：
- [ ] 页面外层统一容器类（统一内边距与背景）
- [ ] 查询区固定在顶部（若页面有查询）
- [ ] 按钮区顺序统一：新增 > 修改 > 删除 > 刷新
- [ ] 表格区统一表头、行高、悬停色、分页位置
- [ ] 编辑区统一 label 宽度、表单项垂直间距、按钮区位置
- [ ] 弹层（layer）标题和按钮风格统一

补充修正：
- [ ] 修复 `sys/user.html` 中密码输入框为 `type="password"`
- [ ] 活动页中的 Element 组件区域与 Bootstrap 表单间距做统一桥接

验收标准：
- 高频页面视觉一致  
- 用户首次上手无需重新学习页面结构
- 本阶段内每个步骤都完成“提交锚点 + 重启查验”

### 阶段 4：可用性增强（2 天）
- [ ] 所有主要操作按钮增加清晰状态（hover/active/disabled/loading）
- [ ] 表单校验提示位置统一（输入框下方或右侧固定规则）
- [ ] 表格空状态文案统一（如“暂无数据，请调整筛选条件”）
- [ ] 关键页面适配 375px/768px/1366px 三种视口
- [ ] 常见可访问性修正（label 对应、焦点态可见）

验收标准：
- 移动端不出现横向滚动条  
- 键盘焦点可见，错误提示可读
- 本阶段内每个步骤都完成“提交锚点 + 重启查验”

## 6. 具体改动清单（文件级）

新增文件：
- `src/main/resources/static/public/css/theme.css`

重点修改文件：
- `src/main/resources/static/public/css/main.css`
- `src/main/resources/static/index.html`
- `src/main/resources/static/login.html`
- `src/main/resources/static/sys/main.html`
- `src/main/resources/static/sys/user.html`
- `src/main/resources/static/client/client.html`
- `src/main/resources/static/client/clientbusiness.html`
- `src/main/resources/static/channel/channel.html`
- `src/main/resources/static/activity/activity.html`

可选（视进度）：
- 其余引用 `main.css` 的页面按同模板批量收敛

## 7. 验收清单（上线前）

功能回归：
- [ ] 登录、退出、修改密码可正常使用
- [ ] 菜单跳转与 iframe 加载正常
- [ ] 表格增删改查流程正常

视觉一致性：
- [ ] 按钮样式一致
- [ ] 表单间距一致
- [ ] 表格头/行样式一致
- [ ] 弹窗风格一致

安全与规范：
- [ ] 密码输入框不再使用明文 `text`
- [ ] 不再新增高风险全局样式覆盖
- [ ] 行内样式持续减少

性能与兼容：
- [ ] 首屏无明显闪烁和布局抖动
- [ ] Chrome/Edge 验证通过
- [ ] 375px 宽度下核心流程可操作

## 8. 风险与回滚

主要风险：
- 局部页面依赖旧的全局样式，收敛后可能出现样式偏差。
- 页面混用 Bootstrap 与 Element 组件，细节对齐可能不一致。

控制措施：
- 每阶段独立提交（建议每天至少 1 个可回滚提交）。
- 优先改高频页，低频页后置，避免大面积同时改动。
- 每改 1 页做一次手工冒烟测试（新增/修改/删除/返回）。
- 每个最小改动步骤都必须重启应用后再验收。

回滚方案：
- 发现大面积样式回归时，先回退 `theme.css` 接入点，再逐页排查。
- 关键流程异常时优先回滚对应页面，不回滚整分支。

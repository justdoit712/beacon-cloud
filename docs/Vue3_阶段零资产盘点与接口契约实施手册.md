# Vue3 重构阶段零：资产盘点与接口契约实施手册

本手册是对《前端 Vue3 升级落地方案 V3.0》中**阶段零（资产盘点与接口契约冻结）**的详细执行规范。当前 `beacon-webmaster` 前端不是标准 Vue2 SPA，而是 Spring Boot 静态资源目录中的 HTML + jQuery + Bootstrap Table + AdminLTE + Vue 实例混合形态，因此必须先盘清资产和接口，再进入 Vue3 工程建设。

---

## 1. 阶段目标

阶段零的目标不是写新页面，而是建立后续重构的“地图”：

1. 明确需要迁移的所有页面、脚本、静态资源和第三方插件。
2. 明确每个页面依赖的后端接口、请求参数、响应结构和分页规则。
3. 冻结登录、鉴权、菜单、上传等核心接口契约，避免重构期间前后端口径漂移。
4. 确认 Vue3 新工程最终部署方式，为阶段四回滚预案提供依据。

---

## 2. 老前端资产盘点

### 2.1 盘点范围

以 `beacon-webmaster/src/main/resources/static` 为主，至少覆盖：

1. 入口页面：`login.html`、`index.html`。
2. 业务页面：各业务目录下的 `*.html`。
3. 页面脚本：`public/js/**/*.js`。
4. 公共脚本：`public/js/common.js`、`public/libs/app.js` 以及鉴权、表格、弹窗相关脚本。
5. 第三方插件：Bootstrap Table、Layer、zTree、UEditor、ECharts、AdminLTE 等。
6. 静态资源：图片、图标、主题样式、字体文件。

### 2.2 页面/API 盘点表

阶段零必须产出并持续维护页面/API 盘点表，当前文件为 `docs/Vue3_页面_API_盘点表.md`。

| 字段 | 说明 |
| --- | --- |
| 业务域 | 如系统管理、客户管理、通道管理、账户管理、统计图表 |
| 老页面路径 | 如 `static/activity/activity.html` |
| 老脚本路径 | 如 `static/public/js/activity/activity.js` |
| 目标 Vue3 路由 | 如 `/activity/list` |
| 目标组件路径 | 如 `src/views/activity/ActivityList.vue` |
| 后端接口 | 页面依赖的查询、新增、修改、删除、详情、导入导出接口 |
| 分页规则 | `limit/offset`、`page/pageSize` 或其他格式 |
| 依赖插件 | Bootstrap Table、zTree、UEditor、ECharts 等 |
| 隐性逻辑 | 老代码中的特殊兼容、默认值、边界判断、权限判断 |
| 负责人 | 前端开发责任人 |
| 验收用例 | 登录后访问、查询、重置、新增、编辑、删除、异常提示等 |
| 迁移状态 | 未开始、开发中、联调中、已验收 |

---

## 3. 核心接口契约冻结

### 3.1 登录与鉴权

Vue3 新工程必须沿用当前后端 JWT 鉴权口径：

1. **验证码接口**：`GET /sys/auth/captcha.jpg?uuid=<uuid>&t=<timestamp>`
   * 参数：`uuid` (前端生成的UUID)，`t` (当前毫秒时间戳)。
2. **登录接口**：`POST /sys/login`。
   * 请求 Payload (JSON):
     ```json
     {
       "username": "admin",
       "password": "MD5_password_or_raw",
       "captcha": "1234",
       "uuid": "uuid_string"
     }
     ```
3. **登录响应**：成功后从响应中取得 `data.token`，响应结构为：
   ```json
   {
     "code": 0,
     "msg": "success",
     "data": {
       "token": "JWT_TOKEN_STRING"
     }
   }
   ```
4. 前端本地存储 key 统一为 `Auth-Token`，便于和旧系统观察期兼容。
5. 所有受保护接口统一发送请求头：
   ```http
   Authorization: Bearer <token>
   ```
6. 后端认证过滤器读取的是 `Authorization`，不是 `Auth-Token`。`Auth-Token` 只作为浏览器本地存储 key 使用。
7. 遇到 HTTP `401` 时，前端必须清理本地登录态并跳转登录页。

### 3.2 密码传输口径

当前后端 `/sys/login` 接收前端传入的密码后，在服务端结合用户 salt 做 MD5 校验。阶段零结论如下：

1. Vue3 重构默认不新增 AES/RSA 前端加密，避免和现有后端登录逻辑不兼容。
2. 生产环境必须通过 HTTPS 保护登录请求传输。
3. 如果后续要增加前端加密，必须由后端先提供解密和密钥轮换方案，再单独立项修改登录契约。

### 3.3 菜单与权限

需要冻结以下接口的请求/响应结构：

1. **当前用户信息接口**：`GET /sys/user/info`
   * 响应 JSON 结构：
     ```json
     {
       "code": 0,
       "msg": "success",
       "data": {
         "userId": 1,
         "username": "admin"
       }
     }
     ```
2. **当前用户菜单树接口**：`GET /sys/menu/user`
   * 响应 JSON 结构中的菜单属性映射：
     * `menuId`: 菜单ID
     * `parentId`: 父菜单ID
     * `name`: 菜单显示文本 (映射至路由 `meta.title`)
     * `url`: 菜单对应的老页面HTML路径，如 `sys/user.html` (前端映射为 Vue3 路由 `path` 及 `component`)
     * `icon`: 菜单图标样式类，如 `fa fa-cog` (映射至路由 `meta.icon`)
     * `list`: 子菜单项数组 (映射至路由 `children` 属性)
     * `type`: 菜单类型 (0: 目录, 1: 菜单, 2: 按钮)

### 3.4 分页与表格

旧页面及后端 Controller 统一使用分页参数为：

```text
limit=10&offset=0&order=asc
```

* **重要结论**：Vue3 ProTable 必须向后端发送 `limit` 和 `offset` 两个参数用于分页，不能使用 `page` / `pageSize`。查询参数统一使用 `keyword` 进行关键字过滤。

### 3.5 上传与富文本

涉及 UEditor、图片上传、文件上传的页面必须单独登记：

1. 上传接口地址。
2. 文件字段名，如 `file`、`mypic`。
3. 返回 URL 字段。
4. 文件大小、类型限制。
5. 是否需要鉴权请求头。

---

## 4. 部署方式确认

阶段零必须由前端、后端、运维共同确认 Vue3 新工程最终上线形态，二选一并写入上线方案：

### 4.1 方式 A：独立 Nginx 静态站点

Vue3 工程单独构建 `dist`，由 Nginx 托管静态资源，并通过 Nginx 反向代理后端接口。

* 优点：前后端部署解耦，回滚时切换静态站点或网关规则即可。
* 注意：需要配置 History 路由 fallback、接口代理、静态资源缓存策略。

### 4.2 方式 B：打包进 Spring Boot 静态资源

Vue3 工程构建后的资源放入 `beacon-webmaster/src/main/resources/static` 或构建流程指定目录，随 `beacon-webmaster` 一起打包发布。

* 优点：贴近当前部署方式，减少新增服务器和代理配置。
* 注意：回滚需要恢复旧静态资源包或回滚 `beacon-webmaster` 应用版本；前端资源变更会绑定后端发布节奏。

---

## 5. 阶段零验收标准

阶段零结束时，必须具备以下交付物：

1. 页面/API 盘点表已完成，所有业务页面都有负责人和目标 Vue3 路由。
2. 登录、鉴权、菜单、分页、上传接口契约已冻结。
3. 已明确 UI 组件库统一使用 Element Plus。
4. 已明确密码传输不新增前端 AES/RSA，除非后端另行提供契约。
5. 已明确生产部署方式和对应回滚路径。

完成以上验收后，才进入阶段一新工程基建。

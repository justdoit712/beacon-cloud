# beacon-cache V2 API 契约（阶段 3.2）

## 目标

V2 接口用于替代 V1 中 `Object/Map/Set` 原始类型返回，先提供强类型读取能力，同时保留 V1 兼容。

## 响应结构

统一使用 `ResultVO<T>`：

- `code`: `0` 表示成功
- `msg`: 成功默认空字符串
- `data`: 具体数据

## V2 读取接口

1. `GET /v2/cache/hash/{key}`
- 返回：`ResultVO<Map<String, String>>`
- 说明：读取 Hash 全量字段，值统一转为字符串

2. `GET /v2/cache/hash/{key}/string/{field}`
- 返回：`ResultVO<String>`

3. `GET /v2/cache/hash/{key}/int/{field}`
- 返回：`ResultVO<Integer>`
- 说明：字段值不可转换为整型时返回 `400`

4. `GET /v2/cache/hash/{key}/long/{field}`
- 返回：`ResultVO<Long>`
- 说明：字段值不可转换为长整型时返回 `400`

5. `GET /v2/cache/string/{key}`
- 返回：`ResultVO<String>`

6. `GET /v2/cache/set/{key}/string-members`
- 返回：`ResultVO<Set<String>>`

7. `GET /v2/cache/set/{key}/map-members`
- 返回：`ResultVO<Set<Map<String, Object>>>`
- 说明：集合成员不是 Map 时返回 `400`

## 鉴权

- V2 路径已接入缓存鉴权拦截：`/v2/cache/**`
- 权限判定与 V1 一致（按读/写/keys/test 分类）

## 与 3.3 的关系

3.2 已提供 `/v2/cache/hash/{key}/string|int|long/{field}`，可直接作为 3.3 调用方 Feign 迁移目标路径。

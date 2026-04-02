# beacon-cloud 文档导航

文档类型：阅读导航  
适用对象：新同学 / 开发 / 排障 / 答辩  
验证基线：精简后文档结构  
关联模块：全局  
最后核对日期：2026-03-18

---

## 1. 当前文档结构

当前仓库保留 4 类文档：

1. 系统总览：1 份
2. 模块总文档：9 份
3. 专题方案：2 份
4. 路线图：1 份

建议理解为：**一模块一总文档 + 少量跨模块专题**。

---

## 2. 最推荐的阅读顺序

### 2.1 第一次接触项目

1. `01_level1_系统全局概览.md`
2. `02_统一重构优先级路线图.md`
3. `03_P0_冻结边界止血实施方案.md`

### 2.2 想看模块怎么改

1. `04_beacon-common_refactor_guide.md`
2. `05_beacon-cache_refactor_guide.md`
3. `06_beacon-api_refactor_guide.md`
4. `07_beacon-strategy_refactor_guide.md`
5. `08_beacon-smsgateway_refactor_guide.md`
6. `09_beacon-search_refactor_guide.md`
7. `10_beacon-push_refactor_guide.md`
8. `11_beacon-webmaster_refactor_guide.md`
9. `12_beacon-monitor_refactor_guide.md`

### 2.3 想看跨模块专题

1. `beacon-webmaster/src/docs/21_mysql_redis_sync_fix_guide.md`
2. `03_P0_冻结边界止血实施方案.md`
3. `02_统一重构优先级路线图.md`

---

## 3. 当前保留文档

### 3.1 系统总览

1. `01_level1_系统全局概览.md`

### 3.2 模块总文档

1. `04_beacon-common_refactor_guide.md`
2. `05_beacon-cache_refactor_guide.md`
3. `06_beacon-api_refactor_guide.md`
4. `07_beacon-strategy_refactor_guide.md`
5. `08_beacon-smsgateway_refactor_guide.md`
6. `09_beacon-search_refactor_guide.md`
7. `10_beacon-push_refactor_guide.md`
8. `11_beacon-webmaster_refactor_guide.md`
9. `12_beacon-monitor_refactor_guide.md`

### 3.3 专题方案

1. `beacon-webmaster/src/docs/21_mysql_redis_sync_fix_guide.md`
2. `03_P0_冻结边界止血实施方案.md`

### 3.4 路线图

1. `02_统一重构优先级路线图.md`

---

## 4. 维护建议

1. 后续新增模块文档，优先追加到对应“模块总文档”，不要再拆一份分析文档和一份改造文档
2. 新的跨模块问题，优先并入 `beacon-webmaster/src/docs/21_mysql_redis_sync_fix_guide.md`、`02_统一重构优先级路线图.md`、`03_P0_冻结边界止血实施方案.md` 这类已有专题或路线图，只有在明显独立时再新开专题
3. 如果某一份模块总文档再次膨胀到难读，再考虑按主题拆分，而不是恢复“分析 + 重构”双份结构

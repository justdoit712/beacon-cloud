# beacon-cloud 文档导航

文档类型：阅读导航  
适用对象：新同学 / 开发 / 排障 / 答辩  
验证基线：文档目录清点  
关联模块：全局  
最后核对日期：2026-03-17

---

## 1. 快速入口

1. 想先看系统整体：先读 `22` -> `23` -> `24`
2. 想看主链路细节：读 `sms-send-workflow-analysis.md`
3. 想看某个模块怎么改：先读对应 `*_module_analysis.md`，再读对应 `*_refactor_guide.md`
4. 想看项目接下来往哪走：读 `sms-business-architecture-evolution-roadmap.md`
5. 想看当前还没完成什么：读 `status_matrix.md`
6. 想看缓存同步门面层怎么做增强：读 `增强_第五层AOP适用点说明.md`

## 2. 按角色阅读

### 2.1 新同学入门

1. `22_level1_系统全局概览.md`
2. `23_level2_模块与架构构成.md`
3. `24_level3_核心业务链路分析.md`
4. `sms-send-workflow-analysis.md`

### 2.2 开发改模块

1. 先找对应模块分析文档：
   `01/03/05/07/10/12/14/16/18`
2. 再看对应重构文档：
   `02/04/06/09/11/13/15/17/19/20`
3. 如果涉及缓存一致性，再补读：
   `21_mysql_redis_sync_fix_guide.md`

### 2.3 排障定位

1. 主链路问题：`24_level3_核心业务链路分析.md`
2. 对象流转问题：`sms-send-workflow-analysis.md`
3. 模块风险深挖：对应模块分析文档，策略专项看 `08_strategy_module_risk_explanation.md`
4. 当前未完成项总表：`status_matrix.md`

### 2.4 答辩与展示

1. `22_level1_系统全局概览.md`
2. `23_level2_模块与架构构成.md`
3. `24_level3_核心业务链路分析.md`
4. `21_mysql_redis_sync_fix_guide.md`
5. `sms-business-architecture-evolution-roadmap.md`

## 3. 文档分组

### 3.1 模块分析

1. `01_beacon-common_module_analysis.md`
2. `03_beacon-cache_module_analysis.md`
3. `05_beacon-api_module_analysis.md`
4. `07_beacon-strategy_module_analysis.md`
5. `10_beacon-smsgateway_module_analysis.md`
6. `12_beacon-search_module_analysis.md`
7. `14_beacon-push_module_analysis.md`
8. `16_beacon-webmaster_module_analysis.md`
9. `18_beacon-monitor_module_analysis.md`

### 3.2 重构指南

1. `02_beacon-common_refactor_guide.md`
2. `04_beacon-cache_refactor_guide.md`
3. `06_beacon-api_refactor_guide.md`
4. `09_beacon-strategy_refactor_guide.md`
5. `11_beacon-smsgateway_refactor_guide.md`
6. `13_beacon-search_refactor_guide.md`
7. `15_beacon-push_refactor_guide.md`
8. `17_beacon-webmaster_refactor_guide.md`
9. `19_beacon-monitor_refactor_guide.md`
10. `20_beacon-test_refactor_guide.md`

### 3.3 专题文档

1. `08_strategy_module_risk_explanation.md`
2. `21_mysql_redis_sync_fix_guide.md`
3. `sms-send-workflow-analysis.md`
4. `sms-business-architecture-evolution-roadmap.md`
5. `增强_第五层AOP适用点说明.md`

### 3.4 分层总览

1. `22_level1_系统全局概览.md`
2. `23_level2_模块与架构构成.md`
3. `24_level3_核心业务链路分析.md`

## 4. 维护建议

1. 新增模块文档时，优先补：模块分析 + 重构指南
2. 新增专题时，补到“专题文档”分组，不必强行编号
3. 文档更新后，同步维护 `status_matrix.md`

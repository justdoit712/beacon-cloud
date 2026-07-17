<template>
  <div class="console-dashboard">
    <!-- Console Hero -->
    <div class="console-hero">
      <div class="console-hero-copy">
        <span class="console-eyebrow">Beacon Webmaster</span>
        <h1>欢迎使用烽火云短信平台</h1>
        <p class="console-subtitle">集中查看关键指标，快速进入高频页面。</p>
      </div>
      <div class="console-hero-visual">
        <el-icon class="hero-icon"><Monitor /></el-icon>
      </div>
    </div>

    <!-- Metrics Cards -->
    <div class="console-section">
      <div class="console-section-head">
        <h2>指标概览</h2>
        <span class="sub-text">实时统计关键业务运营指标</span>
      </div>
      
      <el-row :gutter="20" class="console-kpi-grid">
        <el-col :xs="24" :sm="12" :md="6" v-for="item in kpis" :key="item.label">
          <el-card shadow="hover" class="console-kpi-card" :body-style="{ padding: '20px' }">
            <div class="kpi-icon-wrap" :style="{ background: item.bgColor, color: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="kpi-content">
              <p class="console-kpi-label">{{ item.label }}</p>
              <p class="console-kpi-value">{{ item.value }}</p>
              <p class="console-kpi-meta">{{ item.meta }}</p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- Shortcuts -->
    <div class="console-section">
      <div class="console-section-head">
        <h2>常用入口</h2>
        <span class="sub-text">点击快速跳转至高频管理页面</span>
      </div>

      <el-row :gutter="20" class="console-shortcut-grid">
        <el-col :xs="12" :sm="6" v-for="link in shortcuts" :key="link.title">
          <el-card shadow="hover" class="console-shortcut-item" @click="handleNavigate(link.path)">
            <div class="shortcut-icon" :style="{ color: link.color }">
              <el-icon><component :is="link.icon" /></el-icon>
            </div>
            <span class="shortcut-title">{{ link.title }}</span>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Cpu, Checked, Bell, User, Briefcase, Connection, Promotion } from '@element-plus/icons-vue'

const router = useRouter()

const kpis = ref([
  {
    label: '今日提交',
    value: '1,284,592 条',
    meta: '较昨日 +12.4%',
    icon: Promotion,
    color: '#409EFF',
    bgColor: 'rgba(64, 158, 255, 0.1)'
  },
  {
    label: '发送成功率',
    value: '99.82%',
    meta: '近 24 小时平均',
    icon: Checked,
    color: '#67C23A',
    bgColor: 'rgba(103, 194, 58, 0.1)'
  },
  {
    label: '待处理任务',
    value: '0 个',
    meta: '任务池运行正常',
    icon: Cpu,
    color: '#E6A23C',
    bgColor: 'rgba(230, 162, 60, 0.1)'
  },
  {
    label: '异常告警',
    value: '0 条',
    meta: '系统状态良好',
    icon: Bell,
    color: '#F56C6C',
    bgColor: 'rgba(245, 108, 108, 0.1)'
  }
])

const shortcuts = [
  { title: '用户管理', path: '/sys/user', icon: User, color: '#409EFF' },
  { title: '客户管理', path: '/client/client', icon: Briefcase, color: '#67C23A' },
  { title: '渠道管理', path: '/channel/channel', icon: Connection, color: '#E6A23C' },
  { title: '活动管理', path: '/activity/activity', icon: Promotion, color: '#909399' }
]

function handleNavigate(path: string) {
  router.push(path)
}
</script>

<style scoped>
.console-dashboard {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}

/* Hero Section */
.console-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #1f2937, #111827);
  color: #fff;
  border-radius: 12px;
  padding: 40px;
  margin-bottom: 30px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}
.console-eyebrow {
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  color: var(--el-color-primary);
  font-weight: 600;
  margin-bottom: 8px;
  display: block;
}
.console-hero-copy h1 {
  font-size: 28px;
  margin: 0 0 12px 0;
  font-weight: 700;
}
.console-subtitle {
  color: #9ca3af;
  font-size: 16px;
  margin: 0;
}
.hero-icon {
  font-size: 80px;
  color: rgba(255, 255, 255, 0.15);
}

/* Sections */
.console-section {
  margin-bottom: 35px;
}
.console-section-head {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  gap: 10px;
}
.console-section-head h2 {
  font-size: 20px;
  margin: 0;
  font-weight: 600;
}
.sub-text {
  font-size: 13px;
  color: #909399;
}

/* KPI Cards */
.console-kpi-card {
  display: flex;
  align-items: center;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  transition: all 0.3s;
}
.console-kpi-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  width: 100%;
}
.kpi-icon-wrap {
  width: 54px;
  height: 54px;
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 26px;
  margin-right: 16px;
  flex-shrink: 0;
}
.kpi-content {
  flex-grow: 1;
}
.console-kpi-label {
  font-size: 14px;
  color: #909399;
  margin: 0 0 4px 0;
}
.console-kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 4px 0;
}
.console-kpi-meta {
  font-size: 12px;
  color: #67C23A;
  margin: 0;
}

/* Shortcuts */
.console-shortcut-item {
  text-align: center;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: all 0.3s;
  background-color: var(--el-fill-color-blank);
}
.console-shortcut-item:hover {
  transform: translateY(-4px);
  border-color: var(--el-color-primary);
}
.shortcut-icon {
  font-size: 32px;
  margin-bottom: 12px;
  display: inline-flex;
}
.shortcut-title {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
</style>

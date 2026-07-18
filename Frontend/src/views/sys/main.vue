<template>
  <div class="console-dashboard relative z-10 p-4 max-w-7xl mx-auto">
    <!-- Console Hero -->
    <div 
      class="console-hero glass relative overflow-hidden rounded-2xl p-10 mb-8 flex justify-between items-center"
      v-motion
      :initial="{ opacity: 0, y: 50 }"
      :enter="{ opacity: 1, y: 0, transition: { duration: 800, type: 'spring' } }"
    >
      <!-- Hero bg glow -->
      <div class="absolute -right-20 -top-20 w-64 h-64 bg-purple-500/20 rounded-full blur-3xl"></div>
      
      <div class="console-hero-copy relative z-10">
        <span class="console-eyebrow text-sm uppercase tracking-widest text-purple-400 font-semibold mb-2 block">Beacon Webmaster</span>
        <h1 class="text-3xl font-bold text-white mb-3">欢迎使用烽火云短信平台</h1>
        <p class="console-subtitle text-gray-300 text-base m-0">集中查看关键指标，快速进入高频页面。</p>
      </div>
      <div class="console-hero-visual relative z-10">
        <el-icon class="hero-icon text-8xl text-white/20"><Monitor /></el-icon>
      </div>
    </div>

    <!-- Metrics Cards -->
    <div class="console-section mb-10">
      <div 
        class="console-section-head flex items-center gap-3 mb-5"
        v-motion
        :initial="{ opacity: 0, x: -30 }"
        :enter="{ opacity: 1, x: 0, transition: { duration: 600, delay: 200 } }"
      >
        <h2 class="text-xl font-semibold text-white m-0">指标概览</h2>
        <span class="sub-text text-sm text-gray-400">实时统计关键业务运营指标</span>
      </div>
      
      <el-row :gutter="20" class="console-kpi-grid">
        <el-col 
          :xs="24" :sm="12" :md="6" 
          v-for="(item, index) in kpis" 
          :key="item.label"
        >
          <div 
            v-motion
            :initial="{ opacity: 0, y: 30 }"
            :enter="{ opacity: 1, y: 0, transition: { duration: 500, delay: 300 + index * 100 } }"
          >
            <el-card shadow="hover" class="console-kpi-card glass border-none !bg-white/5 hover:!bg-white/10 transition-all duration-300 transform hover:-translate-y-1" :body-style="{ padding: '20px' }">
              <div class="flex items-center w-full">
                <div class="kpi-icon-wrap w-14 h-14 rounded-xl flex justify-center items-center text-2xl mr-4 shrink-0" :style="{ background: item.bgColor, color: item.color }">
                  <el-icon><component :is="item.icon" /></el-icon>
                </div>
                <div class="kpi-content flex-grow">
                  <p class="console-kpi-label text-sm text-gray-400 m-0 mb-1">{{ item.label }}</p>
                  <p class="console-kpi-value text-2xl font-bold text-white m-0 mb-1">{{ item.value }}</p>
                  <p class="console-kpi-meta text-xs m-0" :class="item.color === '#F56C6C' ? 'text-red-400' : 'text-green-400'">{{ item.meta }}</p>
                </div>
              </div>
            </el-card>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- Shortcuts -->
    <div class="console-section">
      <div 
        class="console-section-head flex items-center gap-3 mb-5"
        v-motion
        :initial="{ opacity: 0, x: -30 }"
        :enter="{ opacity: 1, x: 0, transition: { duration: 600, delay: 600 } }"
      >
        <h2 class="text-xl font-semibold text-white m-0">常用入口</h2>
        <span class="sub-text text-sm text-gray-400">点击快速跳转至高频管理页面</span>
      </div>

      <el-row :gutter="20" class="console-shortcut-grid">
        <el-col 
          :xs="12" :sm="6" 
          v-for="(link, index) in shortcuts" 
          :key="link.title"
        >
          <div
            v-motion
            :initial="{ opacity: 0, scale: 0.9 }"
            :enter="{ opacity: 1, scale: 1, transition: { duration: 400, delay: 800 + index * 100 } }"
          >
            <el-card 
              shadow="hover" 
              class="console-shortcut-item glass border-none !bg-white/5 hover:!bg-white/15 transition-all duration-300 transform hover:-translate-y-2 cursor-pointer text-center" 
              @click="handleNavigate(link.path)"
            >
              <div class="shortcut-icon text-3xl mb-3 inline-flex p-3 rounded-full bg-white/5" :style="{ color: link.color }">
                <el-icon><component :is="link.icon" /></el-icon>
              </div>
              <span class="shortcut-title block text-base font-medium text-white">{{ link.title }}</span>
            </el-card>
          </div>
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
    bgColor: 'rgba(64, 158, 255, 0.15)'
  },
  {
    label: '发送成功率',
    value: '99.82%',
    meta: '近 24 小时平均',
    icon: Checked,
    color: '#67C23A',
    bgColor: 'rgba(103, 194, 58, 0.15)'
  },
  {
    label: '待处理任务',
    value: '0 个',
    meta: '任务池运行正常',
    icon: Cpu,
    color: '#E6A23C',
    bgColor: 'rgba(230, 162, 60, 0.15)'
  },
  {
    label: '异常告警',
    value: '0 条',
    meta: '系统状态良好',
    icon: Bell,
    color: '#F56C6C',
    bgColor: 'rgba(245, 108, 108, 0.15)'
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
/* Scoped styles are mostly replaced by Tailwind utility classes */
</style>

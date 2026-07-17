<template>
  <div class="smspie-container">
    <el-card class="filter-card">
      <el-form :inline="true" :model="form" class="filter-form">
        <el-form-item label="客户业务">
          <el-select v-model="form.clientID" placeholder="请选择客户业务" clearable>
            <el-option
              v-for="item in sites"
              :key="item.id"
              :label="item.corpname"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.starttime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="x"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.stoptime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="x"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">查询</el-button>
          <el-button @click="resetForm" :disabled="loading">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="chartMessage"
        :title="chartMessage"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      />
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card total-card">
            <div class="stat-title">发送总量</div>
            <div class="stat-value">{{ stats.total }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card success-card">
            <div class="stat-title">成功量</div>
            <div class="stat-value">{{ stats.success }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card fail-card">
            <div class="stat-title">失败量</div>
            <div class="stat-value">{{ stats.failed }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card wait-card">
            <div class="stat-title">等待量</div>
            <div class="stat-value">{{ stats.waiting }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="chart-card">
      <div ref="chartRef" class="chart-canvas"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getPieChartData } from '@/api/echarts'
import { getClientBusinessAll } from '@/api/client'
import { ElMessage } from 'element-plus'

const chartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

const loading = ref(false)
const chartMessage = ref('设置筛选条件后，可在这里查看图表反馈。')

const form = reactive({
  clientID: '',
  starttime: '',
  stoptime: ''
})

const stats = reactive({
  waiting: 0,
  success: 0,
  failed: 0,
  total: 0
})

const sites = ref<any[]>([])

const getSites = async () => {
  try {
    const res = await getClientBusinessAll()
    if (res.code === 0 && res.data) {
      sites.value = res.data
    }
  } catch (error) {
    console.error(error)
  }
}

const findSeriesValue = (seriesData: any[], targetName: string) => {
  for (let i = 0; i < seriesData.length; i++) {
    if (seriesData[i] && seriesData[i].name === targetName) {
      return Number(seriesData[i].value || 0)
    }
  }
  return 0
}

const initChart = () => {
  if (!chartRef.value) return
  if (!myChart) {
    myChart = echarts.init(chartRef.value)
  }
}

const handleSearch = async () => {
  loading.value = true
  chartMessage.value = '正在加载统计结果，请稍候...'
  
  const params: any = {}
  if (form.clientID) params.clientID = form.clientID
  if (form.starttime) params.starttime = form.starttime
  if (form.stoptime) params.stoptime = form.stoptime

  try {
    const res = await getPieChartData(params)
    if (res.code !== 0) {
      chartMessage.value = res.msg || '统计请求失败，请稍后重试。'
      stats.waiting = 0
      stats.success = 0
      stats.failed = 0
      stats.total = 0
      ElMessage.error(chartMessage.value)
      return
    }

    const payload = res.data || {}
    const legendData = Array.isArray(payload.legendData) ? payload.legendData : []
    const seriesData = Array.isArray(payload.seriesData) ? payload.seriesData : []

    stats.waiting = findSeriesValue(seriesData, '等待')
    stats.success = findSeriesValue(seriesData, '成功')
    stats.failed = findSeriesValue(seriesData, '失败')
    stats.total = stats.waiting + stats.success + stats.failed

    chartMessage.value = stats.total > 0 ? '图表已刷新，可结合上方汇总卡片查看当前状态分布。' : '当前筛选条件下暂无统计数据。'

    if (myChart) {
      myChart.setOption({
        title: {
          text: '成功率统计',
          subtext: stats.total > 0 ? '真实有效' : '暂无数据',
          x: 'center'
        },
        tooltip: {
          trigger: 'item',
          formatter: "{a} <br/>{b} : {c} ({d}%)"
        },
        legend: {
          orient: 'vertical',
          left: 'left',
          data: legendData
        },
        series: [
          {
            name: '成功率统计',
            type: 'pie',
            radius: '75%',
            center: ['50%', '55%'],
            data: seriesData,
            itemStyle: {
              emphasis: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      })
    }
  } catch (error) {
    console.error(error)
    chartMessage.value = '统计请求失败，请稍后重试。'
    stats.waiting = 0
    stats.success = 0
    stats.failed = 0
    stats.total = 0
    ElMessage.error(chartMessage.value)
  } finally {
    loading.value = false
    myChart?.resize()
  }
}

const resetForm = () => {
  if (loading.value) return
  form.clientID = ''
  form.starttime = ''
  form.stoptime = ''
  handleSearch()
}

const handleResize = () => {
  myChart?.resize()
}

onMounted(() => {
  getSites()
  initChart()
  handleSearch()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (myChart) {
    myChart.dispose()
  }
})
</script>

<style scoped>
.smspie-container {
  padding: 20px;
}
.filter-card {
  margin-bottom: 20px;
}
.stats-row {
  margin-top: 20px;
}
.stat-card {
  text-align: center;
}
.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
}
.stat-value {
  font-size: 24px;
  font-weight: bold;
}
.total-card .stat-value { color: #409EFF; }
.success-card .stat-value { color: #67C23A; }
.fail-card .stat-value { color: #F56C6C; }
.wait-card .stat-value { color: #E6A23C; }

.chart-card {
  width: 100%;
}
.chart-canvas {
  width: 100%;
  height: 500px;
}
</style>

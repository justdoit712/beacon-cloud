<template>
  <div class="echarts-container">
    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>Line Chart</span>
        </div>
      </template>
      <div ref="chartRef" class="chart-canvas"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getLineChartData } from '@/api/echarts'
import { ElMessage } from 'element-plus'

const chartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

const initChart = async () => {
  if (!chartRef.value) return
  
  myChart = echarts.init(chartRef.value)
  
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: []
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        data: [],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#67C23A'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103,194,58,0.5)' },
            { offset: 1, color: 'rgba(103,194,58,0.1)' }
          ])
        }
      }
    ]
  }

  try {
    const res = await getLineChartData({})
    if (res.code === 0 && res.data) {
      option.xAxis.data = res.data.xAxis || []
      option.series[0].data = res.data.seriesData || []
      myChart.setOption(option)
    } else {
      ElMessage.error(res.msg || '获取数据失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('网络请求异常')
  }
}

const handleResize = () => {
  myChart?.resize()
}

onMounted(() => {
  initChart()
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
.echarts-container {
  padding: 20px;
}
.chart-card {
  width: 100%;
}
.card-header {
  font-weight: bold;
}
.chart-canvas {
  width: 100%;
  height: 400px;
}
</style>

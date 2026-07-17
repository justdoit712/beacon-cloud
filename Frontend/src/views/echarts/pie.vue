<template>
  <div class="echarts-container">
    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>Pie Chart</span>
        </div>
      </template>
      <div ref="chartRef" class="chart-canvas"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getPieChartData } from '@/api/echarts'
import { ElMessage } from 'element-plus'

const chartRef = ref<HTMLElement | null>(null)
let myChart: echarts.ECharts | null = null

const initChart = async () => {
  if (!chartRef.value) return
  
  myChart = echarts.init(chartRef.value)
  
  const option = {
    title: {
      text: 'Success Rate',
      subtext: 'Real Data',
      x: 'center'
    },
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b} : {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      data: []
    },
    series: [
      {
        name: 'Success Rate',
        type: 'pie',
        radius: '55%',
        center: ['50%', '60%'],
        data: [],
        itemStyle: {
          emphasis: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  try {
    const res = await getPieChartData({})
    if (res.code === 0 && res.data) {
      option.legend.data = res.data.legendData || []
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

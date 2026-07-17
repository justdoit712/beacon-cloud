import request from '@/utils/request'

export const getPieChartData = (params: any): Promise<any> => {
  return request.get('/sys/echarts/pie', { params })
}

export const getLineChartData = (params: any): Promise<any> => {
  return request.get('/sys/echarts/line', { params })
}

export const getBarChartData = (params: any): Promise<any> => {
  return request.get('/sys/echarts/bar', { params })
}

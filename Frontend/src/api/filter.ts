import request from '@/utils/request'

// 获取 API 网关过滤器列表
export function getGatewayFilterList() {
  return request.get('/sys/api-gateway-filter/list')
}

// 获取客户策略链列表
export function getStrategyFilterList(params: any) {
  return request.get('/sys/strategy-filter/list', { params })
}

// 获取客户策略链详情
export function getStrategyFilterInfo(id: number | string) {
  return request.get(`/sys/strategy-filter/info/${id}`)
}

// 修改客户策略链
export function updateStrategyFilter(data: any) {
  return request.post('/sys/strategy-filter/update', data)
}

// 获取所有系统支持的过滤器标识列表
export function getAllSupportedFilters() {
  return request.get('/sys/strategy-filter/filters/all')
}

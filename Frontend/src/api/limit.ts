import request from '@/utils/request'

// 获取限流配置列表
export function getLimitList(params: any) {
  return request.get('/sys/limit/list', { params })
}

// 获取限流配置详情
export function getLimitInfo(id: number | string) {
  return request.get(`/sys/limit/info/${id}`)
}

// 新增限流配置
export function saveLimit(data: any) {
  return request.post('/sys/limit/save', data)
}

// 修改限流配置
export function updateLimit(data: any) {
  return request.post('/sys/limit/update', data)
}

// 批量删除限流配置
export function deleteLimits(ids: any[]) {
  return request.post('/sys/limit/del', ids)
}

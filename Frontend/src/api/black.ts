import request from '@/utils/request'

// 获取黑名单列表
export function getBlackList(params: any) {
  return request.get('/sys/black/list', { params })
}

// 获取黑名单详情
export function getBlackInfo(id: number) {
  return request.get(`/sys/black/info/${id}`)
}

// 新增黑名单
export function saveBlack(data: any) {
  return request.post('/sys/black/save', data)
}

// 修改黑名单
export function updateBlack(data: any) {
  return request.post('/sys/black/update', data)
}

// 批量删除黑名单
export function deleteBlacks(ids: number[]) {
  return request.post('/sys/black/del', ids)
}

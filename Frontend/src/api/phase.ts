import request from '@/utils/request'

// 获取号段列表
export function getPhaseList(params: any) {
  return request.get('/sys/phase/list', { params })
}

// 获取号段详情
export function getPhaseInfo(id: number) {
  return request.get(`/sys/phase/info/${id}`)
}

// 新增号段
export function savePhase(data: any) {
  return request.post('/sys/phase/save', data)
}

// 修改号段
export function updatePhase(data: any) {
  return request.post('/sys/phase/update', data)
}

// 批量删除号段
export function deletePhases(ids: number[]) {
  return request.post('/sys/phase/del', ids)
}

// 获取省份列表
export function getProvinces() {
  return request.get('/sys/provinces/all')
}

// 获取城市列表
export function getCities(provId: string | number) {
  return request.get(`/sys/cities/all/${provId}`)
}

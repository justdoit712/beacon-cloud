import request from '@/utils/request'

// 获取到账账单列表
export function getAcountList(params: any) {
  return request.get('/sys/account/list', { params })
}

// 获取到账账单详情
export function getAcountInfo(id: number | string) {
  return request.get(`/sys/account/info/${id}`)
}

// 新增到账账单
export function saveAcount(data: any) {
  return request.post('/sys/account/save', data)
}

// 修改到账账单
export function updateAcount(data: any) {
  return request.post('/sys/account/update', data)
}

// 批量删除到账账单
export function deleteAcountBatch(ids: any[]) {
  return request.post('/sys/account/del', ids)
}

import request from '@/utils/request'

// 获取 Legacy 列表
export function getLegacyList(family: string, params: any) {
  return request.get(`/sys/${family}/list`, { params })
}

// 获取 Legacy 详情
export function getLegacyInfo(family: string, id: number | string) {
  return request.get(`/sys/${family}/info/${id}`)
}

// 新增 Legacy 配置
export function saveLegacy(family: string, data: any) {
  return request.post(`/sys/${family}/save`, data)
}

// 修改 Legacy 配置
export function updateLegacy(family: string, data: any) {
  return request.post(`/sys/${family}/update`, data)
}

// 批量删除 Legacy 配置
export function deleteLegacyBatch(family: string, ids: any[]) {
  return request.post(`/sys/${family}/del`, ids)
}

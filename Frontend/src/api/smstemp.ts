import request from '@/utils/request'

// 获取模板列表
export function getTemplateList(params: any) {
  return request.get('/sys/sms-template/list', { params })
}

// 获取模板详情
export function getTemplateInfo(id: number | string) {
  return request.get(`/sys/sms-template/info/${id}`)
}

// 新增模板
export function saveTemplate(data: any) {
  return request.post('/sys/sms-template/save', data)
}

// 修改模板
export function updateTemplate(data: any) {
  return request.post('/sys/sms-template/update', data)
}

// 批量删除模板
export function deleteTemplates(ids: any[]) {
  return request.post('/sys/sms-template/del', ids)
}

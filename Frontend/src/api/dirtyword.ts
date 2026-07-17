import request from '@/utils/request'

// 获取敏感词列表
export function getDirtyWordList(params: any) {
  return request.get('/sys/message/list', { params })
}

// 获取敏感词详情
export function getDirtyWordInfo(id: number) {
  return request.get(`/sys/message/info/${id}`)
}

// 新增敏感词
export function saveDirtyWord(data: any) {
  return request.post('/sys/message/save', data)
}

// 修改敏感词
export function updateDirtyWord(data: any) {
  return request.post('/sys/message/update', data)
}

// 批量删除敏感词
export function deleteDirtyWords(ids: number[]) {
  return request.post('/sys/message/del', ids)
}

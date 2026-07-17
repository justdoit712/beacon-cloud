import request from '@/utils/request'

// 获取活动列表
export function getActivityList(params: any) {
  return request.get('/sys/activity/list', { params })
}

// 获取活动详情
export function getActivityInfo(id: number) {
  return request.get(`/sys/activity/info/${id}`)
}

// 新增活动
export function saveActivity(data: any) {
  return request.post('/sys/activity/save', data)
}

// 修改活动
export function updateActivity(data: any) {
  return request.post('/sys/activity/update', data)
}

// 批量删除活动
export function deleteActivities(ids: number[]) {
  return request.post('/sys/activity/del', ids)
}

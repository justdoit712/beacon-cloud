import request from '@/utils/request'

// 获取通道列表
export function getChannelList(params: any) {
  return request.get('/sys/channel/list', { params })
}

// 获取全部激活的通道选项
export function getAllChannels() {
  return request.get('/sys/channel/all')
}

// 获取通道详情
export function getChannelInfo(id: number | string) {
  return request.get(`/sys/channel/info/${id}`)
}

// 新增通道
export function saveChannel(data: any) {
  return request.post('/sys/channel/save', data)
}

// 修改通道
export function updateChannel(data: any) {
  return request.post('/sys/channel/update', data)
}

// 批量删除通道
export function deleteChannels(ids: any[]) {
  return request.post('/sys/channel/del', ids)
}

import request from '@/utils/request'

// =================== 客户管理 (client) ===================
export function getClientList(params: any) {
  return request.get('/sys/client/list', { params })
}

export function getClientInfo(id: number | string) {
  return request.get(`/sys/client/info/${id}`)
}

export function saveClient(data: any) {
  return request.post('/sys/client/save', data)
}

export function updateClient(data: any) {
  return request.post('/sys/client/update', data)
}

export function deleteClients(ids: any[]) {
  return request.post('/sys/client/del', ids)
}

// =================== 客户业务 (clientbusiness) ===================
export function getClientBusinessList(params: any) {
  return request.get('/sys/client-business/list', { params })
}

export function getClientBusinessInfo(id: number | string) {
  return request.get(`/sys/client-business/info/${id}`)
}

export function saveClientBusiness(data: any) {
  return request.post('/sys/client-business/save', data)
}

export function updateClientBusiness(data: any) {
  return request.post('/sys/client-business/update', data)
}

export function deleteClientBusinesses(ids: any[]) {
  return request.post('/sys/client-business/del', ids)
}

export function getClientBusinessAll() {
  return request.get('/sys/client-business/all')
}

// =================== 客户通道 (clientchannel) ===================
export function getClientChannelList(params: any) {
  return request.get('/sys/client-channel/list', { params })
}

export function getClientChannelInfo(id: number | string) {
  return request.get(`/sys/client-channel/info/${id}`)
}

export function saveClientChannel(data: any) {
  return request.post('/sys/client-channel/save', data)
}

export function updateClientChannel(data: any) {
  return request.post('/sys/client-channel/update', data)
}

export function deleteClientChannels(ids: any[]) {
  return request.post('/sys/client-channel/del', ids)
}

// =================== 辅助接口 (channel) ===================
export function getAllChannels() {
  return request.get('/sys/channel/all')
}

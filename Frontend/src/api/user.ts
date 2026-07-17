import request from '@/utils/request'

// 获取用户列表
export function getUserList(params: any) {
  return request.get('/sys/user/list', { params })
}

// 获取用户详情
export function getUserInfo(id: number) {
  return request.get(`/sys/user/info/${id}`)
}

// 新增用户
export function saveUser(data: any) {
  return request.post('/sys/user/save', data)
}

// 修改用户
export function updateUser(data: any) {
  return request.post('/sys/user/update', data)
}

// 批量删除用户
export function deleteUsers(ids: number[]) {
  return request.post('/sys/user/del', ids)
}

// 获取所有所属客户数据列表
export function getAllClients() {
  return request.get('/sys/client-business/all')
}

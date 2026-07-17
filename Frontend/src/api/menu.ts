import request from '@/utils/request'

// 获取菜单列表
export function getMenuList(params: any) {
  return request.get('/sys/menu/list', { params })
}

// 获取菜单详情
export function getMenuInfo(id: number) {
  return request.get(`/sys/menu/info/${id}`)
}

// 获取上级菜单选择树
export function getMenuSelect() {
  return request.get('/sys/menu/select')
}

// 新增菜单
export function saveMenu(data: any) {
  return request.post('/sys/menu/save', data)
}

// 修改菜单
export function updateMenu(data: any) {
  return request.post('/sys/menu/update', data)
}

// 批量删除菜单
export function deleteMenus(ids: number[]) {
  return request.post('/sys/menu/del', ids)
}

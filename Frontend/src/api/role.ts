import request from '@/utils/request'

// 获取角色列表
export function getRoleList(params: any) {
  return request.get('/sys/role/list', { params })
}

// 获取角色详情
export function getRoleInfo(id: number) {
  return request.get(`/sys/role/info/${id}`)
}

// 新增角色
export function saveRole(data: any) {
  return request.post('/sys/role/save', data)
}

// 修改角色
export function updateRole(data: any) {
  return request.post('/sys/role/update', data)
}

// 批量删除角色
export function deleteRoles(ids: number[]) {
  return request.post('/sys/role/del', ids)
}

// 获取菜单树
export function getMenuTree() {
  return request.get('/sys/role/menu/tree')
}

// 获取角色拥有的菜单 ID 数组
export function getRoleMenuIds(roleId: number) {
  return request.get(`/sys/role/menu/${roleId}`)
}

// 分配角色菜单权限
export function assignRoleMenus(roleId: number, menuIds: number[]) {
  // Spring MVC @RequestParam 接收 List<Integer>，通过逗号拼接的字符串传递
  const params = new URLSearchParams()
  params.append('roleId', String(roleId))
  if (menuIds && menuIds.length > 0) {
    params.append('menuIds', menuIds.join(','))
  }
  return request.post('/sys/role/menu/assign', null, { params })
}

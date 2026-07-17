<template>
  <template v-if="menu.type === 0 || (menu.list && menu.list.length > 0)">
    <el-sub-menu :index="String(menu.menuId)">
      <template #title>
        <el-icon v-if="menu.icon"><component :is="getIconComponent(menu.icon)" /></el-icon>
        <span>{{ menu.name }}</span>
      </template>
      <sidebar-item
        v-for="child in menu.list"
        :key="child.menuId"
        :menu="child"
      />
    </el-sub-menu>
  </template>

  <template v-else-if="menu.type === 1">
    <el-menu-item :index="getRoutePath(menu.url)">
      <el-icon v-if="menu.icon"><component :is="getIconComponent(menu.icon)" /></el-icon>
      <template #title>
        <span>{{ menu.name }}</span>
      </template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import * as Icons from '@element-plus/icons-vue'


defineProps<{
  menu: {
    menuId: number
    parentId: number
    name: string
    url: string | null
    icon: string | null
    type: number
    list: any[] | null
  }
}>()

function getRoutePath(url: string | null): string {
  if (!url) return ''
  // 将 sys/user.html 转换成 /sys/user
  let path = url.replace('.html', '')
  if (!path.startsWith('/')) {
    path = '/' + path
  }
  return path
}

function getIconComponent(iconStr: string | null) {
  if (!iconStr) return null
  // 转换 fa fa-user -> User
  let name = iconStr.replace('fa fa-', '')
  name = name.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join('')
  
  if (name === 'Cog' || name === 'Gear') name = 'Setting'
  if (name === 'UserSecret') name = 'User'
  if (name === 'ThList') name = 'List'
  if (name === 'Bug') name = 'Warning'
  if (name === 'Tasks') name = 'Checked'
  if (name === 'SunO') name = 'Sunny'
  
  return (Icons as any)[name] || Icons.Menu
}
</script>

<template>
  <template v-if="menu.type === 0 || (menu.list && menu.list.length > 0)">
    <el-sub-menu :index="String(menu.id)">
      <template #title>
        <el-icon>
          <component v-if="getIconComponent(menu.icon, menu.name)" :is="getIconComponent(menu.icon, menu.name)" />
        </el-icon>
        <span>{{ menu.name }}</span>
      </template>
      <sidebar-item
        v-for="child in menu.list"
        :key="child.id"
        :menu="child"
      />
    </el-sub-menu>
  </template>

  <template v-else-if="menu.type === 1">
    <el-menu-item :index="getRoutePath(menu.url)">
      <el-icon>
        <component v-if="getIconComponent(menu.icon, menu.name)" :is="getIconComponent(menu.icon, menu.name)" />
      </el-icon>
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
    id: number
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

function getIconComponent(iconStr: string | null, menuName: string = '') {
  // 1. Keyword-based Smart Fallback
  if (menuName) {
    if (menuName.includes('充值')) return Icons.Money
    if (menuName.includes('账单') || menuName.includes('财务') || menuName.includes('扣费')) return Icons.Wallet
    if (menuName.includes('通道')) return Icons.Connection
    if (menuName.includes('路由')) return Icons.Guide
    if (menuName.includes('客户')) return Icons.User
    if (menuName.includes('员工') || menuName.includes('用户')) return Icons.Avatar
    if (menuName.includes('配置')) return Icons.Setting
    if (menuName.includes('系统') || menuName.includes('设置')) return Icons.Operation
    if (menuName.includes('报表') || menuName.includes('分析')) return Icons.PieChart
    if (menuName.includes('统计')) return Icons.DataAnalysis
    if (menuName.includes('日志')) return Icons.Document
    if (menuName.includes('记录') || menuName.includes('明细')) return Icons.Tickets
    if (menuName.includes('角色') || menuName.includes('权限')) return Icons.Key
    if (menuName.includes('字典')) return Icons.Collection
    if (menuName.includes('任务') || menuName.includes('定时') || menuName.includes('调度')) return Icons.Clock
    if (menuName.includes('消息') || menuName.includes('通知') || menuName.includes('模板')) return Icons.Bell
    if (menuName.includes('主页') || menuName.includes('首页')) return Icons.House
  }

  // 2. Exact mapping based on DB icon string
  if (iconStr) {
    let name = iconStr.replace('fa fa-', '')
    name = name.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join('')
    
    const map: Record<string, string> = {
      'Cog': 'Setting', 'Gear': 'Setting', 'Cogs': 'Setting',
      'UserSecret': 'User', 'Users': 'User',
      'ThList': 'List', 'ListUl': 'List',
      'Bug': 'Warning', 'Tasks': 'Checked', 'SunO': 'Sunny',
      'Home': 'House', 'Dashboard': 'Odometer',
      'BarChart': 'DataAnalysis', 'AreaChart': 'DataAnalysis', 'LineChart': 'DataAnalysis', 'PieChart': 'PieChart',
      'FileTextO': 'Document', 'FileText': 'Document', 'File': 'Document',
      'Envelope': 'Message', 'Vcard': 'Postcard', 'IdCard': 'Postcard',
      'Sitemap': 'Connection', 'Desktop': 'Monitor', 'Wrench': 'Tools',
      'Key': 'Key', 'Lock': 'Lock', 'Unlock': 'Unlock', 'Cloud': 'Cloudy',
      'Bell': 'Bell', 'Money': 'Money', 'Jpy': 'Coin', 'Cny': 'Coin', 'Rmb': 'Coin',
      'CreditCard': 'CreditCard', 'CreditCardAlt': 'Bankcard', 'Bank': 'House',
      'Exchange': 'Switch', 'Random': 'Switch', 'ArrowsH': 'Switch',
      'Truck': 'Van', 'Rss': 'Connection', 'ShareAlt': 'Share', 'Link': 'Link',
      'Plug': 'Connection', 'Ticket': 'Ticket', 'ShoppingCart': 'ShoppingCart',
      'Paypal': 'Money', 'Diamond': 'Trophy', 'Gift': 'Present',
      'Send': 'Position', 'PaperPlane': 'Position', 'Wifi': 'Connection', 'Signal': 'Connection'
    }
    name = map[name] || name
    if ((Icons as any)[name]) {
      return (Icons as any)[name]
    }
  }

  // 3. Hash-based Diverse Fallback for anything unmatched
  const fallbackIcons = [
    Icons.Grid, Icons.Box, Icons.Star, Icons.Opportunity, Icons.Medal, 
    Icons.Flag, Icons.Lightning, Icons.Compass, Icons.Trophy, Icons.Location,
    Icons.Position, Icons.Collection, Icons.MagicStick, Icons.Reading, Icons.Suitcase,
    Icons.Picture, Icons.Mouse, Icons.Brush, Icons.Paperclip, Icons.Link
  ]
  const seedString = menuName || iconStr || 'default'
  let hash = 0
  for (let i = 0; i < seedString.length; i++) {
    hash = seedString.charCodeAt(i) + ((hash << 5) - hash)
  }
  const index = Math.abs(hash) % fallbackIcons.length
  return fallbackIcons[index]
}
</script>

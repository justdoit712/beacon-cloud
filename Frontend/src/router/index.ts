import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/userStore'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 动态载入 views 目录下的组件
const modules = import.meta.glob('../views/**/*.vue')

// 递归解析后端菜单树并拍平成一维路由，放入 Layout 子路由下
function generateRoutes(menuList: any[]): RouteRecordRaw[] {
  const tempRoutes: RouteRecordRaw[] = []

  function traverse(list: any[]) {
    for (const item of list) {
      if (item.type === 1 && item.url) {
        let path = item.url.replace('.html', '')
        if (!path.startsWith('/')) {
          path = '/' + path
        }

        // 尝试匹配组件路径，如 ../views/sys/user.vue
        const componentKey = `../views/${item.url.replace('.html', '.vue')}`
        
        // 兼容驼峰写法或首字母大写，如 ../views/sys/User.vue
        const parts = item.url.replace('.html', '').split('/')
        if (parts.length > 1) {
          parts[parts.length - 1] = parts[parts.length - 1].charAt(0).toUpperCase() + parts[parts.length - 1].slice(1)
        }
        const componentKeyCamel = `../views/${parts.join('/')}.vue`

        const component = modules[componentKey] || modules[componentKeyCamel] || (() => import('@/views/sys/main.vue'))

        tempRoutes.push({
          path,
          name: path.replace(/\//g, '_').substring(1),
          component,
          meta: {
            title: item.name,
            icon: item.icon
          }
        })
      }
      if (item.list && item.list.length > 0) {
        traverse(item.list)
      }
    }
  }

  traverse(menuList)
  return tempRoutes
}

let isRoutesLoaded = false

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.token

  if (to.path === '/login') {
    if (token) {
      next('/')
    } else {
      next()
    }
  } else {
    if (!token) {
      next('/login')
    } else {
      // 已经登录，检查是否已加载用户信息和动态路由
      if (!isRoutesLoaded || !userStore.userInfo) {
        try {
          await userStore.getUserInfo()
          const menuList = await userStore.getMenuList()
          
          const dynamicRoutes = generateRoutes(menuList || [])

          
          // 创建 Layout 根路由
          const layoutRoute: RouteRecordRaw = {
            path: '/',
            component: () => import('@/layout/index.vue'),
            children: [
              {
                path: '',
                name: 'Dashboard',
                component: () => import('@/views/sys/main.vue'),
                meta: { title: '首页' }
              },
              ...dynamicRoutes
            ]
          }
          
          router.addRoute(layoutRoute)
          isRoutesLoaded = true
          
          // 404 fallback 路由
          router.addRoute({
            path: '/:pathMatch(.*)*',
            redirect: '/'
          })

          next({ ...to, replace: true })
        } catch (error) {
          userStore.clearToken()
          isRoutesLoaded = false
          next('/login')
        }
      } else {
        next()
      }
    }
  }
})

export default router

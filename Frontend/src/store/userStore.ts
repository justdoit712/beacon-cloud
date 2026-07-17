import { defineStore } from 'pinia'
import request from '@/utils/request'

interface UserState {
  token: string
  userInfo: any
  roles: string[]
  menuList: any[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem('Auth-Token') || '',
    userInfo: null,
    roles: [],
    menuList: []
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('Auth-Token', token)
    },
    clearToken() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.menuList = []
      localStorage.removeItem('Auth-Token')
    },
    async login(loginForm: any) {
      try {
        const res: any = await request.post('/sys/login', loginForm)
        if (res && res.code === 0) {
          this.setToken(res.data.token)
          return res
        } else {
          return Promise.reject(new Error(res?.msg || '登录失败'))
        }
      } catch (error) {
        return Promise.reject(error)
      }
    },
    async getUserInfo() {
      try {
        const res: any = await request.get('/sys/user/info')
        if (res && res.code === 0) {
          this.userInfo = res.data
          // 如果有 roles 等字段也可以赋值
          this.roles = res.data.roles || ['admin']
          return res.data
        }
      } catch (error) {
        return Promise.reject(error)
      }
    },
    async getMenuList() {
      try {
        const res: any = await request.get('/sys/menu/user')
        if (res && res.code === 0) {
          this.menuList = res.data || []
          return this.menuList
        }
      } catch (error) {
        return Promise.reject(error)
      }
    },
    async logout() {
      this.clearToken()
    }
  }
})


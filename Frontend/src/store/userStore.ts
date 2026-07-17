import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('Auth-Token') || '',
    userInfo: null,
    roles: []
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
      localStorage.removeItem('Auth-Token')
    }
  }
})

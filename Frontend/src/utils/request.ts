import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '',
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 从 localStorage 中获取 Token
    const token = localStorage.getItem('Auth-Token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    // 剥离外层包装，直接返回业务数据
    const res = response.data
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('Auth-Token')
      ElMessage.error('登录失效，请重新登录')
      window.location.href = '/login'
    } else {
      ElMessage.error(error.message || '系统异常')
    }
    return Promise.reject(error)
  }
)

export default request

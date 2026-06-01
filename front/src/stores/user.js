import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, setToken } from '@/api'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(
    localStorage.getItem('user')
      ? JSON.parse(localStorage.getItem('user'))
      : null
  )

  // 计算属性
  function isTokenExpired(tokenStr) {
    if (!tokenStr) return true
    try {
      const parts = tokenStr.split('.')
      // 不是标准 JWT（三段）时无法判断 exp，假定不过期
      if (parts.length !== 3) return false
      // Restore base64 url -> base64
      const payloadBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
      // atob 可能产生 utf-8 问题，使用 decodeURIComponent 保证解析正确
      const jsonPayload = decodeURIComponent(
        atob(payloadBase64)
          .split('')
          .map((c) => {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
          })
          .join('')
      )
      const payload = JSON.parse(jsonPayload)
      if (!payload.exp) return false
      return Date.now() / 1000 >= payload.exp
    } catch (e) {
      console.error('解析 token 失败:', e)
      // 无法解析时当作过期处理以保证安全
      return true
    }
  }

  const isLoggedIn = computed(() => !!token.value && !!userInfo.value && !isTokenExpired(token.value))
  const userId = computed(() => userInfo.value?.userId || null)
  const email = computed(() => userInfo.value?.email || '')
  const username = computed(() => userInfo.value?.username || '')

  // 初始化：从 localStorage 恢复状态
  function init() {
    const savedToken = localStorage.getItem('token')
    const savedUser = localStorage.getItem('user')

    // 只有当 token 存在且未过期时才恢复 token，否则清理本地存储
    if (savedToken && !isTokenExpired(savedToken)) {
      token.value = savedToken
      setToken(savedToken)
    } else {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      return
    }

    if (savedUser) {
      try {
        userInfo.value = JSON.parse(savedUser)
      } catch (e) {
        console.error('解析用户信息失败:', e)
        userInfo.value = null
        localStorage.removeItem('user')
      }
    }
  }

  // 登录
  async function login(email, code) {
    try {
      const result = await api.loginOrRegister(email, code)

      if (result.status && result.data) {
        // 保存 token
        token.value = result.data.token
        setToken(result.data.token)

        // 解析并保存用户信息
        if (result.data.userInfo) {
          try {
            const parsedUserInfo = JSON.parse(result.data.userInfo)
            userInfo.value = parsedUserInfo
            localStorage.setItem('user', JSON.stringify(parsedUserInfo))
          } catch (e) {
            console.error('解析用户信息失败:', e)
            throw new Error('用户信息格式错误')
          }
        }

        return { success: true }
      } else {
        return { success: false, message: result.message || '登录失败' }
      }
    } catch (error) {
      return { success: false, message: error.message || '登录失败，请稍后重试' }
    }
  }

  // 发送验证码
  async function sendCode(email) {
    try {
      const result = await api.sendCode(email)
      return { success: result.status, message: result.message }
    } catch (error) {
      return { success: false, message: error.message || '发送验证码失败' }
    }
  }

  // 登出
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  // 初始化
  init()

  return {
    // 状态
    token,
    userInfo,
    // 计算属性
    isLoggedIn,
    userId,
    email,
    username,
    // 方法
    login,
    sendCode,
    logout,
    init,
  }
})


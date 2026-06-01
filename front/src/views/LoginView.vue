<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import Navbar from '@/components/Navbar.vue'

const router = useRouter()
const userStore = useUserStore()

const email = ref('')
const code = ref('')
const countdown = ref(0)
const isSendingCode = ref(false) // 发送验证码的加载状态
const isLoggingIn = ref(false) // 登录的加载状态
const errorMessage = ref('')

// 验证邮箱格式
const validateEmail = (email) => {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return re.test(email)
}

// 发送验证码
const sendCode = async () => {
  if (!email.value) {
    errorMessage.value = '请输入邮箱地址'
    return
  }

  if (!validateEmail(email.value)) {
    errorMessage.value = '请输入有效的邮箱地址'
    return
  }

  try {
    isSendingCode.value = true
    errorMessage.value = ''
    
    // 使用 store 发送验证码
    const result = await userStore.sendCode(email.value)
    
    if (!result.success) {
      errorMessage.value = result.message || '发送验证码失败'
      return
    }
    
    // 开始倒计时
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
    
    // 提示成功
    console.log('验证码已发送到:', email.value)
  } catch (error) {
    errorMessage.value = error.message || '发送验证码失败，请稍后重试'
  } finally {
    isSendingCode.value = false
  }
}

// 登录/注册
const handleLogin = async () => {
  if (!email.value) {
    errorMessage.value = '请输入邮箱地址'
    return
  }

  if (!validateEmail(email.value)) {
    errorMessage.value = '请输入有效的邮箱地址'
    return
  }

  if (!code.value) {
    errorMessage.value = '请输入验证码'
    return
  }

  if (code.value.length !== 6) {
    errorMessage.value = '验证码应为6位数字'
    return
  }

  try {
    isLoggingIn.value = true
    errorMessage.value = ''
    
    // 使用 store 登录
    const result = await userStore.login(email.value, code.value)
    
    if (!result.success) {
      errorMessage.value = result.message || '登录失败，请检查验证码是否正确'
      return
    }
    
    // 登录成功，跳转到首页
    router.push('/')
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请检查验证码是否正确'
  } finally {
    isLoggingIn.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <Navbar />

    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo">
            <div class="logo-icon">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                <path
                  d="M20.59 12l-8.3-8.29a1 1 0 0 0-1.42 1.42L18.17 12l-7.29 7.29a1 1 0 0 0 1.42 1.42l8.29-8.29zM7.7 12l8.29-8.29a1 1 0 1 0-1.42-1.42L5.83 12l8.74 8.71a1 1 0 0 0 1.42-1.42z"
                />
              </svg>
            </div>
          </div>
          <h1>欢迎来到 CodeMaster</h1>
          <p>使用邮箱验证码登录，首次登录将自动注册</p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <div v-if="errorMessage" class="error-message">
            <i class="fas fa-exclamation-circle"></i>
            {{ errorMessage }}
          </div>

          <div class="form-group">
            <label for="email">邮箱地址</label>
            <div class="input-wrapper">
              <i class="fas fa-envelope input-icon"></i>
              <input
                id="email"
                v-model="email"
                type="email"
                placeholder="请输入您的邮箱地址"
                class="form-input"
                :disabled="isSendingCode || isLoggingIn"
                @input="errorMessage = ''"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="code">验证码</label>
            <div class="code-input-wrapper">
              <div class="input-wrapper">
                <i class="fas fa-key input-icon"></i>
                <input
                  id="code"
                  v-model="code"
                  type="text"
                  placeholder="请输入6位验证码"
                  class="form-input"
                  maxlength="6"
                  :disabled="isSendingCode || isLoggingIn"
                  @input="errorMessage = ''"
                />
              </div>
              <button
                type="button"
                class="code-btn"
                :class="{ disabled: countdown > 0 || isSendingCode || isLoggingIn }"
                :disabled="countdown > 0 || isSendingCode || isLoggingIn"
                @click="sendCode"
              >
                <span v-if="isSendingCode">
                  <i class="fas fa-spinner fa-spin"></i>
                  发送中...
                </span>
                <span v-else-if="countdown > 0">{{ countdown }}s后重发</span>
                <span v-else>发送验证码</span>
              </button>
            </div>
          </div>

          <button type="submit" class="login-btn" :disabled="isLoggingIn || isSendingCode">
            <span v-if="isLoggingIn">
              <i class="fas fa-spinner fa-spin"></i>
              登录中...
            </span>
            <span v-else>登录 / 注册</span>
          </button>
        </form>

        <div class="login-footer">
          <p>登录即表示您同意我们的</p>
          <a href="#" class="link">服务条款</a>
          <span>和</span>
          <a href="#" class="link">隐私政策</a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--bg-light);
}

.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 80px);
  padding: 2rem;
}

.login-card {
  background: var(--white);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  border: 1px solid var(--border-color);
  width: 100%;
  max-width: 480px;
  padding: 3rem;
}

.login-header {
  text-align: center;
  margin-bottom: 2.5rem;
}

.login-logo {
  margin-bottom: 1.5rem;
}

.login-logo .logo-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 50%;
  background: var(--primary-light);
}

.login-logo .logo-icon svg {
  width: 32px;
  height: 32px;
  fill: var(--primary-color);
}

.login-header h1 {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-dark);
  margin-bottom: 0.8rem;
}

.login-header p {
  color: var(--text-light);
  font-size: 0.95rem;
}

.login-form {
  margin-bottom: 2rem;
}

.error-message {
  background: rgba(215, 38, 61, 0.1);
  color: #d7263d;
  padding: 0.8rem 1rem;
  border-radius: 8px;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-dark);
  margin-bottom: 0.6rem;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 1rem;
  color: var(--text-light);
  font-size: 0.9rem;
  z-index: 1;
}

.form-input {
  width: 100%;
  padding: 0.9rem 1rem 0.9rem 2.8rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-size: 0.95rem;
  color: var(--text-dark);
  background: var(--white);
  transition: var(--transition);
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.1);
}

.form-input:disabled {
  background: var(--bg-light);
  cursor: not-allowed;
}

.form-input::placeholder {
  color: var(--text-light);
}

.code-input-wrapper {
  display: flex;
  gap: 0.8rem;
}

.code-input-wrapper .input-wrapper {
  flex: 1;
}

.code-btn {
  padding: 0.9rem 1.5rem;
  border: 1px solid var(--primary-color);
  border-radius: var(--radius);
  background: var(--white);
  color: var(--primary-color);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
  white-space: nowrap;
  flex-shrink: 0;
}

.code-btn:hover:not(.disabled) {
  background: var(--primary-color);
  color: white;
}

.code-btn.disabled {
  background: var(--bg-light);
  border-color: var(--border-color);
  color: var(--text-light);
  cursor: not-allowed;
}

.login-btn {
  width: 100%;
  padding: 1rem;
  border: none;
  border-radius: var(--radius);
  background: var(--primary-color);
  color: white;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 2rem;
}

.login-btn:hover:not(:disabled) {
  background: #3857d8;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(67, 97, 238, 0.3);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.login-footer {
  text-align: center;
  color: var(--text-light);
  font-size: 0.85rem;
  line-height: 1.8;
}

.login-footer .link {
  color: var(--primary-color);
  text-decoration: none;
  transition: var(--transition);
}

.login-footer .link:hover {
  text-decoration: underline;
}

@media (max-width: 576px) {
  .login-container {
    padding: 1rem;
  }

  .login-card {
    padding: 2rem 1.5rem;
  }

  .code-input-wrapper {
    flex-direction: column;
  }

  .code-btn {
    width: 100%;
  }
}
</style>


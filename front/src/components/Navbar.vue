<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isMenuOpen = ref(false)
const isUserMenuOpen = ref(false)

const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value
}

const closeMenu = () => {
  isMenuOpen.value = false
}

const toggleUserMenu = () => {
  isUserMenuOpen.value = !isUserMenuOpen.value
}

const closeUserMenu = () => {
  isUserMenuOpen.value = false
}

// 获取用户头像文字（用户名首字母或邮箱首字母）
const avatarText = computed(() => {
  if (userStore.username) {
    return userStore.username.charAt(0).toUpperCase()
  }
  if (userStore.email) {
    return userStore.email.charAt(0).toUpperCase()
  }
  return 'U'
})

// 登出
const handleLogout = () => {
  userStore.logout()
  closeUserMenu()
  router.push('/')
}

// 点击外部关闭用户菜单
const handleClickOutside = (event) => {
  if (isUserMenuOpen.value && !event.target.closest('.user-menu-wrapper')) {
    closeUserMenu()
  }
}

// 添加全局点击监听
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <nav class="navbar">
    <div class="logo">
      <div class="logo-icon">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
          <path
            d="M20.59 12l-8.3-8.29a1 1 0 0 0-1.42 1.42L18.17 12l-7.29 7.29a1 1 0 0 0 1.42 1.42l8.29-8.29zM7.7 12l8.29-8.29a1 1 0 1 0-1.42-1.42L5.83 12l8.74 8.71a1 1 0 0 0 1.42-1.42z"
          />
        </svg>
      </div>
      CodeMaster
    </div>

    <div class="nav-links" :class="{ active: isMenuOpen }">
      <RouterLink to="/" class="nav-link" @click="closeMenu">首页</RouterLink>
      <RouterLink to="/question-bank" class="nav-link" @click="closeMenu">智能题库</RouterLink>
      <RouterLink to="/my-questions" class="nav-link" @click="closeMenu">我的题目</RouterLink>
      <RouterLink to="/history" class="nav-link" @click="closeMenu">历史记录</RouterLink>
    </div>

    <div class="nav-btns">
      <!-- 未登录：显示登录按钮 -->
      <RouterLink v-if="!userStore.isLoggedIn" to="/login" class="btn btn-login">登录</RouterLink>
      
      <!-- 已登录：显示用户头像 -->
      <div v-else class="user-menu-wrapper" @click.stop>
        <button class="user-avatar-btn" @click="toggleUserMenu">
          <div class="user-avatar">
            {{ avatarText }}
          </div>
        </button>
        
        <!-- 用户下拉菜单 -->
        <div v-if="isUserMenuOpen" class="user-menu" @click.stop>
          <div class="user-menu-header">
            <div class="user-menu-avatar">
              {{ avatarText }}
            </div>
            <div class="user-menu-info">
              <div class="user-menu-name">{{ userStore.username || '用户' }}</div>
              <div class="user-menu-email">{{ userStore.email }}</div>
            </div>
          </div>
          <div class="user-menu-divider"></div>
          <button class="user-menu-item" @click="handleLogout">
            <i class="fas fa-sign-out-alt"></i>
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </div>

    <button class="menu-toggle" id="menuToggle" type="button" @click="toggleMenu">
      <i class="fas" :class="isMenuOpen ? 'fa-times' : 'fa-bars'"></i>
    </button>
  </nav>
</template>

<style scoped>
.user-menu-wrapper {
  position: relative;
}

.user-avatar-btn {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 1rem;
  transition: var(--transition);
  border: 2px solid var(--primary-light);
}

.user-avatar-btn:hover .user-avatar {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(67, 97, 238, 0.3);
}

.user-menu {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  background: var(--white);
  border-radius: var(--radius);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--border-color);
  min-width: 240px;
  z-index: 1000;
  overflow: hidden;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.user-menu-header {
  padding: 1rem 1.25rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: var(--bg-light);
}

.user-menu-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 1.1rem;
  flex-shrink: 0;
}

.user-menu-info {
  flex: 1;
  min-width: 0;
}

.user-menu-name {
  font-weight: 600;
  color: var(--text-dark);
  font-size: 0.95rem;
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-email {
  font-size: 0.85rem;
  color: var(--text-light);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-divider {
  height: 1px;
  background: var(--border-color);
  margin: 0.5rem 0;
}

.user-menu-item {
  width: 100%;
  padding: 0.75rem 1.25rem;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: var(--text-dark);
  font-size: 0.9rem;
  transition: var(--transition);
}

.user-menu-item:hover {
  background: var(--bg-light);
  color: var(--primary-color);
}

.user-menu-item i {
  width: 16px;
  text-align: center;
}

/* 点击外部关闭菜单 */
@media (max-width: 768px) {
  .user-menu {
    right: 1rem;
    min-width: 200px;
  }
}
</style>


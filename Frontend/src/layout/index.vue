<template>
  <div class="app-wrapper" :class="{ sidebarCollapsed: isCollapsed }">
    <!-- Sidebar -->
    <div class="sidebar-container">
      <div class="logo-area">
        <h1 v-if="!isCollapsed">Beacon Cloud</h1>
        <h1 v-else>BC</h1>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="route.path"
          :collapse="isCollapsed"
          background-color="#1f2d3d"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          unique-opened
          router
        >
          <sidebar-item
            v-for="menu in menuList"
            :key="menu.menuId"
            :menu="menu"
          />
        </el-menu>
      </el-scrollbar>
    </div>

    <!-- Main Container -->
    <div class="main-container">
      <!-- Header -->
      <div class="header-navbar">
        <div class="left-panel">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <component :is="isCollapsed ? Expand : Fold" />
          </el-icon>
          <el-breadcrumb class="breadcrumb-container" separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="right-panel">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-profile">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">{{ username }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- App Main Content -->
      <div class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/userStore'
import SidebarItem from './components/SidebarItem.vue'
import { Fold, Expand, CaretBottom } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)
const username = computed(() => userStore.userInfo?.username || '管理员')
const menuList = computed(() => userStore.menuList)

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (command === 'password') {
    // 预留修改密码弹窗
  }
}
</script>

<style scoped>
.app-wrapper {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.sidebar-container {
  width: 240px;
  background-color: #1f2d3d;
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;
}

.sidebarCollapsed .sidebar-container {
  width: 64px;
}

.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1a252f;
  color: #fff;
  border-bottom: 1px solid #111a24;
}

.logo-area h1 {
  margin: 0;
  font-size: 20px;
  font-weight: bold;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: #f0f2f5;
}

.header-navbar {
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.left-panel {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  margin-right: 16px;
}

.right-panel {
  display: flex;
  align-items: center;
}

.user-profile {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  margin: 0 8px;
  font-size: 14px;
  color: #303133;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  box-sizing: border-box;
}

/* fade-transform transition */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>

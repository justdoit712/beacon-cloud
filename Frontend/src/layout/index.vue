<template>
  <div class="app-wrapper flex h-screen w-screen overflow-hidden bg-[var(--app-bg)] text-[var(--text-primary)]" :class="{ sidebarCollapsed: isCollapsed }">
    <!-- Sidebar -->
    <div class="sidebar-container bg-[var(--panel-bg)] border-r border-[var(--border-light)] flex flex-col h-full shrink-0 transition-all duration-300 z-10" :class="isCollapsed ? 'w-[64px]' : 'w-[220px]'">
      <div class="logo-area h-[56px] flex items-center justify-center border-b border-[var(--border-light)] font-bold tracking-wider text-[var(--brand-primary)]">
        <h1 v-if="!isCollapsed" class="text-xl">Beacon Cloud</h1>
        <h1 v-else class="text-xl">BC</h1>
      </div>
      <el-scrollbar class="flex-1">
        <el-menu
          :default-active="route.path"
          :collapse="isCollapsed"
          background-color="transparent"
          text-color="var(--text-regular)"
          active-text-color="var(--brand-primary)"
          class="border-r-0"
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
    <div class="main-container flex-1 flex flex-col h-full overflow-hidden relative z-0">
      <!-- Header -->
      <div class="header-navbar h-[56px] bg-[var(--panel-bg)] border-b border-[var(--border-light)] px-6 flex items-center justify-between shrink-0 z-10">
        <div class="left-panel flex items-center">
          <el-icon class="collapse-btn text-xl cursor-pointer mr-4 text-[var(--text-regular)] hover:text-[var(--brand-primary)] transition-colors" @click="toggleSidebar">
            <component :is="isCollapsed ? Expand : Fold" />
          </el-icon>
          <el-breadcrumb class="breadcrumb-container" separator="/">
            <el-breadcrumb-item :to="{ path: '/' }"><span class="text-[var(--text-regular)]">首页</span></el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title"><span class="text-[var(--text-primary)]">{{ route.meta.title }}</span></el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="right-panel flex items-center">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-profile flex items-center cursor-pointer hover:bg-[var(--app-bg)] px-3 py-1.5 rounded-lg transition-colors">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" class="border border-[var(--border-light)]" />
              <span class="username mx-2 text-sm text-[var(--text-regular)]">{{ username }}</span>
              <el-icon class="text-[var(--text-regular)]"><CaretBottom /></el-icon>
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
      <div class="app-main flex-1 p-6 overflow-y-auto box-border">
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
/* fade-transform transition */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px) scale(0.98);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px) scale(0.98);
}

/* 覆盖 Element Plus 样式 */
:deep(.el-breadcrumb__inner) {
  color: inherit !important;
}
:deep(.el-menu) {
  border-right: none !important;
  background-color: transparent !important;
}
:deep(.el-sub-menu__title:hover),
:deep(.el-menu-item:hover) {
  background-color: var(--brand-primary-light) !important;
}
</style>

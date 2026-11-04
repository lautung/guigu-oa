<template>
  <el-popover v-model:visible="open" placement="bottom-end" :width="240" trigger="click">
    <template #reference>
      <button type="button" class="user-trigger" aria-label="用户菜单" aria-haspopup="menu">
        <img :src="avatarSrc" class="avatar" alt="" />
        <span v-if="userStore.name" class="username">{{ userStore.name }}</span>
        <el-icon class="user-trigger-arrow"><component :is="shellIcon('ArrowDown')" /></el-icon>
      </button>
    </template>

    <div class="user-menu-panel" role="menu">
      <button type="button" class="user-menu-profile" role="menuitem" @click="openProfile">
        <el-icon><component :is="shellIcon('User')" /></el-icon>
        <span>个人中心</span>
      </button>

      <div class="appearance">
        <span class="appearance-label">主题</span>
        <div class="appearance-options">
          <button
            v-for="option in THEME_OPTIONS"
            :key="option.value"
            type="button"
            class="appearance-option"
            :class="{ active: shell.theme === option.value }"
            :data-theme="option.value"
            @click="shell.setTheme(option.value)"
          >
            {{ option.label }}
          </button>
        </div>

        <span class="appearance-label">密度</span>
        <div class="appearance-options">
          <button
            v-for="option in DENSITY_OPTIONS"
            :key="option.value"
            type="button"
            class="appearance-option"
            :class="{ active: shell.density === option.value }"
            :data-density="option.value"
            @click="shell.setDensity(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <button type="button" class="lock-btn" role="menuitem" @click="handleLock">
        <el-icon><component :is="shellIcon('Lock')" /></el-icon>
        <span>锁屏</span>
      </button>

      <button type="button" class="logout-btn" role="menuitem" @click="handleLogout">
        退出登录
      </button>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PATHS } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import { useLockScreenStore } from './lock-screen'
import { useShellStore } from './shell-state'
import type { ShellDensity, ShellTheme } from './shell-preferences'
import { shellIcon } from './shell-icons'

const DEFAULT_AVATAR = '/avatar.svg'

const THEME_OPTIONS: readonly { value: ShellTheme; label: string }[] = [
  { value: 'light', label: '亮色' },
  { value: 'dark', label: '暗色' },
]

const DENSITY_OPTIONS: readonly { value: ShellDensity; label: string }[] = [
  { value: 'small', label: '紧凑' },
  { value: 'default', label: '默认' },
  { value: 'large', label: '宽松' },
]

const router = useRouter()
const userStore = useUserStore()
const shell = useShellStore()
const lockScreen = useLockScreenStore()
const open = ref(false)
const avatarSrc = computed(() => userStore.avatar || DEFAULT_AVATAR)

function openProfile(): void {
  open.value = false
  void router.push(PATHS.WORK_PROFILE)
}

function handleLock(): void {
  open.value = false
  lockScreen.lock()
}

async function handleLogout(): Promise<void> {
  open.value = false
  await userStore.logout()
}
</script>

<style lang="scss" scoped>
.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.user-trigger:hover,
.user-trigger:focus-visible {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  outline: none;
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  font-size: 13px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-trigger-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.user-menu-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-menu-profile,
.lock-btn,
.logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.user-menu-profile:hover,
.user-menu-profile:focus-visible,
.lock-btn:hover,
.lock-btn:focus-visible,
.logout-btn:hover,
.logout-btn:focus-visible {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  outline: none;
}

.lock-btn {
  border-top: 1px solid var(--el-border-color-lighter);
}

.logout-btn {
  border-top: 1px solid var(--el-border-color-lighter);
  border-radius: 0 0 6px 6px;
  color: var(--el-color-danger);
}

.appearance {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 6px 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.appearance-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.appearance-options {
  display: flex;
  gap: 4px;
}

.appearance-option {
  flex: 1;
  padding: 4px 0;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
}

.appearance-option:hover,
.appearance-option:focus-visible {
  border-color: var(--el-color-primary);
  outline: none;
}

.appearance-option.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

@media (max-width: 899px) {
  .username {
    max-width: 72px;
  }

  .user-trigger-arrow {
    display: none;
  }
}
</style>

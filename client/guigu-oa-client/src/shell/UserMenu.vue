<template>
  <el-popover v-model:visible="open" placement="bottom-end" :width="240" trigger="click">
    <template #reference>
      <button
        type="button"
        class="user-trigger"
        :aria-label="t('shell.userMenu.aria')"
        aria-haspopup="menu"
      >
        <img :src="avatarSrc" class="avatar" alt="" />
        <span v-if="userStore.name" class="username">{{ userStore.name }}</span>
        <el-icon class="user-trigger-arrow"><component :is="shellIcon('ArrowDown')" /></el-icon>
      </button>
    </template>

    <div class="user-menu-panel" role="menu">
      <button type="button" class="user-menu-profile" role="menuitem" @click="openProfile">
        <el-icon><component :is="shellIcon('User')" /></el-icon>
        <span>{{ t('shell.userMenu.profile') }}</span>
      </button>

      <div class="appearance">
        <span class="appearance-label">{{ t('shell.userMenu.theme') }}</span>
        <div class="appearance-options">
          <button
            v-for="option in THEME_OPTIONS"
            :key="option"
            type="button"
            class="appearance-option"
            :class="{ active: shell.theme === option }"
            :data-theme="option"
            @click="shell.setTheme(option)"
          >
            {{ t(`shell.userMenu.themes.${option}`) }}
          </button>
        </div>

        <span class="appearance-label">{{ t('shell.userMenu.density') }}</span>
        <div class="appearance-options">
          <button
            v-for="option in DENSITY_OPTIONS"
            :key="option"
            type="button"
            class="appearance-option"
            :class="{ active: shell.density === option }"
            :data-density="option"
            @click="shell.setDensity(option)"
          >
            {{ t(`shell.userMenu.densities.${option}`) }}
          </button>
        </div>
      </div>

      <button type="button" class="lock-btn" role="menuitem" @click="handleLock">
        <el-icon><component :is="shellIcon('Lock')" /></el-icon>
        <span>{{ t('shell.userMenu.lock') }}</span>
      </button>

      <button type="button" class="logout-btn" role="menuitem" @click="handleLogout">
        {{ t('shell.userMenu.logout') }}
      </button>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { translate as t } from '@/i18n'
import { requestBinary } from '@/request/request'
import { PATHS } from '@/router/paths'
import { avatarRequest } from '@/profile/config'
import { createAvatarLoader } from '@/profile/avatar'
import { useUserStore } from '@/store/modules/user'
import { useLockScreenStore } from './lock-screen'
import { useShellStore } from './shell-state'
import type { ShellDensity, ShellTheme } from './shell-preferences'
import { shellIcon } from './shell-icons'

const DEFAULT_AVATAR = '/avatar.svg'

const THEME_OPTIONS: readonly ShellTheme[] = ['light', 'dark']

const DENSITY_OPTIONS: readonly ShellDensity[] = ['small', 'default', 'large']

const router = useRouter()
const userStore = useUserStore()
const shell = useShellStore()
const lockScreen = useLockScreenStore()
const open = ref(false)
const avatarSrc = ref(DEFAULT_AVATAR)
// /info.avatar 是已认证同源端点，`<img>` 拿不到 token 头：带认证取回 blob 再显示（#254）。
const avatars = createAvatarLoader({
  load: (path) => requestBinary(avatarRequest(path), { notifyNormalErrors: false }),
  createObjectUrl: (blob) => URL.createObjectURL(blob),
  revokeObjectUrl: (url) => URL.revokeObjectURL(url),
})

watch(
  () => userStore.avatar,
  async (path) => {
    try {
      avatarSrc.value = (path ? await avatars.read(path) : '') || DEFAULT_AVATAR
    } catch {
      avatarSrc.value = DEFAULT_AVATAR
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => avatars.dispose())

function openProfile(): void {
  open.value = false
  void router.push(PATHS.PROFILE)
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

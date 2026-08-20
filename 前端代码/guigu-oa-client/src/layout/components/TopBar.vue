<template>
  <header class="top-bar">
    <div class="brand">云尚办公</div>
    <div class="user-area">
      <img :src="avatarSrc" class="avatar" alt="">
      <span class="username">{{ userStore.name }}</span>
      <el-button type="text" class="logout-btn" @click="handleLogout">退出</el-button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { ROUTES } from '@/navigation/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const DEFAULT_AVATAR = 'https://static.dingtalk.com/media/lALOnahFD80CgM0CgA_640_640.png_450x10000q90.jpg'

const avatarSrc = computed(() => userStore.avatar || DEFAULT_AVATAR)

async function handleLogout() {
  await userStore.logout()
  router.push(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(route.fullPath)}`)
}
</script>

<style lang="scss" scoped>
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  flex-shrink: 0;
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  font-size: 14px;
  color: #606266;
}

.logout-btn {
  padding: 0;
  margin-left: 4px;
}

@media (max-width: 768px) {
  .brand {
    font-size: 16px;
  }

  .username {
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

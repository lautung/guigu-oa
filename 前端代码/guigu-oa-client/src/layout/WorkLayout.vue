<template>
  <div class="work-layout">
    <header class="top-bar">
      <div class="brand">云尚办公</div>
      <div class="user-area">
        <img :src="avatarSrc" class="avatar" alt="">
        <span class="username">{{ name }}</span>
        <el-button type="text" class="logout-btn" @click="handleLogout">退出</el-button>
      </div>
    </header>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { ROUTES } from '@/navigation/access-policy'

const DEFAULT_AVATAR = 'https://static.dingtalk.com/media/lALOnahFD80CgM0CgA_640_640.png_450x10000q90.jpg'

export default {
  name: 'WorkLayout',
  computed: {
    ...mapGetters(['name', 'avatar']),
    avatarSrc() {
      return this.avatar || DEFAULT_AVATAR
    }
  },
  methods: {
    async handleLogout() {
      await this.$store.dispatch('user/logout')
      this.$router.push(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(this.$route.fullPath)}`)
    }
  }
}
</script>

<style lang="scss" scoped>
.work-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
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

.main-content {
  flex: 1;
  padding: 16px;
  max-width: 960px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
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

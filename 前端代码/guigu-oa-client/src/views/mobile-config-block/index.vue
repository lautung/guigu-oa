<template>
  <div class="mobile-config-block">
    <div class="message-card">
      <i class="el-icon-monitor icon" />
      <h1>{{ message }}</h1>
      <p>配置功能需要在电脑浏览器中操作。</p>
      <el-button type="primary" @click="goBack">返回办事</el-button>
    </div>
  </div>
</template>

<script>
import { DESKTOP_CONFIG_HINT, getDefaultPostLoginRoute } from '@/navigation/access-policy'

export default {
  name: 'MobileConfigBlock',
  data() {
    return {
      message: DESKTOP_CONFIG_HINT
    }
  },
  methods: {
    goBack() {
      this.$router.replace(getDefaultPostLoginRoute())
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-config-block {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f5f7fa;
  box-sizing: border-box;
}

.message-card {
  width: 100%;
  max-width: 360px;
  padding: 32px 24px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 16px;
}

h1 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}

p {
  margin: 0 0 24px;
  font-size: 14px;
  color: #909399;
}
</style>

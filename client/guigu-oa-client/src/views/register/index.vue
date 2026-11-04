<template>
  <div v-loading="register.state.loading" class="register-container">
    <div class="register-card">
      <div class="title-container">
        <h3 class="title">云尚办公</h3>
        <p class="subtitle">注册账号</p>
      </div>

      <div v-if="register.state.loaded && !register.state.enabled" class="register-closed">
        <el-alert
          title="注册未开放"
          type="warning"
          description="管理员尚未开放自助注册，请联系管理员开通账号。"
          :closable="false"
          show-icon
        />
        <router-link class="back-login" :to="PATHS.LOGIN">返回登录</router-link>
      </div>

      <template v-else-if="register.state.enabled">
        <el-form
          class="register-form"
          label-position="top"
          :model="register.state"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="用户名">
            <el-input
              v-model="register.state.username"
              maxlength="20"
              placeholder="用户名"
              tabindex="1"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="register.state.password"
              type="password"
              placeholder="至少 6 位密码"
              tabindex="2"
              show-password
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="register.state.confirm"
              type="password"
              placeholder="再次输入密码"
              tabindex="3"
              show-password
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input
              v-model="register.state.name"
              maxlength="20"
              placeholder="姓名"
              tabindex="4"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item label="手机（可选）">
            <el-input
              v-model="register.state.phone"
              maxlength="20"
              placeholder="手机号"
              tabindex="5"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
        </el-form>

        <p v-if="register.state.formError" class="error-row">{{ register.state.formError }}</p>

        <el-button
          :loading="register.state.submitting"
          type="primary"
          class="register-btn"
          @click="handleSubmit"
        >
          注册
        </el-button>
        <router-link class="back-login" :to="PATHS.LOGIN">已有账号？返回登录</router-link>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RegisterPage' })

import { onMounted } from 'vue'

import { createRegisterPage } from '@/auth/register-browser'
import { PATHS } from '@/router/paths'

const register = createRegisterPage()

onMounted(() => {
  register.loadSwitch()
})

async function handleSubmit() {
  await register.submit()
}
</script>

<style lang="scss" scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2d3a4b;
  padding: 16px;
}

.register-card {
  width: 100%;
  max-width: 400px;
  padding: 32px 24px;
  background: #fff;
  border-radius: 8px;
}

.title-container {
  margin-bottom: 24px;
  text-align: center;

  .title {
    margin: 0;
    font-size: 24px;
    color: #303133;
  }

  .subtitle {
    margin: 8px 0 0;
    color: #909399;
  }
}

.register-closed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.register-btn {
  width: 100%;
}

.error-row {
  margin: 0 0 12px;
  color: #f56c6c;
}

.back-login {
  display: block;
  margin-top: 16px;
  text-align: center;
  color: #409eff;
  text-decoration: none;
}
</style>

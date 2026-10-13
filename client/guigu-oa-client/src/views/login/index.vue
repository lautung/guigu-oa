<template>
  <div class="login-container">
    <el-form
      ref="loginFormRef"
      :model="loginForm"
      :rules="loginRules"
      class="login-form"
      label-position="left"
    >
      <div class="title-container">
        <h3 class="title">云尚办公</h3>
      </div>

      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          placeholder="用户名"
          tabindex="1"
          @keyup.enter="handleLogin"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="密码"
          tabindex="2"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>

      <el-button :loading="loading" type="primary" class="login-btn" @click="handleLogin">
        登录
      </el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'LoginPage' })

import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { buildLoginRedirectPath } from '@/navigation'
import { ElMessage, type FormInstance } from 'element-plus'
import { RecognitionFailure } from '@/recognition'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance | null>(null)
const loading = ref(false)
const redirect = ref<string | undefined>(undefined)

const loginForm = ref({
  username: '',
  password: '',
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur', min: 6 }],
}

watch(
  () => route.query,
  (query) => {
    const target = query?.redirect
    redirect.value = typeof target === 'string' ? target : undefined
  },
  { immediate: true },
)

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(loginForm.value)
      router.push({ path: buildLoginRedirectPath(redirect.value) })
    } catch (error) {
      if (!(error instanceof RecognitionFailure && error.kind === 'superseded')) {
        ElMessage.error(error instanceof Error ? error.message : '登录失败')
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2d3a4b;
  padding: 16px;
}

.login-form {
  width: 100%;
  max-width: 400px;
  padding: 32px 24px;
  background: #fff;
  border-radius: 8px;
}

.title-container {
  margin-bottom: 24px;

  .title {
    margin: 0;
    text-align: center;
    font-size: 24px;
    color: #303133;
  }
}

.login-btn {
  width: 100%;
}
</style>

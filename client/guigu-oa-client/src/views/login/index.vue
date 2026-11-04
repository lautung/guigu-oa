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
          :placeholder="t('login.username')"
          tabindex="1"
          @keyup.enter="handleLogin"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          :placeholder="t('login.password')"
          tabindex="2"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>

      <el-button :loading="loading" type="primary" class="login-btn" @click="handleLogin">
        {{ t('login.submit') }}
      </el-button>

      <div v-if="registerEnabled" class="register-entry">
        <router-link :to="PATHS.REGISTER">{{ t('login.register') }}</router-link>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'LoginPage' })

import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { translate as t } from '@/i18n'
import { useUserStore } from '@/store/modules/user'
import { buildLoginRedirectPath } from '@/navigation'
import { loadRegisterSwitch } from '@/api/register'
import { PATHS } from '@/router/paths'
import { ElMessage, type FormInstance } from 'element-plus'
import { RecognitionFailure } from '@/recognition'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance | null>(null)
const loading = ref(false)
const redirect = ref<string | undefined>(undefined)
/** 注册开关只在登录页初始化时探测一次；失败按关闭处理，入口不闪。 */
const registerEnabled = ref(false)

const loginForm = ref({
  username: '',
  password: '',
})

const loginRules = {
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur', min: 6 }],
}

watch(
  () => route.query,
  (query) => {
    const target = query?.redirect
    redirect.value = typeof target === 'string' ? target : undefined
    // 注册成功后跳回登录页并预填用户名（#253），不自动登录。
    const username = query?.username
    if (typeof username === 'string' && username) {
      loginForm.value.username = username
    }
  },
  { immediate: true },
)

onMounted(async () => {
  try {
    registerEnabled.value = await loadRegisterSwitch()
  } catch {
    registerEnabled.value = false
  }
})

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
        ElMessage.error(error instanceof Error ? error.message : t('login.failed'))
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

.register-entry {
  margin-top: 16px;
  text-align: center;

  a {
    color: #409eff;
    text-decoration: none;
  }
}
</style>

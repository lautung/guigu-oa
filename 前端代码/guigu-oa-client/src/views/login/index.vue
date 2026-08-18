<template>
  <div class="login-container">
    <el-form
      ref="loginForm"
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
          prefix-icon="el-icon-user"
          tabindex="1"
          @keyup.enter.native="handleLogin"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="密码"
          prefix-icon="el-icon-lock"
          tabindex="2"
          show-password
          @keyup.enter.native="handleLogin"
        />
      </el-form-item>

      <el-button
        :loading="loading"
        type="primary"
        class="login-btn"
        @click.native.prevent="handleLogin"
      >
        登录
      </el-button>
    </el-form>
  </div>
</template>

<script>
import { buildLoginRedirectPath } from '@/navigation/access-policy'

export default {
  name: 'Login',
  data() {
    return {
      loginForm: {
        username: '',
        password: ''
      },
      loginRules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur', min: 6 }]
      },
      loading: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  methods: {
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (!valid) return
        this.loading = true
        this.$store.dispatch('user/login', this.loginForm).then(() => {
          this.$router.push({ path: buildLoginRedirectPath(this.redirect) })
        }).finally(() => {
          this.loading = false
        })
      })
    }
  }
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

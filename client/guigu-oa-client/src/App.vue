<template>
  <div id="app">
    <section v-if="!userStore.recognized && userStore.recovery.status !== 'idle'" class="recovery">
      <h1>云尚办公</h1>
      <p role="status">
        {{
          userStore.recovery.status === 'loading'
            ? t('app.recoveryLoading')
            : t('app.recoveryUnavailable')
        }}
      </p>
      <el-button
        type="primary"
        :loading="userStore.recovery.status === 'loading'"
        @click="userStore.recognition?.resume()"
        >{{ t('app.retry') }}</el-button
      >
    </section>
    <router-view v-else />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { translate as t } from '@/i18n'
import { useUserStore } from '@/store/modules/user'

export default defineComponent({
  name: 'App',
  setup() {
    return { t, userStore: useUserStore() }
  },
})
</script>

<style>
html,
body,
#app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>

<style scoped>
.recovery {
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #303133;
}
.recovery h1 {
  font-size: 24px;
}
.recovery p {
  margin: 16px 0 24px;
}
</style>

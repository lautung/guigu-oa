<template>
  <div class="lock-screen" role="dialog" aria-modal="true" :aria-label="t('shell.lockScreen.aria')">
    <form class="lock-panel" @submit.prevent="submit">
      <div class="lock-brand">
        <span class="lock-brand-logo">云</span>
        <span class="lock-brand-text">{{ t('shell.brand') }}</span>
      </div>
      <h2 class="lock-title">{{ t('shell.lockScreen.locked') }}</h2>
      <p class="lock-hint">
        {{
          userStore.name
            ? t('shell.lockScreen.hintNamed', { name: userStore.name })
            : t('shell.lockScreen.hint')
        }}
      </p>
      <input
        ref="passwordInput"
        v-model="password"
        class="lock-password"
        type="password"
        autocomplete="current-password"
        :placeholder="t('shell.lockScreen.passwordPlaceholder')"
        :disabled="submitting || lockScreen.loggingOut"
      />
      <p v-if="lockScreen.error" class="lock-error" role="alert">{{ lockScreen.error }}</p>
      <button
        class="lock-submit"
        type="submit"
        :disabled="submitting || lockScreen.loggingOut || !password"
      >
        {{ t('shell.lockScreen.unlock') }}
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { translate as t } from '@/i18n'
import { useUserStore } from '@/store/modules/user'
import { useLockScreenStore } from './lock-screen'

const userStore = useUserStore()
const lockScreen = useLockScreenStore()
const password = ref('')
const submitting = ref(false)
const passwordInput = ref<HTMLInputElement | null>(null)

onMounted(async () => {
  await nextTick()
  passwordInput.value?.focus()
})

async function submit(): Promise<void> {
  if (submitting.value || lockScreen.loggingOut) return
  if (!password.value) return
  submitting.value = true
  try {
    const unlocked = await lockScreen.unlock(password.value)
    if (!unlocked) {
      password.value = ''
      await nextTick()
      passwordInput.value?.focus()
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--el-bg-color-page);
}

.lock-panel {
  width: min(360px, 100%);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 28px 28px 24px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-light);
}

.lock-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}

.lock-brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.lock-brand-text {
  font-size: 13px;
}

.lock-title {
  margin: 4px 0 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.lock-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.lock-password {
  width: 100%;
  box-sizing: border-box;
  padding: 9px 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.lock-password:focus {
  border-color: var(--el-color-primary);
  outline: none;
}

.lock-password:disabled {
  background: var(--el-fill-color-light);
}

.lock-error {
  margin: 0;
  font-size: 13px;
  color: var(--el-color-danger);
}

.lock-submit {
  padding: 9px 12px;
  border: 0;
  border-radius: 6px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.lock-submit:hover:not(:disabled) {
  background: var(--el-color-primary-light-3);
}

.lock-submit:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>

<template>
  <div v-loading="profile.state.loading" class="profile-page">
    <h2 class="page-title">{{ t('profile.title') }}</h2>

    <div class="profile-grid">
      <el-card class="profile-card avatar-card" :header="t('profile.avatar')">
        <div class="avatar-row">
          <img
            class="avatar-preview"
            :src="profile.state.avatarUrl || DEFAULT_AVATAR"
            :alt="t('profile.avatar')"
          />
          <div class="avatar-actions">
            <label class="avatar-upload" :class="{ disabled: profile.state.uploading }">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                :disabled="profile.state.uploading"
                @change="onAvatarSelected"
              />
              <span>{{
                profile.state.uploading ? t('profile.uploading') : t('profile.chooseImage')
              }}</span>
            </label>
            <p class="hint">{{ t('profile.avatarHint') }}</p>
            <p v-if="profile.state.avatarError" class="error-row">
              {{ profile.state.avatarError }}
            </p>
          </div>
        </div>
      </el-card>

      <el-card class="profile-card" :header="t('profile.basicInfo')">
        <el-form label-width="72px" @submit.prevent="save">
          <el-form-item :label="t('profile.username')">
            <el-input :model-value="profile.state.username" disabled />
          </el-form-item>
          <el-form-item :label="t('profile.name')">
            <el-input v-model="profile.state.name" maxlength="20" />
          </el-form-item>
          <el-form-item :label="t('profile.phone')">
            <el-input v-model="profile.state.phone" maxlength="20" />
          </el-form-item>
          <p v-if="profile.state.formError" class="error-row">{{ profile.state.formError }}</p>
          <el-button type="primary" :loading="profile.state.saving" @click="save">
            {{ t('profile.save') }}
          </el-button>
        </el-form>
      </el-card>

      <el-card class="profile-card" :header="t('profile.changePassword')">
        <el-form label-width="72px" @submit.prevent="submitPassword">
          <el-form-item :label="t('profile.oldPassword')">
            <el-input v-model="profile.state.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item :label="t('profile.newPassword')">
            <el-input v-model="profile.state.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item :label="t('profile.confirmPassword')">
            <el-input v-model="profile.state.confirmPassword" type="password" show-password />
          </el-form-item>
          <p class="hint">{{ t('profile.changePasswordHint') }}</p>
          <p v-if="profile.state.passwordError" class="error-row">
            {{ profile.state.passwordError }}
          </p>
          <el-button
            type="primary"
            :loading="profile.state.changingPassword"
            @click="submitPassword"
          >
            {{ t('profile.changePassword') }}
          </el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ProfilePage' })

import { onBeforeUnmount, onMounted } from 'vue'

import { translate as t } from '@/i18n'
import { createProfilePage } from '@/profile/profile-browser'

const DEFAULT_AVATAR = '/avatar.svg'

const profile = createProfilePage()

onMounted(() => {
  void profile.load()
})

onBeforeUnmount(() => {
  profile.dispose()
})

function onAvatarSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 允许重新选择同一个文件：清空后 change 才会再次触发。
  input.value = ''
  if (file) void profile.uploadAvatar(file)
}

function save(): void {
  void profile.save()
}

function submitPassword(): void {
  void profile.changePassword()
}
</script>

<style lang="scss" scoped>
.profile-page {
  padding: 0;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}

.profile-card {
  background: var(--el-bg-color);
}

.avatar-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.avatar-preview {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.avatar-actions {
  flex: 1;
  min-width: 0;
}

.avatar-upload {
  display: inline-block;
  padding: 7px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
}

.avatar-upload:hover,
.avatar-upload:focus-within {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.avatar-upload.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.avatar-upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.error-row {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-color-danger);
}
</style>

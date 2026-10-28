<template>
  <div v-loading="loading" class="profile-page">
    <h2 class="page-title">基本信息</h2>
    <div class="profile-card">
      <p><span>姓名</span>{{ profile.name || '—' }}</p>
      <p><span>手机号</span>{{ profile.phone || '—' }}</p>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

import request from '@/utils/request'
import { presentProfile, readProfile } from '@/work/profile'

export default defineComponent({
  name: 'WorkProfile',
  data() {
    return {
      loading: false,
      profile: presentProfile(),
    }
  },
  created() {
    this.fetchProfile()
  },
  methods: {
    async fetchProfile() {
      this.loading = true
      try {
        this.profile = await readProfile(request)
      } catch {
        /* 请求层已提示 */
      } finally {
        this.loading = false
      }
    },
  },
})
</script>

<style lang="scss" scoped>
.profile-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}

.profile-card p {
  margin: 0 0 12px;
  font-size: 16px;
  color: #303133;
}

.profile-card span {
  display: inline-block;
  width: 72px;
  color: #909399;
}
</style>

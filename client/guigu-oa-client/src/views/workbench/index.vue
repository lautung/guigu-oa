<template>
  <div class="workbench-page">
    <header class="workbench-head">
      <h2 class="page-title">工作台</h2>
      <p class="page-subtitle">今天没有待处理事项。</p>
    </header>

    <el-card shadow="never" class="workbench-card">
      <el-empty description="工作台页面正在建设中">
        <el-button type="primary" @click="openPending">前往待处理</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'WorkbenchPage' })

import { useRouter } from 'vue-router'
import { PATHS } from '@/router/paths'

const router = useRouter()

function openPending(): void {
  void router.push(PATHS.WORK_PENDING)
}
</script>

<style lang="scss" scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.workbench-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.workbench-card {
  border-radius: 10px;
}
</style>

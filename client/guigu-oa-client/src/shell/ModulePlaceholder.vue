<template>
  <section class="module-placeholder">
    <el-empty :description="description">
      <p class="module-placeholder-hint">该模块的页面将在后续版本提供。</p>
    </el-empty>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : ''))
const description = computed(() => (title.value ? `${title.value}暂无可用页面` : '暂无可用页面'))
</script>

<style lang="scss" scoped>
.module-placeholder {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  padding: 60px 20px;
}

.module-placeholder-hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

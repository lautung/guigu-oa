<template>
  <div class="shell-breadcrumb">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="(crumb, index) in crumbs" :key="`${index}-${crumb}`">
        {{ crumb }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  crumbs: string[]
}>()
</script>

<style lang="scss" scoped>
.shell-breadcrumb {
  flex-shrink: 0;
  padding: 8px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 899px) {
  .shell-breadcrumb {
    padding: 6px 12px;
  }
}
</style>

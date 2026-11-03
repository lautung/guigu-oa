<template>
  <div class="shell-breadcrumb">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="(crumb, index) in items" :key="`${index}-${crumb.title}`">
        <router-link v-if="crumb.path" :to="crumb.path" class="shell-breadcrumb-link">
          {{ crumb.title }}
        </router-link>
        <span
          v-else
          class="shell-breadcrumb-text"
          :aria-current="index === items.length - 1 ? 'page' : undefined"
        >
          {{ crumb.title }}
        </span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import type { BreadcrumbNode } from './breadcrumb'

defineProps<{
  items: BreadcrumbNode[]
}>()
</script>

<style lang="scss" scoped>
.shell-breadcrumb {
  flex-shrink: 0;
  padding: 8px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.shell-breadcrumb-link {
  color: var(--el-text-color-regular);
  text-decoration: none;
}

.shell-breadcrumb-link:hover,
.shell-breadcrumb-link:focus-visible {
  color: var(--el-color-primary);
  outline: none;
}

@media (max-width: 899px) {
  .shell-breadcrumb {
    padding: 6px 12px;
  }
}
</style>

<template>
  <div class="pagination-wrap">
    <el-pagination
      :background="compact"
      :current-page="page"
      :page-size="size"
      :page-sizes="compact ? undefined : PAGE_SIZES"
      :total="total"
      :layout="compact ? 'prev, pager, next' : 'sizes, prev, pager, next, jumper, ->, total'"
      @current-change="$emit('page-change', $event)"
      @size-change="$emit('size-change', $event)"
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  page: number
  size: number
  total: number
  compact?: boolean
}>()
defineEmits<{
  (event: 'page-change', page: number): void
  (event: 'size-change', size: number): void
}>()

const PAGE_SIZES = [5, 10, 20, 30, 40, 50, 100]
</script>

<style lang="scss" scoped>
.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>

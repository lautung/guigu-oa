<template>
  <el-alert
    v-if="status === 'failed'"
    class="list-load-error"
    type="error"
    :closable="false"
    :title="error || '加载失败'"
  >
    <el-button link @click="$emit('retry')">重试</el-button>
  </el-alert>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import type { PropType } from 'vue'

import type { ListReadStatus } from '@/list/list-read'

export default defineComponent({
  name: 'ListLoadError',
  props: {
    status: {
      type: String as PropType<ListReadStatus>,
      required: true,
    },
    error: {
      type: String,
      default: '',
    },
  },
  emits: ['retry'],
})
</script>

<style lang="scss" scoped>
.list-load-error {
  margin-bottom: 12px;
}
</style>

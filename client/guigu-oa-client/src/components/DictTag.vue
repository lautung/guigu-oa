<template>
  <el-tag v-if="option" :type="tagType" :size="size" class="dict-tag">
    {{ option.dictLabel }}
  </el-tag>
  <span v-else class="dict-tag-fallback">{{ fallback }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useDict } from '@/dict/use-dict'
import type { AuditTagType } from '@/types/domain'

/**
 * 字典标签（#251，ADR 0062）：按 `dictType` 取选项，命中渲染 `el-tag`。
 *
 * 值未命中（类型没种、数据停用/删除）时回退到原值文本，不做静默空白；
 * 空值显示 `emptyText`（默认「—」）。颜色仍是页面的视觉选择：`tagType` 只管样式，
 * 标签文字永远来自字典。
 */
const props = withDefaults(
  defineProps<{
    dictType: string
    value?: string | number | null
    tagType?: AuditTagType
    size?: 'large' | 'default' | 'small'
    emptyText?: string
  }>(),
  { value: null, tagType: 'info', size: 'small', emptyText: '—' },
)

const { dicts } = useDict(props.dictType)

const option = computed(() =>
  (dicts[props.dictType] || []).find((item) => String(item.dictValue) === String(props.value)),
)

const fallback = computed(() => {
  if (props.value == null || props.value === '') return props.emptyText
  return String(props.value)
})
</script>

<style lang="scss" scoped>
.dict-tag-fallback {
  color: #606266;
}
</style>

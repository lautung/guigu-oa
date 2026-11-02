<template>
  <el-popover v-model:visible="visible" placement="bottom-end" :width="320" trigger="click">
    <template #reference>
      <slot name="trigger" :open="() => (visible = true)" />
    </template>
    <div class="menu-search">
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索菜单 / 页面"
        :prefix-icon="Search"
        clearable
        @keyup.enter="pickFirst"
      />
      <div class="menu-search-list">
        <button v-for="item in results" :key="item.key" @click="pick(item)">
          <span class="menu-search-title">{{ item.title }}</span>
          <span class="menu-search-trail">{{ item.trail.join(' / ') }}</span>
        </button>
        <p v-if="!results.length" class="menu-search-empty">没有匹配的菜单</p>
      </div>
      <p class="menu-search-hint">原型示意：第一版是否保留菜单搜索由这轮决定</p>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  items: { type: Array, required: true },
})
const emit = defineEmits(['select'])

const visible = ref(false)
const keyword = ref('')

const results = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  const list = value
    ? props.items.filter(
        (item) =>
          item.title.toLowerCase().includes(value) ||
          item.trail.join(' / ').toLowerCase().includes(value),
      )
    : props.items.slice(0, 8)
  return list.slice(0, 8)
})

watch(visible, (open) => {
  if (!open) keyword.value = ''
})

function pick(item) {
  emit('select', item)
  visible.value = false
}

function pickFirst() {
  if (results.value.length) pick(results.value[0])
}
</script>

<style scoped>
.menu-search-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-search-list button {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  border: 0;
  background: transparent;
  border-radius: 6px;
  padding: 6px 8px;
  cursor: pointer;
  text-align: left;
}

.menu-search-list button:hover {
  background: var(--el-fill-color-light);
}

.menu-search-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.menu-search-trail {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.menu-search-empty {
  margin: 8px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.menu-search-hint {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>

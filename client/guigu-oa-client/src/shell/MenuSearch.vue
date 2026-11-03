<template>
  <el-popover
    v-model:visible="open"
    placement="bottom-end"
    :width="440"
    trigger="click"
    @show="focusInput"
  >
    <template #reference>
      <button type="button" class="menu-search-trigger" title="搜索菜单" aria-label="搜索菜单">
        <el-icon><component :is="shellIcon('Search')" /></el-icon>
      </button>
    </template>

    <div class="menu-search-panel">
      <input
        ref="inputRef"
        v-model="query"
        type="text"
        class="menu-search-input"
        placeholder="搜索页面、菜单或面包屑"
        aria-label="搜索页面、菜单或面包屑"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.enter.prevent="openActive"
        @keydown.esc.prevent="open = false"
      />

      <ul v-if="results.length" class="menu-search-results" role="listbox">
        <li v-for="(item, index) in results" :key="item.path">
          <button
            type="button"
            class="menu-search-item"
            :class="{ active: index === activeIndex }"
            role="option"
            :aria-selected="index === activeIndex"
            @mouseenter="activeIndex = index"
            @click="openEntry(item)"
          >
            <span class="menu-search-title">{{ item.title }}</span>
            <span class="menu-search-trail">{{ item.trail.join(' / ') }}</span>
          </button>
        </li>
      </ul>
      <p v-else-if="query.trim()" class="menu-search-empty">没有匹配的页面</p>
      <p v-else class="menu-search-hint">输入页面标题或面包屑</p>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { buildMenuSearch, searchMenus, type MenuSearchEntry } from './menu-search'
import { shellIcon } from './shell-icons'

const router = useRouter()
const userStore = useUserStore()

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const menuIndex = computed(() => buildMenuSearch(userStore.menus || []))
const results = computed(() => searchMenus(menuIndex.value, query.value))

watch(query, () => {
  activeIndex.value = 0
})

function focusInput(): void {
  void nextTick(() => inputRef.value?.focus())
}

function move(step: number): void {
  const total = results.value.length
  if (!total) return
  activeIndex.value = (activeIndex.value + step + total) % total
}

function openEntry(entry: MenuSearchEntry): void {
  open.value = false
  query.value = ''
  activeIndex.value = 0
  void router.push(entry.path)
}

function openActive(): void {
  const entry = results.value[activeIndex.value]
  if (entry) openEntry(entry)
}
</script>

<style lang="scss" scoped>
.menu-search-trigger {
  width: 32px;
  height: 32px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.menu-search-trigger:hover,
.menu-search-trigger:focus-visible {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  outline: none;
}

.menu-search-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.menu-search-input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  box-sizing: border-box;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-size: 13px;
  outline: none;
}

.menu-search-input:focus {
  border-color: var(--el-color-primary);
}

.menu-search-results {
  max-height: 320px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.menu-search-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.menu-search-item.active {
  background: var(--el-color-primary-light-9);
}

.menu-search-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.menu-search-trail {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.menu-search-empty,
.menu-search-hint {
  margin: 6px 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>

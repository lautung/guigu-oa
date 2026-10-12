<template>
  <aside class="app-sidebar">
    <nav v-for="group in sidebarTree" :key="group.key" class="sidebar-group">
      <div class="group-title" @click="toggleGroup(group.key)">
        <span>{{ group.title }}</span>
        <span v-if="group.key === 'config'">
          {{ groupCollapsed[group.key] ? '▼' : '▲' }}
        </span>
      </div>

      <p v-if="group.hint && groupCollapsed[group.key]" class="group-hint">
        {{ group.hint }}
      </p>

      <ul v-show="!groupCollapsed[group.key]" class="menu-list">
        <sidebar-node
          v-for="item in group.items"
          :key="item.path"
          :item="item"
          :active-path="activePath"
        />
      </ul>
    </nav>
  </aside>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { buildSidebarTree, isMobileViewport } from '@/navigation'
import SidebarNode from './SidebarNode.vue'

const route = useRoute()
const userStore = useUserStore()

const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const groupCollapsed = ref({})

const activePath = computed(() => route.path)

const sidebarTree = computed(() =>
  buildSidebarTree({
    menus: userStore.menus,
    viewportWidth: viewportWidth.value
  })
)

watch(
  sidebarTree,
  tree => {
    const collapsed = {}
    tree.forEach(group => {
      collapsed[group.key] = !!group.collapsed
    })
    groupCollapsed.value = collapsed
  },
  { immediate: true }
)

function handleResize() {
  viewportWidth.value = window.innerWidth
}

function toggleGroup(key) {
  if (key !== 'config' || !isMobileViewport(viewportWidth.value)) return
  groupCollapsed.value = { ...groupCollapsed.value, [key]: !groupCollapsed.value[key] }
}

onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))
</script>

<style lang="scss" scoped>
.app-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #ebeef5;
  padding: 12px 0;
  overflow-y: auto;
}

.sidebar-group + .sidebar-group {
  margin-top: 16px;
  border-top: 1px solid #f2f6fc;
  padding-top: 12px;
}

.group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 8px;
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  text-transform: none;
}

.group-hint {
  margin: 0 16px 8px;
  font-size: 12px;
  line-height: 1.4;
  color: #e6a23c;
}

.menu-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

@media (max-width: 768px) {
  .app-sidebar {
    width: 100%;
    max-width: 100%;
    border-right: none;
    border-bottom: 1px solid #ebeef5;
    max-height: 40vh;
  }
}
</style>

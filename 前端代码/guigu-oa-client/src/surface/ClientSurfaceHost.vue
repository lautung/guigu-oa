<template>
  <div class="client-surface">
    <top-bar />
    <div class="body">
      <app-sidebar />
      <main class="main-content" :class="contentClass">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
import { useUserStore } from '@/store/modules/user'
import { resolveClientSurface } from './client-surface'
import TopBar from '@/layout/components/TopBar.vue'
import AppSidebar from '@/layout/components/AppSidebar.vue'

export default {
  name: 'ClientSurfaceHost',
  components: { TopBar, AppSidebar },
  setup() {
    return { userStore: useUserStore() }
  },
  computed: {
    surface() {
      return resolveClientSurface({
        path: this.$route.path,
        hasToken: true,
        menus: this.userStore.menus || []
      })
    },
    contentClass() {
      return this.surface.density ? `is-${this.surface.density}` : null
    }
  }
}
</script>

<style lang="scss" scoped>
.client-surface {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.main-content {
  flex: 1;
  width: 100%;
  padding: 16px;
  box-sizing: border-box;
  overflow-y: auto;
}

.main-content.is-constrained {
  max-width: 960px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .body {
    flex-direction: column;
  }

  .main-content.is-constrained {
    max-width: none;
  }
}
</style>

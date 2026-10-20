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

<script lang="ts">
import { defineComponent } from 'vue'

import { useUserStore } from '@/store/modules/user'
import { createViewportOccupancyHandler, resolveClientSurface } from './client-surface'
import TopBar from '@/layout/components/TopBar.vue'
import AppSidebar from '@/layout/components/AppSidebar.vue'

export default defineComponent({
  name: 'ClientSurfaceHost',
  components: { TopBar, AppSidebar },
  setup() {
    return { userStore: useUserStore() }
  },
  data() {
    return {
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      handleViewportWidth: null as ((viewportWidth: number) => void) | null,
      onViewportResize: null as (() => void) | null,
    }
  },
  computed: {
    surfaceInput() {
      const path = this.$route.path
      const menus = this.userStore.menus || []
      return {
        path,
        hasToken: true,
        menus,
      }
    },
    surface() {
      return resolveClientSurface({
        ...this.surfaceInput,
        viewportWidth: this.viewportWidth,
      })
    },
    contentClass() {
      return this.surface.density ? `is-${this.surface.density}` : null
    },
  },
  watch: {
    'surface.occupancy'() {
      // A late role assignment may refresh menus after its original page has unmounted.
      this.handleViewportWidth?.(this.viewportWidth)
    },
  },
  created() {
    this.handleViewportWidth = createViewportOccupancyHandler({
      getSurfaceInput: () => this.surfaceInput,
      goTo: (to) => this.$router.replace(to),
    })
  },
  mounted() {
    this.onViewportResize = () => {
      this.viewportWidth = window.innerWidth
      this.handleViewportWidth?.(this.viewportWidth)
    }
    if (this.onViewportResize) {
      window.addEventListener('resize', this.onViewportResize)
    }
  },
  beforeUnmount() {
    if (this.onViewportResize) {
      window.removeEventListener('resize', this.onViewportResize)
    }
  },
})
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

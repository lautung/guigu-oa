<template>
  <div class="config-layout">
    <top-bar />
    <div class="body">
      <app-sidebar />
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
import TopBar from './components/TopBar'
import AppSidebar from './components/AppSidebar'

export default {
  name: 'ConfigLayout',
  components: { TopBar, AppSidebar }
}
</script>

<style lang="scss" scoped>
.config-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>

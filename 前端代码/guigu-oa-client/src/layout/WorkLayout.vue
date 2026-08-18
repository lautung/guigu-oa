<template>
  <div class="work-layout">
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
  name: 'WorkLayout',
  components: { TopBar, AppSidebar }
}
</script>

<style lang="scss" scoped>
.work-layout {
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
  padding: 16px;
  max-width: 960px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .body {
    flex-direction: column;
  }

  .main-content {
    max-width: none;
  }
}
</style>

<template>
  <div class="work-stub">
    <h2>{{ title }}</h2>
    <p>页面占位，后续票实现具体功能。</p>
  </div>
</template>

<script>
export default {
  name: 'WorkStubPage',
  computed: {
    title() {
      return (this.$route.meta && this.$route.meta.title) || '办事'
    }
  }
}
</script>

<style lang="scss" scoped>
.work-stub {
  h2 {
    margin: 0 0 8px;
    color: #303133;
  }

  p {
    margin: 0;
    color: #909399;
  }
}
</style>

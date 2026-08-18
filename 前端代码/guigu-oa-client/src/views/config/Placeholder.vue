<template>
  <div class="placeholder-page">
    <h2>{{ title }}</h2>
    <p>页面占位，后续票实现具体功能。</p>
  </div>
</template>

<script>
export default {
  name: 'ConfigPlaceholder',
  computed: {
    title() {
      return (this.$route.meta && this.$route.meta.title) || '配置'
    }
  }
}
</script>

<style lang="scss" scoped>
.placeholder-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;

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

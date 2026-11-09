<template>
  <div class="config-page placeholder-page">
    <h2 class="page-title">打卡记录</h2>
    <el-empty description="页面建设中，考勤底座就绪后由后续票交付" />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

/**
 * 打卡记录 占位页（#278）：菜单与路由先行，页面实现归后续票。
 */
export default defineComponent({
  name: 'AttendanceRecordPage',
})
</script>

<style lang="scss" scoped>
.placeholder-page {
  min-height: 320px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}
</style>

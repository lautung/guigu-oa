<template>
  <div class="start-page">
    <h2 class="page-title">发起审批</h2>

    <div v-loading="loading">
      <el-empty v-if="!loading && groups.length === 0" description="暂无已发布的审批模板" />

      <div v-for="group in groups" :key="group.id" class="type-group">
        <h3>{{ group.name }}</h3>
        <div class="template-grid">
          <div v-for="template in group.templates" :key="template.id" class="template-card">
            <img v-if="template.iconUrl" :src="template.iconUrl" alt="">
            <span>{{ template.name }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { findProcessType } from '@/api/process'
import { presentStartGroups } from '@/config/process-template'

export default {
  name: 'WorkStart',
  data() {
    return {
      loading: false,
      groups: []
    }
  },
  created() {
    this.fetchGroups()
  },
  methods: {
    fetchGroups() {
      this.loading = true
      findProcessType().then(response => {
        this.groups = presentStartGroups(response.data || [])
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.start-page {
  h2,
  h3 {
    margin: 0;
    color: #303133;
  }
}

.page-title {
  margin-bottom: 16px;
  font-size: 20px;
}

.type-group {
  margin-bottom: 20px;

  h3 {
    margin-bottom: 12px;
    font-size: 16px;
  }
}

.template-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.template-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 88px;
  padding: 8px 4px;
  color: #606266;
  font-size: 12px;
  text-align: center;

  img {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    margin-bottom: 8px;
  }
}
</style>

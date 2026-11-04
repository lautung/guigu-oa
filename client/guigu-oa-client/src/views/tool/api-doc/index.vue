<template>
  <div class="config-page tool-page">
    <div class="page-header">
      <h2 class="page-title">接口文档</h2>
    </div>

    <el-empty
      v-if="availability === 'unavailable'"
      class="api-doc-unavailable"
      description="当前环境未启用接口文档，仅开发环境提供"
    />

    <section v-else-if="availability === 'available'" class="api-doc-panel">
      <p class="api-doc-summary">当前环境已启用接口文档，Swagger UI 会在新窗口中打开。</p>
      <el-button type="primary" class="open-swagger-button" @click="openSwaggerUi">
        打开 Swagger UI
      </el-button>
      <dl class="api-doc-endpoints">
        <div class="api-doc-endpoint">
          <dt>OpenAPI 描述</dt>
          <dd>
            <code>{{ API_DOCS_PATH }}</code>
          </dd>
        </div>
        <div class="api-doc-endpoint">
          <dt>Swagger UI</dt>
          <dd>
            <code>{{ SWAGGER_UI_PATH }}</code>
          </dd>
        </div>
      </dl>
    </section>

    <div v-else v-loading="true" class="api-doc-probing" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  API_DOCS_PATH,
  SWAGGER_UI_PATH,
  probeApiDoc,
  type ApiDocAvailability,
} from '@/config/api-doc'

defineOptions({ name: 'ToolApiDocPage' })

/**
 * null 是挂载后的探测中；探测结果只有可用/未启用两态（ADR 0061）。
 * 这是一次无认证的可选端点探测，没有身份归属与迟到结果问题，不套请求作用域/快照读。
 */
const availability = ref<ApiDocAvailability | null>(null)

function openSwaggerUi(): void {
  window.open(SWAGGER_UI_PATH, '_blank', 'noopener')
}

onMounted(async () => {
  availability.value = await probeApiDoc()
})
</script>

<style lang="scss" scoped>
.tool-page {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.api-doc-panel {
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.api-doc-summary {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.api-doc-endpoints {
  margin: 20px 0 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.api-doc-endpoint {
  display: flex;
  gap: 12px;
  font-size: 13px;

  dt {
    min-width: 96px;
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 0;
    color: var(--el-text-color-primary);
  }
}

.api-doc-probing {
  min-height: 120px;
}
</style>

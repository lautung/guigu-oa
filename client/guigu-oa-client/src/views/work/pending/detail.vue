<template>
  <div class="pending-detail" :aria-busy="state?.loading">
    <div class="detail-toolbar">
      <el-button @click="opening?.back()">返回</el-button>
      <el-button :disabled="state?.loading" @click="opening?.reload()">刷新详情</el-button>
    </div>
    <p v-if="state?.loading" role="status">正在读取详情…</p>
    <el-alert
      v-if="state?.error"
      type="error"
      :closable="false"
      :title="state.stale ? '详情尚未更新' : '详情读取失败'"
      :description="state.error"
      show-icon
    />
    <el-alert
      v-if="state?.recordError"
      type="error"
      :closable="false"
      title="处理恢复记录故障，已暂停新的处理"
      :description="state.recordError"
      show-icon
    />
    <el-alert
      v-if="state?.submitting"
      type="info"
      :closable="false"
      title="正在提交处理"
      description="可以直接离开；离开不会撤销已提交的处理。"
    />
    <section
      v-if="state?.recoveries.length && !state.submitting"
      class="recovery-panel"
      aria-label="处理结果待确认"
    >
      <h3>处理结果待确认</h3>
      <p>尚不能确定原处理是否生效。请核实详情或已处理信息；系统不会自动重发。</p>
      <div v-for="record in state.recoveries" :key="record.operationId">
        <p>所选动作：{{ record.status === 1 ? '通过' : '拒绝' }}</p>
        <el-button
          v-if="opening?.canEndRecovery(record.operationId)"
          @click="opening?.endRecovery(record.operationId)"
          >我已核实，结束本次记录</el-button
        >
      </div>
      <div class="recovery-actions">
        <el-button @click="opening?.viewProcessed()">查看已处理</el-button>
        <el-button @click="opening?.returnPending()">返回待处理</el-button>
      </div>
    </section>
    <el-alert
      v-if="state?.ended"
      type="info"
      :closable="false"
      title="本次记录已结束"
      description="这不表示原请求失败。如仍需处理，请返回最新待处理重新进入并主动选择。"
    />
    <el-button v-if="state?.ended" @click="opening?.returnPending()">返回最新待处理</el-button>
    <el-alert
      v-if="state?.actionError && !state.recoveries.length"
      type="warning"
      :closable="false"
      :title="state.actionError"
    />
    <div v-if="detail" class="detail-card">
      <div class="header">
        <h2>{{ detail.title }}</h2>
        <p class="time">{{ detail.createTime }}</p>
        <p v-if="detail.progressLabel" class="progress">{{ detail.progressLabel }}</p>
        <el-tag :type="detail.statusTagType">{{ detail.statusLabel }}</el-tag>
      </div>

      <section
        v-for="section in detailSections"
        :key="section"
        :class="section === 'records' ? 'record-section' : 'form-section'"
      >
        <template v-if="section === 'records'">
          <h3>审批流转记录</h3>
          <el-timeline v-if="detail.records.length">
            <el-timeline-item
              v-for="(record, index) in detail.records"
              :key="`${record.createTime}-${index}`"
              :timestamp="record.createTime"
              placement="top"
            >
              {{ record.operateUser }} {{ record.description }}
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty-hint">暂无流转记录</div>
        </template>
        <template v-else>
          <h3>表单信息</h3>
          <div v-if="detail.formFields.length === 0" class="empty-hint">暂无表单字段</div>
          <div v-for="field in detail.formFields" :key="field.key" class="form-field">
            <h4>{{ field.key }}</h4>
            <p v-html="field.value" />
          </div>
        </template>
      </section>
    </div>

    <div v-if="detail && detail.canApprove" class="action-bar">
      <el-button :disabled="!state?.canSubmit" @click="opening?.decide(-1)">拒绝</el-button>
      <el-button type="primary" :disabled="!state?.canSubmit" @click="opening?.decide(1)"
        >通过</el-button
      >
    </div>
    <section v-if="detail && state" class="attachment-section" aria-label="附件">
      <h3>附件</h3>
      <p>附件独立于表单内容保存。</p>
      <label
        v-if="state.attachmentUploadAllowed"
        class="attachment-picker"
        :class="{ 'is-disabled': !state.canAddAttachments }"
        :aria-disabled="!state.canAddAttachments"
      >
        {{ state.attachmentBusy ? '附件补传中…' : '添加附件' }}
        <input type="file" multiple :disabled="!state.canAddAttachments" @change="addFiles" />
      </label>
      <p v-if="state.submitting || state.recoveries.length">
        处理在途或结果待确认期间暂停新增补传，附件仍可读取和下载。
      </p>
      <p v-if="state.attachmentLoading" role="status">正在读取附件…</p>
      <p v-if="state.attachmentError" role="alert">{{ state.attachmentError }}</p>
      <el-button :disabled="state.attachmentLoading" @click="opening?.reloadAttachments()"
        >重新读取附件</el-button
      >
      <p v-if="!state.attachmentLoading && !state.attachments.length">暂无附件</p>
      <ul>
        <li v-for="attachment in state.attachments" :key="attachment.id">
          <span>{{ attachment.fileName }}</span>
          <el-button
            link
            :disabled="state.downloadingId === attachment.id"
            @click="opening?.downloadAttachment(attachment)"
            >下载</el-button
          >
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { openRequestScope } from '@/request-scope'
import { useUserStore } from '@/store/modules/user'
import { createApprovalDetail, type ApprovalDetailOpening } from '@/work/approval-detail'
import { createHttpDetailAdapter } from '@/work/approval-detail-http'
import { workListPerspectiveFromRoute } from '@/work/work-list'
import { resolveClientSurface } from '@/surface/client-surface'

defineOptions({ name: 'PendingDetail' })

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const recognition = user.recognition
if (!recognition) throw new Error('认出 module 尚未接入')
const module = createApprovalDetail(
  createHttpDetailAdapter({
    openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
    entryId: () => recognition.capture().entryId,
    storage: {
      getItem: (key) => localStorage.getItem(key),
      setItem: (key, value) => localStorage.setItem(key, value),
    },
    navigate: (path) => {
      void router.push(path)
    },
    openDownload(url, fileName) {
      const link = document.createElement('a')
      link.href = url
      link.rel = 'noopener'
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
    },
  }),
)
const opening = shallowRef<ApprovalDetailOpening>()
const state = computed(() => opening.value?.state)
const detail = computed(() => state.value?.detail)
const detailSections = computed(() =>
  detail.value?.recordsFirst ? ['records', 'form'] : ['form', 'records'],
)
watch(
  [() => route.fullPath, () => (user.token ? recognition.capture().entryId : '')],
  () => {
    opening.value?.release()
    const processId = route.params.id
    if (typeof processId !== 'string' || !user.token) {
      opening.value = undefined
      return
    }
    const perspective = workListPerspectiveFromRoute(route)
    if (!perspective.returnPath)
      perspective.returnPath =
        resolveClientSurface({ path: route.path }).returnPath || '/work/pending'
    opening.value = module.open({
      processId,
      taskId: typeof route.params.taskId === 'string' ? route.params.taskId : undefined,
      perspective,
    })
  },
  { immediate: true },
)
onBeforeUnmount(() => opening.value?.release())
function addFiles(event: Event) {
  const input = event.currentTarget as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  void opening.value?.addAttachments(files)
}
</script>

<style lang="scss" scoped>
.detail-toolbar,
.recovery-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.recovery-panel,
.attachment-section {
  margin: 16px 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow-wrap: anywhere;
}
.attachment-picker {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 12px;
  margin-bottom: 12px;
  overflow: hidden;
  color: #409eff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  cursor: pointer;

  &:focus-within {
    outline: 2px solid #409eff;
    outline-offset: 2px;
  }
  &.is-disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
  input {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0;
    cursor: inherit;
  }
}

.attachment-section li {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pending-detail {
  padding-bottom: 72px;
}

.detail-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.header {
  h2 {
    margin: 4px 0 8px;
    font-size: 20px;
    color: #303133;
  }

  .time {
    margin: 0 0 8px;
    font-size: 13px;
    color: #909399;
  }

  .progress {
    margin: 0 0 8px;
    font-size: 14px;
    color: #606266;
  }
}

.form-section,
.record-section {
  margin-top: 24px;

  h3 {
    margin: 0 0 12px;
    font-size: 15px;
    color: #303133;
  }
}

.form-field {
  margin-bottom: 12px;

  h4 {
    margin: 0 0 4px;
    font-size: 13px;
    color: #909399;
    font-weight: 500;
  }

  p {
    margin: 0;
    font-size: 15px;
    color: #303133;
    line-height: 1.5;
    word-break: break-word;
  }

  :deep(img) {
    max-width: 100%;
    height: auto;
  }
}

.empty-hint {
  font-size: 13px;
  color: #c0c4cc;
}

.action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 0;
  background: #f5f7fa;
}

@media (max-width: 768px) {
  .action-bar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    padding: 12px 16px;
    background: #fff;
    border-top: 1px solid #ebeef5;
    z-index: 10;
  }
}
</style>

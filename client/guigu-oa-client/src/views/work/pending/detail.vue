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
      v-if="state?.submitting || state?.transferring || state?.addingSign"
      type="info"
      :closable="false"
      title="正在提交处理"
      description="可以直接离开；离开不会撤销已提交的处理。"
    />
    <section
      v-if="
        state?.recoveries.length && !state.submitting && !state.transferring && !state.addingSign
      "
      class="recovery-panel"
      aria-label="处理结果待确认"
    >
      <h3>处理结果待确认</h3>
      <p>尚不能确定原处理是否生效。请核实详情或已处理信息；系统不会自动重发。</p>
      <div v-for="record in state.recoveries" :key="record.operationId">
        <p>
          所选动作：{{
            record.action === 'withdraw'
              ? '撤回'
              : record.action === 'transfer'
                ? '转办'
                : record.action === 'addSign'
                  ? '加签'
                  : record.status === 1
                    ? '通过'
                    : '拒绝'
          }}
        </p>
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
      :description="
        detail?.perspective?.kind === 'pending'
          ? '这不表示原请求失败。如仍需处理，请返回最新待处理重新进入并主动选择。'
          : '这不表示原请求失败。请刷新详情或返回列表核实当前状态。'
      "
    />
    <el-button
      v-if="state?.ended && detail?.perspective?.kind === 'pending'"
      @click="opening?.returnPending()"
      >返回最新待处理</el-button
    >
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
        <!-- 待处理行状态（#288，ADR 0068）：挂起行/候选人行/催办标记在详情头部呈现。 -->
        <el-tag v-if="detail.pendingRow?.suspended" type="warning" effect="plain"
          >等待加签人处理</el-tag
        >
        <el-tag v-else-if="detail.pendingRow?.actionKind === 'CANDIDATE'" type="info" effect="plain"
          >候选人之一，处理即认领</el-tag
        >
        <el-tag v-if="detail.pendingRow?.urged" type="warning" effect="plain">已催办</el-tag>
        <!-- 申请人动作（#287，ADR 0068）：只在已发起视角的审批中出现，不进待处理动作主格。 -->
        <div v-if="state?.showWithdraw || state?.showUrge" class="applicant-actions">
          <el-button
            v-if="state?.showWithdraw"
            :disabled="!state?.canWithdraw"
            @click="opening?.withdraw()"
            >撤回审批</el-button
          >
          <el-button v-if="state?.showUrge" :disabled="!state?.canUrge" @click="opening?.urge()"
            >催办</el-button
          >
        </div>
        <p v-if="state?.urgeDone" class="urge-hint" role="status">
          已提醒当前处理人；同一审批 24 小时内仅可催办一次。
        </p>
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
            <p>{{ field.value }}</p>
          </div>
        </template>
      </section>
    </div>

    <div v-if="detail && detail.canApprove" class="action-bar">
      <el-button :disabled="!state?.canSubmit" @click="opening?.decide(-1)">拒绝</el-button>
      <el-button type="primary" :disabled="!state?.canSubmit" @click="opening?.decide(1)"
        >通过</el-button
      >
      <!-- 转办/加签（#288，ADR 0068）：当前处理人且未挂起；挂起行与加签行不出现。 -->
      <el-button
        v-if="state?.showTransfer"
        :disabled="!state?.canTransfer"
        @click="openTransferDialog"
        >转办</el-button
      >
      <el-button v-if="state?.showAddSign" :disabled="!state?.canAddSign" @click="openAddSignDialog"
        >加签</el-button
      >
    </div>
    <el-dialog v-model="transferVisible" title="转办审批" width="420px">
      <p class="dialog-hint">
        转办后审批永久交给目标用户；你将成为转办经手人，只读可见（不能处理、不能补传）。
      </p>
      <el-select
        v-model="transferTargetId"
        filterable
        placeholder="选择转办目标"
        class="dialog-select"
        :loading="state?.actionTargetsLoading"
      >
        <el-option
          v-for="target in targetOptions"
          :key="target.id"
          :label="target.name"
          :value="target.id"
        />
      </el-select>
      <p v-if="state?.actionTargetsError" role="alert">{{ state.actionTargetsError }}</p>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!transferTargetId || !state?.canTransfer || state?.transferring"
          @click="confirmTransfer"
          >{{ state?.transferring ? '转办提交中…' : '确认转办' }}</el-button
        >
      </template>
    </el-dialog>
    <el-dialog v-model="addSignVisible" title="加签" width="480px">
      <el-radio-group v-model="addSignType" class="dialog-radio">
        <el-radio :value="1">前加签：先由加签人处理，全部通过后回到我</el-radio>
        <el-radio :value="2">后加签：我的通过先记录，加签人全部通过后进入下一节点</el-radio>
      </el-radio-group>
      <el-select
        v-model="addSignAssigneeIds"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        placeholder="选择加签人（可多选，全部通过才算通过）"
        class="dialog-select"
        :loading="state?.actionTargetsLoading"
      >
        <el-option
          v-for="target in targetOptions"
          :key="target.id"
          :label="target.name"
          :value="target.id"
        />
      </el-select>
      <p v-if="state?.actionTargetsError" role="alert">{{ state.actionTargetsError }}</p>
      <template #footer>
        <el-button @click="addSignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!addSignAssigneeIds.length || !state?.canAddSign || state?.addingSign"
          @click="confirmAddSign"
          >{{ state?.addingSign ? '加签提交中…' : '确认加签' }}</el-button
        >
      </template>
    </el-dialog>
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
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import {
  createApprovalDetail,
  type ActionTarget,
  type ApprovalDetailOpening,
} from '@/work/approval-detail'
import { createHttpDetailAdapter } from '@/work/approval-detail-http'
import { workListPerspectiveFromRoute } from '@/work/work-list'
import type { EntityId } from '@/types/domain'

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
// 转办/加签弹层（#288）：服务端已排除申请人；排除本人按姓名本地过滤（写前校验兜底）。
const transferVisible = ref(false)
const transferTargetId = ref<EntityId | ''>('')
const addSignVisible = ref(false)
const addSignType = ref<1 | 2>(1)
const addSignAssigneeIds = ref<EntityId[]>([])
const targetOptions = computed<ActionTarget[]>(() =>
  (state.value?.actionTargets || []).filter((target) => target.name !== user.name),
)
function openTransferDialog() {
  transferTargetId.value = ''
  transferVisible.value = true
  void opening.value?.loadActionTargets()
}
function openAddSignDialog() {
  addSignType.value = 1
  addSignAssigneeIds.value = []
  addSignVisible.value = true
  void opening.value?.loadActionTargets()
}
function confirmTransfer() {
  if (!transferTargetId.value) return
  opening.value?.transfer(transferTargetId.value)
  transferVisible.value = false
}
function confirmAddSign() {
  if (!addSignAssigneeIds.value.length) return
  opening.value?.addSign(addSignType.value, addSignAssigneeIds.value)
  addSignVisible.value = false
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

  .el-tag {
    margin-right: 8px;
  }

  .applicant-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }

  .urge-hint {
    margin: 8px 0 0;
    font-size: 13px;
    color: #909399;
  }
}

.dialog-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #909399;
}

.dialog-select {
  width: 100%;
}

.dialog-radio {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
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
    white-space: pre-wrap;
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

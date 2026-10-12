<template>
  <div v-loading="loading" class="pending-detail">
    <div v-if="detail" class="detail-card">
      <div class="header">
        <el-button type="text" icon="el-icon-arrow-left" @click="goBack">返回</el-button>
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
      <el-button :disabled="submitting" @click="submitApproval(-1)">拒绝</el-button>
      <el-button type="primary" :disabled="submitting" @click="submitApproval(1)">通过</el-button>
    </div>
    <AttachmentSection v-if="detail" :process-id="detail.id" />
  </div>
</template>

<script>
import AttachmentSection from '@/components/AttachmentSection.vue'
import { show, approve } from '@/api/process'
import {
  buildApprovalPayload,
  postApprovalRoute,
  presentDetail
} from '@/work/pending-flow'
import { resolveClientSurface } from '@/surface/client-surface'
import { workListPerspectiveFromRoute } from '@/work/work-list'

export default {
  name: 'PendingDetail',
  components: { AttachmentSection },
  data() {
    return {
      loading: false,
      submitting: false,
      detail: null
    }
  },
  created() {
    this.fetchDetail()
  },
  computed: {
    detailSections() {
      if (!this.detail) return []
      if (this.detail.recordsFirst) return ['records', 'form']
      return ['form', 'records']
    }
  },
  methods: {
    fetchDetail() {
      this.loading = true
      show(this.$route.params.id).then(response => {
        this.detail = presentDetail(
          response.data || {},
          workListPerspectiveFromRoute(this.$route)
        )
      }).finally(() => {
        this.loading = false
      })
    },
    submitApproval(status) {
      const payload = buildApprovalPayload({
        processId: this.detail.id,
        taskId: this.$route.params.taskId,
        status
      })
      this.submitting = true
      approve(payload).then(() => {
        this.$router.push(postApprovalRoute())
      }).finally(() => {
        this.submitting = false
      })
    },
    goBack() {
      const returnPath = this.detail && this.detail.perspective && this.detail.perspective.returnPath
      if (returnPath) {
        this.$router.push(returnPath)
        return
      }
      const surface = resolveClientSurface({
        path: this.$route.path
      })
      if (surface.returnPath) {
        this.$router.push(surface.returnPath)
        return
      }
      this.$router.push({ name: 'Pending' })
    }
  }
}
</script>

<style lang="scss" scoped>
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

  ::v-deep img {
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

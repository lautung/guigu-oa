<template>
  <section class="attachment-section">
    <div class="section-heading">
      <div>
        <h3>附件</h3>
        <p>附件独立于表单内容保存。</p>
      </div>
      <label v-if="canUpload" class="select-file-button">
        {{ uploadingCount ? `上传中 ${uploadingCount}` : '添加附件' }}
        <input
          type="file"
          multiple
          :disabled="loading || uploadingCount > 0"
          @change="onFilesSelected"
        >
      </label>
    </div>

    <div v-if="loading" class="empty-hint">正在读取附件…</div>
    <div v-else-if="loadFailed" class="empty-hint">
      附件读取失败，请重试。
      <button type="button" class="text-button" @click="load">重试</button>
    </div>
    <div v-else-if="attachments.length === 0" class="empty-hint">暂无附件</div>
    <ul v-else class="attachment-list">
      <li v-for="attachment in attachments" :key="attachment.id" class="attachment-row">
        <span class="file-name">{{ attachment.fileName }}</span>
        <span class="file-size">{{ formatSize(attachment.sizeBytes) }}</span>
        <button
          v-if="processId"
          type="button"
          class="text-button"
          :disabled="downloadingId === attachment.id"
          @click="download(attachment)"
        >
          {{ downloadingId === attachment.id ? '准备中…' : '下载' }}
        </button>
        <button
          v-else
          type="button"
          class="text-button danger-button"
          :disabled="deletingId === attachment.id"
          @click="remove(attachment)"
        >
          {{ deletingId === attachment.id ? '删除中…' : '删除' }}
        </button>
      </li>
    </ul>
  </section>
</template>

<script>
import {
  completeUpload,
  createDownloadGrant,
  createUpload,
  deletePending,
  listDraft,
  listForProcess
} from '@/api/attachment'
import { uploadAttachment } from '@/work/attachments'

export default {
  name: 'AttachmentSection',
  props: {
    draftId: {
      type: String,
      default: ''
    },
    processId: {
      type: [Number, String],
      default: null
    }
  },
  data() {
    return {
      attachments: [],
      canUpload: !this.processId,
      loading: false,
      uploadingCount: 0,
      loadFailed: false,
      deletingId: null,
      downloadingId: null
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      if (!this.processId && !this.draftId) return
      this.loading = true
      this.$emit('busy-change', true)
      try {
        if (this.processId) {
          const response = await listForProcess(this.processId)
          const data = response.data || {}
          this.attachments = data.attachments || []
          this.canUpload = !!data.canUpload
        } else {
          const response = await listDraft(this.draftId)
          this.attachments = response.data || []
        }
        this.loadFailed = false
        this.$emit('ready-change', true)
        this.emitAttachmentIds()
      } catch (error) {
        this.loadFailed = true
        this.$emit('ready-change', false)
        this.$message.error('附件读取失败')
      } finally {
        this.loading = false
        this.$emit('busy-change', this.uploadingCount > 0)
      }
    },
    async onFilesSelected(event) {
      const files = Array.from(event.target.files || [])
      event.target.value = ''
      if (!files.length) return

      for (const file of files) {
        this.uploadingCount += 1
        this.$emit('busy-change', true)
        try {
          const attachment = await uploadAttachment(file, {
            draftId: this.draftId,
            processId: this.processId,
            createUpload,
            completeUpload,
            put: (url, options) => fetch(url, options)
          })
          this.attachments.push(attachment)
          this.emitAttachmentIds()
        } catch (error) {
          this.$message.error(error.message || '附件上传失败')
        } finally {
          this.uploadingCount -= 1
          this.$emit('busy-change', this.uploadingCount > 0)
        }
      }
    },
    async remove(attachment) {
      this.deletingId = attachment.id
      try {
        await deletePending(attachment.id)
        this.attachments = this.attachments.filter(item => item.id !== attachment.id)
        this.emitAttachmentIds()
      } finally {
        this.deletingId = null
      }
    },
    async download(attachment) {
      this.downloadingId = attachment.id
      try {
        const response = await createDownloadGrant(this.processId, attachment.id)
        const link = document.createElement('a')
        link.href = response.data.downloadUrl
        link.rel = 'noopener'
        link.download = attachment.fileName
        document.body.appendChild(link)
        link.click()
        link.remove()
      } finally {
        this.downloadingId = null
      }
    },
    formatSize(sizeBytes) {
      if (sizeBytes < 1024) return `${sizeBytes} B`
      if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`
      return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
    },
    emitAttachmentIds() {
      this.$emit('attachments-change', this.attachments.map(item => item.id))
    }
  }
}
</script>

<style lang="scss" scoped>
.attachment-section {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h3 {
    margin: 0 0 4px;
    color: #303133;
    font-size: 15px;
  }

  p {
    margin: 0;
    color: #909399;
    font-size: 13px;
  }
}

.select-file-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  overflow: hidden;
  color: #409eff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;

  input {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0;
    cursor: pointer;
  }
}

.attachment-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.attachment-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 38px;
  border-top: 1px solid #f2f3f5;
  font-size: 14px;
}

.file-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  color: #303133;
}

.file-size,
.empty-hint {
  color: #909399;
  font-size: 13px;
}

.text-button {
  padding: 4px;
  color: #409eff;
  border: 0;
  background: transparent;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }
}

.danger-button {
  color: #f56c6c;
}

.empty-hint {
  margin-top: 12px;
}

@media (max-width: 768px) {
  .attachment-section {
    padding: 12px;
  }

  .attachment-row {
    gap: 8px;
  }
}
</style>

<template>
  <section class="attachment-section">
    <div class="heading">
      <div>
        <h3>附件</h3>
        <p>附件独立于表单内容保存。</p>
      </div>
      <label class="select-file-button">
        {{ view.uploading ? `上传中 ${view.uploading}` : '添加附件' }}
        <input
          type="file"
          aria-label="添加附件"
          multiple
          :disabled="view.attachmentLocked || !view.attachmentsReady"
          @change="onFilesSelected"
        />
      </label>
    </div>
    <p v-if="view.reading" role="status">正在读取附件…</p>
    <p v-else-if="!view.attachmentsReady">
      附件读取未完成。<button type="button" @click="start.reload">重试读取</button>
    </p>
    <p v-else-if="!view.attachments.length && !view.interrupted.length">暂无附件</p>
    <ul>
      <li v-for="attachment in view.attachments" :key="attachment.id">
        <span class="file-name">{{ attachment.fileName }}</span>
        <span v-if="attachment.availability === 'unavailable'" class="unavailable"
          >已不可用于本次发起，请重新选择或不再附加</span
        >
        <span v-else>{{ formatSize(attachment.sizeBytes) }}</span>
        <label v-if="attachment.availability === 'unavailable'" class="select-file-button">
          重新选择
          <input
            type="file"
            :aria-label="`重新选择 ${attachment.fileName}`"
            :disabled="view.attachmentLocked || !view.attachmentsReady"
            @change="replaceUnavailable($event, attachment.id)"
          />
        </label>
        <button
          v-if="attachment.availability === 'unavailable'"
          type="button"
          :disabled="view.attachmentLocked"
          @click="start.omitUnavailable(attachment.id)"
        >
          不再附加
        </button>
        <button
          v-else
          type="button"
          :disabled="view.attachmentLocked"
          @click="start.remove(attachment.id)"
        >
          {{ view.deleting.includes(attachment.id) ? '删除中…' : '删除' }}
        </button>
      </li>
      <li v-for="item in view.interrupted" :key="item.id">
        <span class="file-name">{{ item.name }}</span>
        <span class="unavailable">未确认上传完成，请重新选择文件或检查已登记附件</span>
        <label class="select-file-button">
          重新选择
          <input
            type="file"
            :aria-label="`重新选择未完成文件 ${item.name}`"
            :disabled="view.attachmentLocked || !view.attachmentsReady"
            @change="retryInterrupted($event, item.id)"
          />
        </label>
        <button
          type="button"
          :disabled="view.attachmentLocked"
          @click="start.omitInterrupted(item.id)"
        >
          忽略未完成文件
        </button>
      </li>
    </ul>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { ApprovalStartFacade } from './approval-start-browser'
import type { EntityId } from '@/types/domain'
const props = defineProps<{ start: ApprovalStartFacade }>()
const view = computed(() => props.start.view.value)
function selectedFiles(event: Event) {
  const input = event.currentTarget as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  return files
}
function onFilesSelected(event: Event) {
  void props.start.add(selectedFiles(event))
}
function replaceUnavailable(event: Event, id: EntityId) {
  void props.start.replaceUnavailable(id, selectedFiles(event))
}
function retryInterrupted(event: Event, id: string) {
  void props.start.retryInterrupted(id, selectedFiles(event))
}
function formatSize(size: number) {
  return size < 1024
    ? `${size} B`
    : size < 1048576
      ? `${(size / 1024).toFixed(1)} KB`
      : `${(size / 1048576).toFixed(1)} MB`
}
</script>
<style scoped>
.attachment-section {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}
.heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
h3 {
  margin: 0 0 4px;
  font-size: 15px;
}
p {
  font-size: 13px;
  color: #606266;
}
.select-file-button {
  position: relative;
  flex-shrink: 0;
  padding: 6px 12px;
  border: 1px solid #b3d8ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 13px;
}
.select-file-button input {
  position: absolute;
  inset: 0;
  width: 100%;
  opacity: 0;
  cursor: pointer;
}
ul {
  padding: 0;
  margin: 12px 0 0;
  list-style: none;
}
li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  font-size: 13px;
}
.file-name {
  flex: 1;
  min-width: 100px;
  overflow-wrap: anywhere;
}
.unavailable {
  color: #a05a17;
}
button {
  background: transparent;
  border: 0;
  color: #409eff;
  cursor: pointer;
  padding: 4px;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>

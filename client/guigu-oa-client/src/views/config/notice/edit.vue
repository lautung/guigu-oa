<template>
  <div class="config-page notice-editor">
    <div class="page-header">
      <h2 class="page-title">{{ state.id === '' ? '新建公告' : '编辑公告' }}</h2>
      <el-button @click="back">返回</el-button>
    </div>

    <p v-if="state.phase === 'loading'" role="status" class="notice-loading">正在读取公告…</p>

    <ListLoadError
      v-else-if="state.phase === 'load-error'"
      status="failed"
      :error="state.error"
      @retry="reload"
    />

    <template v-else>
      <el-alert
        v-if="state.error"
        class="notice-editor-error"
        type="error"
        :title="state.error"
        :closable="false"
      />

      <div class="notice-meta-row">
        <el-tag v-if="state.status !== null" :type="statusTagType" size="small">
          {{ statusLabel }}
        </el-tag>
        <span v-if="state.readOnly" class="notice-readonly-hint">
          已撤回的公告只读，只能再次发布或删除。
        </span>
        <span v-else-if="state.dirty" class="notice-dirty-hint">有未保存修改</span>
      </div>

      <el-form label-width="80px" class="notice-form">
        <el-form-item label="标题">
          <el-input
            :model-value="state.form.title"
            :disabled="!state.canEdit"
            maxlength="100"
            show-word-limit
            placeholder="公告标题"
            @update:model-value="updateForm({ title: $event })"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group
            :model-value="state.form.type"
            :disabled="!state.canEdit"
            @update:model-value="updateForm({ type: Number($event) })"
          >
            <el-radio v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-tabs v-model="tab" class="notice-editor-tabs">
        <el-tab-pane label="编辑" name="edit">
          <QuillEditor
            ref="quillRef"
            :model-value="state.form.content"
            :readonly="!state.canEdit"
            :show-image="canUploadImage"
            :load-image="fetchNoticeImage"
            placeholder="请输入公告正文"
            @update:model-value="updateForm({ content: $event })"
            @image="chooseImage"
          />
          <input
            ref="fileInput"
            class="notice-image-input"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            hidden
            @change="onFileChange"
          />
          <p v-if="uploading" role="status" class="notice-uploading">图片上传中…</p>
        </el-tab-pane>
        <el-tab-pane label="预览" name="preview">
          <NoticeContent :html="state.form.content" />
        </el-tab-pane>
      </el-tabs>

      <div class="action-row">
        <el-button
          v-if="state.canEdit && state.id === ''"
          v-hasPermi="permissions.add"
          type="primary"
          :loading="state.phase === 'saving'"
          @click="saveDraft"
          >保存草稿</el-button
        >
        <el-button
          v-else-if="state.canEdit"
          v-hasPermi="permissions.edit"
          type="primary"
          :loading="state.phase === 'saving'"
          @click="saveDraft"
          >{{ state.status === 1 ? '保存' : '保存草稿' }}</el-button
        >
        <el-button
          v-if="state.canPublish && canCreate"
          v-hasPermi="permissions.publish"
          type="success"
          :loading="state.phase === 'saving'"
          @click="publish"
          >{{ state.status === 2 ? '再次发布' : '发布' }}</el-button
        >
        <el-button
          v-if="state.canWithdraw"
          v-hasPermi="permissions.publish"
          type="warning"
          :loading="state.phase === 'saving'"
          @click="withdraw"
          >撤回</el-button
        >
        <el-button
          v-if="state.id !== ''"
          v-hasPermi="permissions.remove"
          type="danger"
          plain
          :loading="state.phase === 'saving'"
          @click="remove"
          >删除</el-button
        >
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import ListLoadError from '@/components/ListLoadError.vue'
import NoticeContent from '@/notice/NoticeContent.vue'
import QuillEditor from '@/notice/QuillEditor.vue'
import { createNoticeEditorPage } from '@/notice/admin-browser'
import { fetchNoticeImage, uploadNoticeImage } from '@/notice/api'
import {
  NOTICE_PERMISSIONS,
  NOTICE_TYPES,
  noticeStatusLabel,
  noticeStatusTagType,
} from '@/notice/config'
import { hasPermission } from '@/permission'
import { PATHS } from '@/router/paths'

defineOptions({ name: 'SysNoticeEdit' })

/**
 * 公告编辑页（#220）：状态机与 dirty 在 `src/notice/admin.ts`，这里只做绑定。
 * 正文编辑用 Quill 2；预览走 `NoticeContent.vue` 的唯一正文渲染面并复用图片水合，
 * 保存载荷里只有服务端图片 URL，blob 地址不会写回正文。
 */

const route = useRoute()
const router = useRouter()
const editor = createNoticeEditorPage(router)
const state = editor.state
const permissions = NOTICE_PERMISSIONS
const typeOptions = NOTICE_TYPES
const tab = ref('edit')
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const quillRef = ref<{ insertImage(url: string): void } | null>(null)

const canUploadImage = computed(
  () => hasPermission(permissions.add) || hasPermission(permissions.edit),
)
const canCreate = computed(() => state.id !== '' || hasPermission(permissions.add))
const statusLabel = computed(() => noticeStatusLabel(state.status))
const statusTagType = computed(() => noticeStatusTagType(state.status))

function routeId(): string | undefined {
  const value = route.params.id
  const raw = Array.isArray(value) ? value[0] : value
  return raw == null || raw === '' ? undefined : String(raw)
}

watch(
  () => route.params.id,
  () => {
    const id = routeId()
    // 新建成功后 replace 到编辑路径：状态机已经拿着同一个 id，不重新载入。
    if (id != null && String(state.id) === id) return
    void editor.open(id)
  },
  { immediate: true },
)

onBeforeRouteLeave(() => editor.requestLeave())
onBeforeUnmount(() => editor.dispose())

function reload() {
  void editor.open(routeId())
}

function updateForm(change: { title?: string; type?: number; content?: string }) {
  editor.updateForm(change)
}

async function back() {
  if (await editor.requestLeave()) void router.push(PATHS.SYSTEM_NOTICE)
}

async function saveDraft() {
  await editor.saveDraft()
}

async function publish() {
  await editor.publish()
}

async function withdraw() {
  await editor.withdraw()
}

async function remove() {
  await editor.remove()
}

function chooseImage() {
  if (!canUploadImage.value) return
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !canUploadImage.value) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage({ message: '图片不能超过 5MB', type: 'error', duration: 3000 })
    return
  }
  uploading.value = true
  try {
    const response = await uploadNoticeImage(file)
    const url = response.data?.url
    if (!url) {
      ElMessage({ message: '图片上传结果不完整，请重试', type: 'error', duration: 3000 })
      return
    }
    quillRef.value?.insertImage(url)
  } catch {
    // 请求层已经提示失败；正文保持原样，不插入半截地址。
  } finally {
    uploading.value = false
  }
}
</script>

<style lang="scss" scoped>
.config-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
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

.notice-loading {
  color: #606266;
}

.notice-editor-error {
  margin-bottom: 12px;
}

.notice-meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.notice-readonly-hint {
  color: #909399;
  font-size: 13px;
}

.notice-dirty-hint {
  color: #e6a23c;
  font-size: 13px;
}

.notice-form {
  max-width: 720px;
}

.notice-editor-tabs {
  margin-top: 8px;
}

.notice-uploading {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .config-page {
    padding: 16px;
  }

  .notice-form {
    max-width: none;
  }
}
</style>

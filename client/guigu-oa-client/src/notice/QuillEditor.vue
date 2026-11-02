<template>
  <div class="notice-quill" :class="{ 'is-readonly': readonly }">
    <div ref="host" class="notice-quill-host"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Quill from 'quill'
import 'quill/dist/quill.snow.css'

import { noticeQuillFormats, noticeQuillToolbar } from '@/notice/quill'

/**
 * Quill 2 的本地包装（ADR 0055）。Quill 自己拥有 DOM，本组件不注入模板 HTML，
 * 正文渲染仍只经 `NoticeContent.vue`。工具栏能力由 `@/notice/quill` 一处拥有。
 *
 * 图片按钮不弹 Quill 自带的 base64 对话框：把点击交给页面，由页面上传到服务端
 * 后调用 `insertImage(serverUrl)` 在光标处插入同源地址。
 */

defineOptions({ name: 'QuillEditor' })

const props = withDefaults(
  defineProps<{
    modelValue?: string
    readonly?: boolean
    showImage?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: '',
    readonly: false,
    showImage: true,
    placeholder: '',
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'image'): void
}>()

interface ToolbarModule {
  addHandler(name: string, handler: () => void): void
}

const host = ref<HTMLElement>()
let quill: Quill | null = null
let applyingExternalValue = false

function currentHtml(): string {
  return quill?.root.innerHTML || ''
}

function applyValue(value: string) {
  if (!quill || value === currentHtml()) return
  applyingExternalValue = true
  try {
    quill.clipboard.dangerouslyPasteHTML(value)
  } finally {
    applyingExternalValue = false
  }
}

onMounted(() => {
  if (!host.value) return
  quill = new Quill(host.value, {
    theme: 'snow',
    readOnly: props.readonly,
    placeholder: props.placeholder,
    modules: { toolbar: noticeQuillToolbar({ image: props.showImage }) },
    formats: noticeQuillFormats({ image: props.showImage }),
  })
  applyValue(props.modelValue)
  quill.on('text-change', () => {
    if (applyingExternalValue || !quill) return
    emit('update:modelValue', quill.root.innerHTML)
  })
  const toolbar = quill.getModule('toolbar') as ToolbarModule | undefined
  toolbar?.addHandler('image', () => emit('image'))
  quill.enable(!props.readonly)
})

watch(
  () => props.modelValue,
  (value) => applyValue(value || ''),
)

watch(
  () => props.readonly,
  (value) => quill?.enable(!value),
)

onBeforeUnmount(() => {
  quill = null
})

/** 在光标处插入服务端返回的同源图片地址；正文里只保存 URL，不保存 blob。 */
function insertImage(url: string) {
  if (!quill || props.readonly || !url) return
  const range = quill.getSelection(true)
  const index = range ? range.index : quill.getLength()
  quill.insertEmbed(index, 'image', url, 'user')
  quill.setSelection(index + 1, 0, 'silent')
}

defineExpose({ insertImage })
</script>

<style lang="scss" scoped>
.notice-quill {
  width: 100%;
  background: #fff;
}

.notice-quill-host {
  min-height: 320px;

  :deep(.ql-toolbar) {
    border-color: #dcdfe6;
    border-radius: 4px 4px 0 0;
  }

  :deep(.ql-container) {
    min-height: 260px;
    border-color: #dcdfe6;
    border-radius: 0 0 4px 4px;
    font-size: 15px;
  }

  :deep(.ql-editor) {
    min-height: 260px;
  }
}

.is-readonly :deep(.ql-toolbar) {
  display: none;
}

.is-readonly :deep(.ql-container) {
  border-radius: 4px;
  background: #f5f7fa;
}

@media (max-width: 768px) {
  .notice-quill-host :deep(.ql-toolbar) {
    white-space: normal;
  }
}
</style>

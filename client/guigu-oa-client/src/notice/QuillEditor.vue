<template>
  <div class="notice-quill" :class="{ 'is-readonly': readonly }">
    <div ref="host" class="notice-quill-host"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Quill from 'quill'
import 'quill/dist/quill.snow.css'

import { fetchNoticeImage } from '@/notice/api'
import { createNoticeImageHydrator } from '@/notice/notice-images'
import { noticeQuillFormats, noticeQuillToolbar } from '@/notice/quill'

/**
 * Quill 2 的本地包装（ADR 0055）。Quill 自己拥有 DOM，本组件不注入模板 HTML，
 * 正文渲染仍只经 `NoticeContent.vue`。工具栏能力由 `@/notice/quill` 一处拥有。
 *
 * 图片按钮不弹 Quill 自带的 base64 对话框：把点击交给页面，由页面上传到服务端
 * 后调用 `insertImage(serverUrl)` 在光标处插入同源地址。
 *
 * 编辑器里的图片用与 `NoticeContent` 同一条认证取回路径预置成 `blob:`（原生 `<img>`
 * 带不上 `token` 头）；服务端地址留在 `data-notice-src`，序列化时回写，所以保存载荷
 * 永远只有同源 URL，不会出现 `blob:`。
 */

const ORIGIN_ATTRIBUTE = 'data-notice-src'

defineOptions({ name: 'QuillEditor' })

const props = withDefaults(
  defineProps<{
    modelValue?: string
    readonly?: boolean
    showImage?: boolean
    placeholder?: string
    /** 认证取回：页面传入 `api.ts` 的图片入口，避免在编辑器里重复一套传输。 */
    loadImage?: (id: number) => Promise<Blob>
    createObjectUrl?: (blob: Blob) => string
    revokeObjectUrl?: (url: string) => void
  }>(),
  {
    modelValue: '',
    readonly: false,
    showImage: true,
    placeholder: '',
    loadImage: undefined,
    createObjectUrl: undefined,
    revokeObjectUrl: undefined,
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

const hydrator = createNoticeImageHydrator({
  loadImage: (id) => (props.loadImage || fetchNoticeImage)(id),
  createObjectUrl: (blob) => (props.createObjectUrl || URL.createObjectURL)(blob),
  revokeObjectUrl: (url) => (props.revokeObjectUrl || URL.revokeObjectURL)(url),
  originAttribute: ORIGIN_ATTRIBUTE,
})

/**
 * 序列化必须用克隆：编辑器 DOM 里的 `src` 是 `blob:`，服务端只接受同源路径。
 * 克隆上从 `data-notice-src` 回写 `src` 并去掉标记，原 DOM 保持 blob 预览。
 */
function currentHtml(): string {
  if (!quill) return ''
  const clone = quill.root.cloneNode(true) as HTMLElement
  for (const image of clone.querySelectorAll(`img[${ORIGIN_ATTRIBUTE}]`)) {
    const origin = image.getAttribute(ORIGIN_ATTRIBUTE)
    image.removeAttribute(ORIGIN_ATTRIBUTE)
    if (origin) image.setAttribute('src', origin)
  }
  return clone.innerHTML
}

function hydrateImages() {
  if (!quill) return
  hydrator.hydrate(quill.root)
}

function applyValue(value: string) {
  if (!quill || value === currentHtml()) return
  // 旧内容的对象地址随 DOM 一起丢弃，先撤销再重新水合。
  hydrator.revokeAll()
  applyingExternalValue = true
  try {
    quill.clipboard.dangerouslyPasteHTML(value)
  } finally {
    applyingExternalValue = false
  }
  hydrateImages()
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
    emit('update:modelValue', currentHtml())
    // 粘贴/拖入的图片同样走认证取回；hydrate 幂等，已水合的元素不会重复取。
    hydrateImages()
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
  hydrator.dispose()
  quill = null
})

/** 在光标处插入服务端返回的同源图片地址；正文里只保存 URL，不保存 blob。 */
function insertImage(url: string) {
  if (!quill || props.readonly || !url) return
  const range = quill.getSelection(true)
  const index = range ? range.index : quill.getLength()
  quill.insertEmbed(index, 'image', url, 'user')
  quill.setSelection(index + 1, 0, 'silent')
  hydrateImages()
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

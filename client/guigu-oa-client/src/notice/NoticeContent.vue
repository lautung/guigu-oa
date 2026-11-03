<template>
  <!--
    全仓唯一 v-html 渲染面（ADR 0028/0055）：正文在服务端按同源白名单消毒一次，
    库里的就是干净 HTML，客户端不再二次消毒。模板在此集中，ESLint 只对本文件
    关闭 `vue/no-v-html`；`scripts/check-v-html.mjs` 守卫没有第二处。
  -->
  <div ref="root" class="notice-content" v-html="html"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue'

import { fetchNoticeImage } from '@/notice/api'
import { createNoticeImageHydrator } from '@/notice/notice-images'

defineOptions({ name: 'NoticeContent' })

const props = withDefaults(
  defineProps<{
    /** 已消毒正文，原样注入（唯一渲染面）。 */
    html?: string
    /** 可注入的图片取回（#220 预览与单测复用）；默认走带认证头的请求客户端。 */
    loadImage?: (id: number) => Promise<Blob>
    createObjectUrl?: (blob: Blob) => string
    revokeObjectUrl?: (url: string) => void
  }>(),
  {
    html: '',
    loadImage: undefined,
    createObjectUrl: undefined,
    revokeObjectUrl: undefined,
  },
)

const root = ref<HTMLElement>()
const hydrator = createNoticeImageHydrator({
  loadImage: (id) => (props.loadImage || fetchNoticeImage)(id),
  createObjectUrl: (blob) => (props.createObjectUrl || URL.createObjectURL)(blob),
  revokeObjectUrl: (url) => (props.revokeObjectUrl || URL.revokeObjectURL)(url),
})

function hydrate() {
  hydrator.hydrate(root.value)
}

onMounted(hydrate)
onUpdated(hydrate)
watch(
  () => props.html,
  () => hydrator.revokeAll(),
)
onBeforeUnmount(() => hydrator.dispose())
</script>

<style lang="scss" scoped>
.notice-content {
  color: #303133;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 16px 0 8px;
    color: #303133;
    line-height: 1.35;
  }

  :deep(p) {
    margin: 0 0 10px;
  }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 10px;
    padding-left: 22px;
  }

  :deep(blockquote) {
    margin: 0 0 10px;
    padding: 8px 12px;
    color: #606266;
    background: #f5f7fa;
    border-left: 3px solid #dcdfe6;
  }

  :deep(pre) {
    margin: 0 0 10px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    overflow-x: auto;
  }

  :deep(code) {
    font-family: Menlo, Consolas, monospace;
    font-size: 13px;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;
  }

  :deep(a:hover) {
    text-decoration: underline;
  }

  :deep(img) {
    max-width: 100%;
    height: auto;
  }
}
</style>

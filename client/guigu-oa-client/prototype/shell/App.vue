<template>
  <el-config-provider :size="ui.size" :locale="zhCn">
    <div class="proto-app">
      <component
        :is="shell"
        :page="ui.page"
        :narrow="narrow"
        :state="ui.state"
        :show-breadcrumb="ui.showBreadcrumb"
        :show-fullscreen="ui.showFullscreen"
        :show-search="ui.showSearch"
      />
      <prototype-bar
        :variant-key="variantKey"
        :variant-name="currentVariant.name"
        :ui="ui"
        @prev="cycleVariant(-1)"
        @next="cycleVariant(1)"
        @set="setUi"
      />
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import PrototypeBar from './PrototypeBar.vue'
import ShellA from './variants/ShellA.vue'
import ShellB from './variants/ShellB.vue'
import ShellC from './variants/ShellC.vue'
import { applyPalette } from './theme'

const variants = [
  { key: 'A', name: '经典后台', comp: ShellA },
  { key: 'B', name: '收件箱优先', comp: ShellB },
  { key: 'C', name: '紧凑指挥台', comp: ShellC },
]

function readVariant() {
  const key = new URLSearchParams(window.location.search).get('variant')
  return variants.some((item) => item.key === key) ? key : 'A'
}

const variantKey = ref(readVariant())
const shell = computed(() => variants.find((item) => item.key === variantKey.value).comp)
const currentVariant = computed(() => variants.find((item) => item.key === variantKey.value))

const params = new URLSearchParams(window.location.search)
const defaults = {
  page: 'workbench',
  state: 'ready',
  theme: 'light',
  size: 'small',
  palette: 'blue',
  narrow: false,
  showBreadcrumb: true,
  showFullscreen: true,
  showSearch: true,
}
const paramNames = {
  page: 'page',
  state: 'state',
  theme: 'theme',
  size: 'size',
  palette: 'palette',
  narrow: 'narrow',
  showBreadcrumb: 'breadcrumb',
  showFullscreen: 'fullscreen',
  showSearch: 'search',
}
const ui = reactive({
  page: ['list', 'instanceList'].includes(params.get('page')) ? 'instanceList' : defaults.page,
  state: ['ready', 'loading', 'empty'].includes(params.get('state'))
    ? params.get('state')
    : defaults.state,
  theme: params.get('theme') === 'dark' ? 'dark' : defaults.theme,
  size: ['small', 'default', 'large'].includes(params.get('size'))
    ? params.get('size')
    : defaults.size,
  palette: ['blue', 'teal', 'indigo'].includes(params.get('palette'))
    ? params.get('palette')
    : defaults.palette,
  narrow: params.get('narrow') === '1',
  showBreadcrumb: params.get('breadcrumb') !== '0',
  showFullscreen: params.get('fullscreen') !== '0',
  showSearch: params.get('search') !== '0',
})

const windowWidth = ref(window.innerWidth)
const narrow = computed(() => ui.narrow || windowWidth.value < 900)

function syncUrl() {
  const url = new URL(window.location.href)
  Object.keys(paramNames).forEach((key) => {
    const value = ui[key]
    if (value === defaults[key]) url.searchParams.delete(paramNames[key])
    else url.searchParams.set(paramNames[key], typeof value === 'boolean' ? (value ? '1' : '0') : value)
  })
  window.history.replaceState(null, '', url)
}

function setUi(key, value) {
  ui[key] = value
  syncUrl()
}

function cycleVariant(step) {
  const index = variants.findIndex((item) => item.key === variantKey.value)
  const next = variants[(index + step + variants.length) % variants.length]
  variantKey.value = next.key
  const url = new URL(window.location.href)
  url.searchParams.set('variant', next.key)
  window.history.replaceState(null, '', url)
}

function handleKey(event) {
  const target = event.target
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) return
  if (event.key === 'ArrowLeft') cycleVariant(-1)
  if (event.key === 'ArrowRight') cycleVariant(1)
}

function handleResize() {
  windowWidth.value = window.innerWidth
}

watch(
  () => ui.theme,
  (theme) => document.documentElement.classList.toggle('dark', theme === 'dark'),
  { immediate: true },
)
watch(() => ui.palette, applyPalette, { immediate: true })

onMounted(() => {
  window.addEventListener('keydown', handleKey)
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKey)
  window.removeEventListener('resize', handleResize)
})
</script>

<style>
.proto-app {
  height: 100%;
}
</style>

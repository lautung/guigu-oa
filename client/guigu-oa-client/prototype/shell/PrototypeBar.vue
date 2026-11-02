<template>
  <div class="proto-bar">
    <button class="proto-arrow" aria-label="上一个变体" @click="emit('prev')">←</button>
    <div class="proto-title">
      <span class="proto-key">{{ variantKey }}</span>
      <span class="proto-name">{{ variantName }}</span>
      <span class="proto-tag">原型</span>
    </div>
    <button class="proto-arrow" aria-label="下一个变体" @click="emit('next')">→</button>
    <span class="proto-sep"></span>
    <el-popover v-model:visible="panelOpen" placement="top-end" :width="304" trigger="click">
      <template #reference>
        <button class="proto-settings">原型设置</button>
      </template>

      <div class="proto-group">
        <div class="proto-group-label">演示页面</div>
        <el-radio-group
          :model-value="ui.page"
          size="small"
          @change="(value) => emit('set', 'page', value)"
        >
          <el-radio-button value="workbench">工作台</el-radio-button>
          <el-radio-button value="instanceList">审批实例列表</el-radio-button>
        </el-radio-group>
      </div>

      <div class="proto-group">
        <div class="proto-group-label">数据状态（工作台与列表）</div>
        <el-radio-group
          :model-value="ui.state"
          size="small"
          @change="(value) => emit('set', 'state', value)"
        >
          <el-radio-button value="ready">正常</el-radio-button>
          <el-radio-button value="loading">加载中</el-radio-button>
          <el-radio-button value="empty">空态</el-radio-button>
        </el-radio-group>
      </div>

      <div class="proto-group">
        <div class="proto-row">
          <span>主题</span>
          <el-radio-group
            :model-value="ui.theme"
            size="small"
            @change="(value) => emit('set', 'theme', value)"
          >
            <el-radio-button value="light">浅色</el-radio-button>
            <el-radio-button value="dark">深色</el-radio-button>
          </el-radio-group>
        </div>
        <div class="proto-row">
          <span>全局尺寸</span>
          <el-radio-group
            :model-value="ui.size"
            size="small"
            @change="(value) => emit('set', 'size', value)"
          >
            <el-radio-button value="small">紧凑</el-radio-button>
            <el-radio-button value="default">默认</el-radio-button>
            <el-radio-button value="large">宽松</el-radio-button>
          </el-radio-group>
        </div>
        <div class="proto-row">
          <span>主色</span>
          <div class="proto-swatches">
            <button
              v-for="palette in palettes"
              :key="palette.key"
              class="proto-swatch"
              :class="{ active: ui.palette === palette.key }"
              :style="{ background: palette.primary }"
              :title="palette.label"
              @click="emit('set', 'palette', palette.key)"
            ></button>
          </div>
        </div>
      </div>

      <div class="proto-group">
        <div class="proto-row">
          <span>窄屏预览（&lt; 900px）</span>
          <el-switch
            :model-value="ui.narrow"
            size="small"
            @change="(value) => emit('set', 'narrow', value)"
          />
        </div>
        <div class="proto-row">
          <span>顶栏面包屑</span>
          <el-switch
            :model-value="ui.showBreadcrumb"
            size="small"
            @change="(value) => emit('set', 'showBreadcrumb', value)"
          />
        </div>
        <div class="proto-row">
          <span>顶栏全屏</span>
          <el-switch
            :model-value="ui.showFullscreen"
            size="small"
            @change="(value) => emit('set', 'showFullscreen', value)"
          />
        </div>
        <div class="proto-row">
          <span>顶栏菜单搜索</span>
          <el-switch
            :model-value="ui.showSearch"
            size="small"
            @change="(value) => emit('set', 'showSearch', value)"
          />
        </div>
        <p class="proto-hint">
          左右方向键也可切换变体。切换变体会重置标签页；标签、列设置等交互只在本页内存中。
        </p>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { palettes } from './theme'

defineProps({
  variantKey: { type: String, required: true },
  variantName: { type: String, required: true },
  ui: { type: Object, required: true },
})

const emit = defineEmits(['prev', 'next', 'set'])
const panelOpen = ref(false)
</script>

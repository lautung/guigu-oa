<template>
  <div v-if="edit" v-loading="state.phase === 'loading'" class="template-set-page">
    <el-steps :active="state.step" finish-status="success" align-center>
      <el-step v-for="title in wizardSteps" :key="title" :title="title" />
    </el-steps>

    <div v-if="state.phase === 'uncertain'" class="result-panel" role="alert">
      <h2>模板保存结果待确认</h2>
      <p>{{ state.error }}。本次提交内容已保留，暂停修改与再次保存。</p>
      <p>原保存仍可能生效；列表暂未找到目标内容也不能证明保存失败。</p>
      <el-button @click="edit.openList()">另开模板列表核实</el-button>
      <el-button type="warning" @click="edit.endEditing()">结束本次编辑</el-button>
    </div>
    <div v-else-if="state.error" class="result-panel" role="alert">
      <p>{{ state.error }}</p>
      <el-button v-if="state.phase === 'load-error'" @click="edit.reload()">重试读取</el-button>
      <el-button
        v-if="state.phase === 'design-error'"
        :disabled="state.capturing"
        @click="edit.retryDesigner()"
        >重试表单设计</el-button
      >
    </div>
    <p v-if="state.phase === 'saving'" role="status">
      正在保存审批模板，本次提交内容已固定。离开不撤销已提交的保存。
    </p>

    <div class="tools">
      <el-button v-if="state.step > 0" :disabled="!state.canNavigateSteps" @click="edit.previous()"
        >上一步</el-button
      >
      <el-button
        type="primary"
        :disabled="state.step === 2 ? !state.canSave : !state.canNavigateSteps"
        @click="edit.next()"
      >
        {{ state.step === 2 ? '提交保存' : '下一步' }}
      </el-button>
      <el-button @click="edit.back()">返回</el-button>
    </div>

    <div v-show="state.step === 0" class="step-pane">
      <el-form label-width="100px" :disabled="!state.canEdit">
        <el-form-item label="审批类型" for="template-type">
          <el-select
            id="template-type"
            :model-value="state.form.processTypeId"
            placeholder="请选择审批类型"
            @update:model-value="edit.updateBasic({ processTypeId: $event })"
          >
            <el-option
              v-for="item in state.types"
              :key="item.id"
              :label="item.name"
              :value="item.id ?? ''"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批名称" for="template-name">
          <el-input
            id="template-name"
            :model-value="state.form.name"
            @update:model-value="edit.updateBasic({ name: $event })"
          />
        </el-form-item>
        <el-form-item label="审批图标" for="template-icon">
          <el-select
            id="template-icon"
            :model-value="state.form.iconUrl"
            placeholder="请选择审批图标"
            @update:model-value="edit.updateBasic({ iconUrl: $event })"
          >
            <el-option
              v-for="item in iconList"
              :key="item.tag + item.iconUrl"
              :label="item.tag"
              :value="item.iconUrl"
            >
              <img :src="item.iconUrl" class="template-icon" alt="" />
              <span>{{ item.tag }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="描述" for="template-description">
          <el-input
            id="template-description"
            :model-value="state.form.description"
            @update:model-value="edit.updateBasic({ description: $event })"
          />
        </el-form-item>
      </el-form>
    </div>

    <div v-show="state.step === 1" class="step-pane">
      <div v-show="!inspectionDesign" :inert="!state.canEdit">
        <fc-designer ref="designer" height="520px" />
      </div>
      <div v-if="inspectionDesign" class="submitted-form-preview" aria-label="本次提交的表单设计">
        <h3>本次提交的表单设计</h3>
        <form-create :rule="inspectionDesign.rule" :option="inspectionDesign.option" />
      </div>
    </div>

    <div v-show="state.step === 2" class="step-pane">
      <label class="zip-label" for="template-zip">选择流程 zip</label>
      <input
        id="template-zip"
        type="file"
        accept=".zip,application/zip"
        :disabled="!state.canEdit"
        @change="selectZip"
      />
      <p>只能上传 zip 压缩文件，且不超过 2048kb。</p>
      <p v-if="state.zipStatus === 'uploading'" role="status">
        流程 zip 正在上传，完成前暂停保存；可重新选择文件。
      </p>
      <div v-if="state.uploadError" role="alert">
        <p>流程 zip 上传失败：{{ state.uploadError }}</p>
        <el-button :disabled="!state.canEdit" @click="edit.retryUpload()">重试上传</el-button>
      </div>
      <p v-if="state.zip" role="status">
        当前采用的流程 zip：{{ state.zip.name }}（{{ state.zip.path }}）
      </p>
      <p v-else-if="state.zipStatus === 'empty'">尚未采用流程 zip，请上传或明确恢复旧 zip。</p>
      <el-button
        v-if="state.zip || state.zipStatus === 'uploading' || state.zipStatus === 'error'"
        :disabled="!state.canEdit"
        @click="edit.removeZip()"
        >移除当前选择</el-button
      >
      <el-button v-if="state.canRestoreZip" @click="edit.restoreZip()">恢复旧 zip</el-button>
      <p>上传成功仅表示流程 zip 已存储。提交保存后模板仍为未发布，另行发布成功后才可发起审批。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTemplateEditor, type TemplateEditorOpening } from '@/config/template-editor'
import { TEMPLATE_ICONS } from '@/config/process-template'
import { resolveClientSurface } from '@/surface/client-surface'
import { useUserStore } from '@/store/modules/user'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const designer = ref()
const edit = shallowRef<TemplateEditorOpening>()
const state = computed(() => edit.value!.state)
const inspectionDesign = computed(() => edit.value?.state.inspectionDesign)
const wizardSteps = ['基本设置', '表单设置', '流程设置']
const iconList = TEMPLATE_ICONS
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')
const editor = createTemplateEditor({
  recognition,
  request,
  occupancy: () =>
    resolveClientSurface({
      path: route.path,
      hasToken: !!userStore.token,
      menus: userStore.menus,
      viewportWidth: window.innerWidth,
    }).occupancy,
  designer: async () => {
    await nextTick()
    if (!designer.value) throw new Error('表单设计器暂不可用，请重试或返回')
    return designer.value
  },
  confirm: async (message, kind) => {
    const ending = kind === 'end-editing'
    try {
      await ElMessageBox.confirm(message, ending ? '结束本次编辑' : '离开审批模板编辑', {
        type: 'warning',
        confirmButtonText: ending ? '结束本次编辑' : '确认离开',
        cancelButtonText: ending ? '保留本次编辑' : '继续编辑',
      })
      return true
    } catch {
      return false
    }
  },
  dismissConfirm: () => {
    ElMessageBox.close()
  },
  notify: (message, level) => {
    ElMessage({ message, type: level })
  },
  navigate: (path) => {
    void router.push(path)
  },
  replace: (path) => {
    void router.replace(path)
  },
  openList: (path) => {
    window.open(router.resolve(path).href, '_blank', 'noopener,noreferrer')
  },
})
function open() {
  const id = route.query.id
  edit.value = editor.open(typeof id === 'string' ? id : undefined)
}
function selectZip(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void edit.value?.selectZip(file)
}
function environmentChanged() {
  void edit.value?.environmentChanged()
}
onBeforeRouteLeave(() => edit.value?.requestLeave() ?? true)
onBeforeRouteUpdate(async (to, from) => {
  if (to.query.id === from.query.id) return true
  return (await edit.value?.requestLeave()) ?? true
})
watch(
  () => route.query.id,
  () => {
    if (route.path === '/processSet/templateSet') open()
  },
)
watch(() => [userStore.token, userStore.menus] as const, environmentChanged, { deep: true })
onMounted(() => {
  open()
  window.addEventListener('resize', environmentChanged)
  window.addEventListener('focus', environmentChanged)
})
onBeforeUnmount(() => {
  edit.value?.dispose()
  ElMessageBox.close()
  window.removeEventListener('resize', environmentChanged)
  window.removeEventListener('focus', environmentChanged)
})
</script>

<style lang="scss" scoped>
.template-set-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.tools {
  margin: 20px 0;
}
.step-pane {
  margin-top: 12px;
}
.result-panel {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #e6a23c;
  border-radius: 6px;
}
.result-panel h2 {
  font-size: 18px;
  margin-top: 0;
}
.zip-label {
  display: block;
  margin-bottom: 8px;
}
.template-icon {
  width: 30px;
  height: 30px;
  vertical-align: middle;
  margin-right: 8px;
}
</style>

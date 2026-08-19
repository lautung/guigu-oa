<template>
  <div class="template-set-page">
    <el-steps :active="stepIndex" finish-status="success" align-center>
      <el-step v-for="title in wizardSteps" :key="title" :title="title" />
    </el-steps>

    <div class="tools">
      <el-button v-if="stepIndex > 0" @click="prev">上一步</el-button>
      <el-button type="primary" :disabled="saving" @click="next">{{ primaryAction }}</el-button>
      <el-button @click="back">返回</el-button>
    </div>

    <div v-show="stepIndex === 0" class="step-pane">
      <el-form label-width="100px">
        <el-form-item label="审批类型">
          <el-select v-model="form.processTypeId" placeholder="请选择审批类型">
            <el-option
              v-for="item in processTypeList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="审批图标">
          <el-select v-model="form.iconUrl" placeholder="请选择审批图标">
            <el-option
              v-for="item in iconList"
              :key="item.tag + item.iconUrl"
              :label="item.tag"
              :value="item.iconUrl"
            >
              <img :src="item.iconUrl" class="template-icon" alt="">
              <span>{{ item.tag }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
      </el-form>
    </div>

    <div v-show="stepIndex === 1" class="step-pane">
      <fc-designer ref="designer" height="520px" />
    </div>

    <div v-show="stepIndex === 2" class="step-pane">
      <el-upload
        drag
        :action="uploadUrl"
        :headers="uploadHeaders"
        :multiple="false"
        :file-list="fileList"
        :before-upload="beforeUpload"
        :on-success="onUploadSuccess"
        :on-remove="onUploadRemove"
      >
        <div class="el-upload__text">将流程 zip 文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">只能上传 zip 压缩文件，且不超过 2048kb</div>
        </template>
      </el-upload>
    </div>
  </div>
</template>

<script>
import { getById, saveOrUpdate } from '@/api/processTemplate'
import { findAll as findAllProcessTypes } from '@/api/processType'
import {
  applyFormDesign,
  applyUploadResult,
  emptyForm,
  presentDesignerState,
  presentForm,
  saveValidationError,
  TEMPLATE_ICONS,
  uploadProcessDefinitionUrl,
  wizardPrimaryAction,
  WIZARD_STEPS,
  zipUploadError
} from '@/config/process-template'
import { getToken } from '@/utils/auth'

export default {
  name: 'ProcessTemplateSet',
  data() {
    return {
      stepIndex: 0,
      saving: false,
      processTypeList: [],
      form: emptyForm(),
      iconList: TEMPLATE_ICONS,
      wizardSteps: WIZARD_STEPS,
      uploadUrl: uploadProcessDefinitionUrl(),
      fileList: []
    }
  },
  computed: {
    primaryAction() {
      return wizardPrimaryAction(this.stepIndex)
    },
    uploadHeaders() {
      return { token: getToken() }
    }
  },
  created() {
    this.fetchProcessTypes()
    const id = this.$route.query.id
    if (id) {
      this.fetchById(id)
    }
  },
  methods: {
    fetchProcessTypes() {
      findAllProcessTypes().then(response => {
        this.processTypeList = response.data || []
      })
    },
    fetchById(id) {
      getById(id).then(response => {
        this.form = presentForm(response.data || {})
        this.fileList = this.form.processDefinitionPath
          ? [{ name: this.form.processDefinitionPath, url: this.form.processDefinitionPath }]
          : []
        this.$nextTick(() => this.restoreDesigner())
      })
    },
    restoreDesigner() {
      if (!this.$refs.designer) {
        return
      }
      const design = presentDesignerState(this.form)
      this.$refs.designer.setRule(design.rule)
      this.$refs.designer.setOption(design.option)
    },
    captureDesigner() {
      if (!this.$refs.designer) {
        return
      }
      this.form = applyFormDesign(this.form, {
        rule: this.$refs.designer.getRule(),
        option: this.$refs.designer.getOption()
      })
    },
    prev() {
      if (this.stepIndex > 0) {
        this.stepIndex -= 1
      }
    },
    next() {
      if (this.stepIndex === 0 && !this.validateBasic()) {
        return
      }
      if (this.stepIndex === 1) {
        this.captureDesigner()
      }
      if (this.stepIndex === 2) {
        this.save()
        return
      }
      this.stepIndex += 1
    },
    validateBasic() {
      if (!this.form.processTypeId || !this.form.name) {
        this.$message.error('请填写审批类型和名称')
        return false
      }
      return true
    },
    beforeUpload(file) {
      const error = zipUploadError(file)
      if (error) {
        this.$message.error(error)
        return false
      }
      return true
    },
    onUploadSuccess(res) {
      if (!res || res.code !== 200) {
        this.$message.error((res && res.message) || '上传失败')
        return
      }
      this.form = applyUploadResult(this.form, res.data || {})
      this.fileList = this.form.processDefinitionPath
        ? [{ name: this.form.processDefinitionPath, url: this.form.processDefinitionPath }]
        : []
    },
    onUploadRemove() {
      this.form = applyUploadResult(this.form, {
        processDefinitionPath: '',
        processDefinitionKey: ''
      })
      this.fileList = []
    },
    save() {
      const error = saveValidationError(this.form)
      if (error) {
        this.$message.error(error)
        return
      }
      this.saving = true
      saveOrUpdate(this.form).then(response => {
        this.$message.success(response.message || '保存成功')
        this.back()
      }).finally(() => {
        this.saving = false
      })
    },
    back() {
      this.$router.push('/processSet/processTemplate')
    }
  }
}
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

.template-icon {
  width: 30px;
  height: 30px;
  vertical-align: middle;
  margin-right: 8px;
}
</style>

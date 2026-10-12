<template>
  <div v-loading="loading" class="apply-page">
    <el-button type="text" @click="goBack">返回</el-button>
    <h2 class="page-title">{{ title }}</h2>
    <form-create
      v-if="rule.length"
      v-model="formData"
      :rule="rule"
      :option="option"
      @submit="onSubmit"
    />
    <AttachmentSection
      v-if="attachmentDraftId"
      :draft-id="attachmentDraftId"
      @busy-change="onAttachmentBusyChange"
      @ready-change="onAttachmentReadyChange"
      @attachments-change="onAttachmentsChange"
    />
  </div>
</template>

<script>
import AttachmentSection from '@/components/AttachmentSection.vue'
import { startUp } from '@/api/process'
import { read } from '@/config/process-template'
import { ROUTES } from '@/navigation/constants'
import request from '@/utils/request'
import { clearDraftId, getOrCreateDraftId } from '@/work/attachments'
import {
  START_SUCCESS_MESSAGE,
  buildStartPayload,
  postStartSuccessRoute,
  presentApplyTemplate
} from '@/work/start-flow'

export default {
  name: 'WorkStartApply',
  components: { AttachmentSection },
  data() {
    return {
      loading: false,
      submitting: false,
      template: null,
      rule: [],
      option: {},
      formData: {},
      title: '发起审批',
      attachmentDraftId: '',
      attachmentIds: [],
      attachmentBusy: false,
      attachmentReady: false
    }
  },
  created() {
    this.attachmentDraftId = getOrCreateDraftId(this.$route.params.processTemplateId)
    this.fetchTemplate()
  },
  methods: {
    goBack() {
      this.$router.push(ROUTES.START)
    },
    onAttachmentBusyChange(busy) {
      this.attachmentBusy = busy
    },
    onAttachmentReadyChange(ready) {
      this.attachmentReady = ready
    },
    onAttachmentsChange(ids) {
      this.attachmentIds = ids
    },
    fetchTemplate() {
      this.loading = true
      read(this.$route.params.processTemplateId, request).then(template => {
        const presented = presentApplyTemplate(template)
        if (!presented.ok) {
          this.$router.replace(presented.redirectTo)
          return
        }
        this.template = presented
        this.title = presented.name || '发起审批'
        this.rule = presented.rule
        this.option = presented.option
      }).finally(() => {
        this.loading = false
      })
    },
    onSubmit(formData) {
      if (this.submitting) {
        return
      }
      if (this.attachmentBusy) {
        this.$message.warning('请等待附件上传完成')
        return
      }
      if (!this.attachmentReady) {
        this.$message.warning('请等待附件读取完成；若读取失败，请重试')
        return
      }
      this.submitting = true
      const payload = buildStartPayload({
        template: this.template,
        formData,
        rule: this.rule,
        attachmentDraftId: this.attachmentDraftId,
        attachmentIds: this.attachmentIds
      })
      startUp(payload).then(() => {
        clearDraftId(this.$route.params.processTemplateId)
        this.$message.success(START_SUCCESS_MESSAGE)
        this.$router.push(postStartSuccessRoute())
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.apply-page {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #303133;
}

@media (max-width: 768px) {
  .apply-page {
    padding: 12px;
    border-radius: 0;
  }

  .page-title {
    font-size: 18px;
  }
}
</style>

<template>
  <div v-loading="loading" class="apply-page">
    <el-button type="text" @click="goBack">返回</el-button>
    <h2 class="page-title">{{ title }}</h2>
    <div class="carbon-copy-field">
      <label for="carbon-copy-users">抄送给</label>
      <el-select
        id="carbon-copy-users"
        v-model="ccUserIds"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        placeholder="选择知情人（可不选）"
        class="carbon-copy-select"
      >
        <el-option
          v-for="recipient in ccRecipients"
          :key="recipient.id"
          :label="recipient.name"
          :value="recipient.id"
        />
      </el-select>
    </div>
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

<script lang="ts">
import { defineComponent } from 'vue'

import AttachmentSection from '@/components/AttachmentSection.vue'
import { findCarbonCopyRecipients, type CarbonCopyRecipient } from '@/api/carbon-copy'
import { startUp } from '@/api/process'
import { read } from '@/config/process-template'
import { ROUTES } from '@/navigation/constants'
import request from '@/utils/request'
import { clearDraftId, getOrCreateDraftId } from '@/work/attachments'
import {
  START_SUCCESS_MESSAGE,
  buildStartPayload,
  postStartSuccessRoute,
  presentApplyTemplate,
} from '@/work/start-flow'
import type { ApplyTemplate } from '@/work/start-flow'
import type { EntityId, FormDataRecord, FormDesignerOption, FormRule } from '@/types/domain'

export default defineComponent({
  name: 'WorkStartApply',
  components: { AttachmentSection },
  data() {
    return {
      loading: false,
      submitting: false,
      template: null as Extract<ApplyTemplate, { ok: true }> | null,
      rule: [] as FormRule[],
      option: {} as FormDesignerOption,
      formData: {} as FormDataRecord,
      title: '发起审批',
      attachmentDraftId: '',
      attachmentIds: [] as EntityId[],
      attachmentBusy: false,
      attachmentReady: false,
      ccRecipients: [] as CarbonCopyRecipient[],
      ccUserIds: [] as EntityId[],
    }
  },
  created() {
    const templateId = this.$route.params.processTemplateId
    if (typeof templateId === 'string') {
      this.attachmentDraftId = getOrCreateDraftId(templateId)
      this.fetchTemplate()
      this.fetchCarbonCopyRecipients()
    } else {
      this.goBack()
    }
  },
  methods: {
    goBack() {
      this.$router.push(ROUTES.START)
    },
    onAttachmentBusyChange(busy: boolean) {
      this.attachmentBusy = busy
    },
    onAttachmentReadyChange(ready: boolean) {
      this.attachmentReady = ready
    },
    onAttachmentsChange(ids: EntityId[]) {
      this.attachmentIds = ids
    },
    fetchCarbonCopyRecipients() {
      findCarbonCopyRecipients().then((response) => {
        this.ccRecipients = response.data || []
      })
    },
    fetchTemplate() {
      const templateId = this.$route.params.processTemplateId
      if (typeof templateId !== 'string') return
      this.loading = true
      read(templateId, request)
        .then((template) => {
          const presented = presentApplyTemplate(template)
          if (!presented.ok) {
            this.$router.replace(presented.redirectTo)
            return
          }
          this.template = presented
          this.title = presented.name || '发起审批'
          this.rule = presented.rule
          this.option = presented.option
        })
        .finally(() => {
          this.loading = false
        })
    },
    onSubmit(formData: FormDataRecord) {
      if (this.submitting) {
        return
      }
      if (!this.template) return
      if (this.attachmentBusy) {
        this.$message.warning('请等待附件上传完成')
        return
      }
      if (!this.attachmentReady) {
        this.$message.warning('请等待附件读取完成；若读取失败，请重试')
        return
      }
      this.submitting = true
      const template = this.template
      const payload = buildStartPayload({
        template,
        formData,
        rule: this.rule,
        attachmentDraftId: this.attachmentDraftId,
        attachmentIds: this.attachmentIds,
        ccUserIds: this.ccUserIds,
      })
      startUp(payload)
        .then(() => {
          clearDraftId(template.id)
          this.$message.success(START_SUCCESS_MESSAGE)
          this.$router.push(postStartSuccessRoute())
        })
        .finally(() => {
          this.submitting = false
        })
    },
  },
})
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

.carbon-copy-field {
  display: grid;
  gap: 8px;
  max-width: 560px;
  margin-bottom: 16px;

  label {
    font-size: 14px;
    color: #606266;
  }
}

.carbon-copy-select {
  width: 100%;
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

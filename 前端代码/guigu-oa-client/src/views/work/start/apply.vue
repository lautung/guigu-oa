<template>
  <div v-loading="loading" class="apply-page">
    <el-button type="text" icon="el-icon-arrow-left" @click="goBack">返回</el-button>
    <h2 class="page-title">{{ title }}</h2>
    <form-create
      v-if="rule.length"
      :rule="rule"
      :option="option"
      @submit="onSubmit"
    />
  </div>
</template>

<script>
import { getProcessTemplate, startUp } from '@/api/process'
import {
  START_SUCCESS_MESSAGE,
  buildStartPayload,
  postStartSuccessRoute,
  presentApplyTemplate
} from '@/work/start-flow'

export default {
  name: 'WorkStartApply',
  data() {
    return {
      loading: false,
      submitting: false,
      template: null,
      rule: [],
      option: {},
      title: '发起审批'
    }
  },
  created() {
    this.fetchTemplate()
  },
  methods: {
    goBack() {
      this.$router.push('/work/start')
    },
    fetchTemplate() {
      this.loading = true
      getProcessTemplate(this.$route.params.processTemplateId).then(response => {
        const presented = presentApplyTemplate(response.data || {})
        if (!presented.ok) {
          this.$router.replace(presented.redirectTo)
          return
        }
        this.template = presented
        this.title = presented.name || '发起审批'
        this.rule = presented.rule
        this.option = Object.assign({}, presented.option, {
          form: Object.assign({}, (presented.option && presented.option.form) || {}, {
            labelPosition: 'top'
          })
        })
      }).finally(() => {
        this.loading = false
      })
    },
    onSubmit(formData) {
      if (this.submitting) {
        return
      }
      this.submitting = true
      const payload = buildStartPayload({
        template: this.template,
        formData,
        rule: this.rule
      })
      startUp(payload).then(() => {
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
</style>

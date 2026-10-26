<template>
  <div v-loading="view.loading" class="apply-page">
    <el-button link @click="goBack">返回</el-button>
    <h2 class="page-title">{{ view.template?.name || '发起审批' }}</h2>
    <el-alert v-if="view.error" :title="view.error" type="error" :closable="false" show-icon />
    <el-button v-if="view.error && view.phase === 'editing'" @click="start.reload"
      >重试读取</el-button
    >
    <div v-if="view.template" class="carbon-copy-field">
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
        :disabled="view.locked"
      >
        <el-option
          v-for="recipient in view.recipients"
          :key="recipient.id"
          :label="recipient.name"
          :value="recipient.id"
        />
      </el-select>
    </div>
    <form-create
      v-if="view.template"
      v-model="formData"
      :rule="rule"
      :option="option"
      @submit="start.submit"
    />
    <ApprovalStartAttachments v-if="view.template" :start="start" />
    <section
      v-if="view.phase === 'unconfirmed' || view.phase === 'completed'"
      class="confirmation"
      aria-label="核实发起结果"
    >
      <h3>{{ view.phase === 'completed' ? '本次已发起' : '发起结果待确认' }}</h3>
      <p>
        {{
          view.phase === 'completed'
            ? '请查看已发起，再结束本次内容。'
            : '请先核实已发起。暂未找到对应审批，并不能证明本次发起没有成功。'
        }}
      </p>
      <div class="confirmation-actions">
        <router-link :to="ROUTES.STARTED">查看已发起</router-link>
        <el-button @click="start.finishFoundApproval">已找到，结束本次</el-button>
        <el-button v-if="view.phase === 'unconfirmed'" @click="start.prepareStartAgain"
          >仍未找到，重新发起</el-button
        >
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ROUTES } from '@/navigation/constants'
import { useApprovalStart } from '@/work/approval-start-browser'
import ApprovalStartAttachments from '@/work/ApprovalStartAttachments.vue'
defineOptions({ name: 'WorkStartApply' })
const route = useRoute(),
  router = useRouter()
const start = useApprovalStart(() =>
  typeof route.params.processTemplateId === 'string' ? route.params.processTemplateId : undefined,
)
const { view, formData, ccUserIds, rule, option } = start
function goBack() {
  void router.push(ROUTES.START)
}
</script>
<style scoped>
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
  margin: 16px 0;
}
.carbon-copy-field label {
  font-size: 14px;
  color: #606266;
}
.carbon-copy-select {
  width: 100%;
}
.confirmation {
  margin-top: 20px;
  padding: 16px;
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  border-radius: 8px;
}
.confirmation h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.confirmation p {
  font-size: 13px;
  line-height: 1.6;
}
.confirmation-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.confirmation-actions .el-button {
  margin: 0;
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

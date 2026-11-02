<template>
  <div class="process-template-page">
    <div class="page-header">
      <h2 class="page-title">审批模板</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="name" label="审批名称" />
      <el-table-column label="图标" width="80" align="center">
        <template #default="scope">
          <img v-if="scope.row.iconUrl" :src="scope.row.iconUrl" class="template-icon" alt="" />
        </template>
      </el-table-column>
      <el-table-column prop="processTypeName" label="审批类型" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="状态" width="220">
        <template #default="scope">
          <div>{{ scope.row.statusLabel }}</div>
          <small v-if="scope.row.availabilityMessage" class="availability-message">
            {{ scope.row.availabilityMessage }}
          </small>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作" width="260" align="center">
        <template #default="scope">
          <el-button link @click="show(scope.row)">查看</el-button>
          <el-button v-if="scope.row.canEdit" link @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button v-if="scope.row.canDelete" link @click="remove(scope.row)">删除</el-button>
          <el-button v-if="scope.row.canPublish" link @click="publishTemplate(scope.row)"
            >发布</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :page="view.page"
      :size="view.size"
      :total="view.total"
      @page-change="list.handlePageChange"
      @size-change="list.handleSizeChange"
    />

    <el-dialog v-model="previewVisible" title="查看审批模板" width="520px">
      <h3>基本信息</h3>
      <el-divider />
      <el-form label-width="90px">
        <el-form-item label="审批类型">{{ preview.processTypeName }}</el-form-item>
        <el-form-item label="名称">{{ preview.name }}</el-form-item>
        <el-form-item label="创建时间">{{ preview.createTime }}</el-form-item>
      </el-form>
      <h3>表单信息</h3>
      <el-divider />
      <form-create v-if="previewVisible" :rule="previewRule" :option="previewOption" />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import request from '@/request/request'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  listRequest,
  presentDesignerState,
  presentListRecord,
  publish,
  removeUnpublished,
} from '@/config/process-template'
import { createListPage } from '@/list-page'
import { listPageEffects } from '@/list-page-browser'
import type { NoListQuery } from '@/list-read'
import { templateSetPath } from '@/router/paths'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  FormDesignerOption,
  FormRule,
  ProcessTemplateListItem,
  ProcessTemplateRecord,
} from '@/types/domain'

export default defineComponent({
  name: 'ProcessTemplateList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<ProcessTemplateRecord, ProcessTemplateListItem, NoListQuery>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => listRequest(page, size),
      present: presentListRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
    }
  },
  data() {
    return {
      previewVisible: false,
      preview: {} as ProcessTemplateRecord,
      previewRule: [] as FormRule[],
      previewOption: { submitBtn: false } as FormDesignerOption,
    }
  },
  methods: {
    openCreate() {
      this.$router.push(templateSetPath())
    },
    openEdit(id: string | number | undefined) {
      if (id == null) return
      this.$router.push(templateSetPath(id))
    },
    show(row: ProcessTemplateRecord) {
      this.preview = row
      const design = presentDesignerState(row)
      this.previewRule = design.rule
      this.previewOption = Object.assign({ submitBtn: false }, design.option)
      this.previewVisible = true
    },
    publishTemplate(template: ProcessTemplateRecord) {
      return this.list.runWrite(() => publish(template, request), '发布成功')
    },
    remove(template: ProcessTemplateRecord) {
      return this.list.confirmRemove(
        '此操作将永久删除该审批模板, 是否继续?',
        () => removeUnpublished(template, request),
        '删除成功',
      )
    },
  },
})
</script>

<style lang="scss" scoped>
.process-template-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.template-icon {
  width: 30px;
  height: 30px;
  vertical-align: middle;
}

.availability-message {
  color: #b88230;
}
</style>

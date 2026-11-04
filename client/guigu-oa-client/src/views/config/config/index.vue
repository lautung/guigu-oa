<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">参数设置</h2>
      <div class="page-actions">
        <el-button v-hasPermi="permissions.edit" icon="el-icon-refresh" @click="refreshCache">
          刷新缓存
        </el-button>
        <el-button
          v-hasPermi="permissions.add"
          type="primary"
          icon="el-icon-plus"
          @click="openCreate"
        >
          新建
        </el-button>
      </div>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="参数名称">
        <el-input v-model="configName" placeholder="参数名称" clearable />
      </el-form-item>
      <el-form-item label="参数键">
        <el-input v-model="configKey" placeholder="参数键" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="configName" label="参数名称" min-width="140" />
      <el-table-column prop="configKey" label="参数键" min-width="180" />
      <el-table-column prop="configValue" label="参数值" min-width="140" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="scope">{{ scope.row.valueTypeLabel }}</template>
      </el-table-column>
      <el-table-column label="属性" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.builtin === 1 ? 'warning' : 'info'">
            {{ scope.row.builtinLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" align="center">
        <template #default="scope">
          <el-button v-hasPermi="permissions.edit" link @click="openEdit(scope.row.id)">
            编辑
          </el-button>
          <el-button
            v-if="scope.row.builtin !== 1"
            v-hasPermi="permissions.remove"
            link
            @click="remove(scope.row.id)"
          >
            删除
          </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" data-test="config-name" :disabled="isBuiltin" />
        </el-form-item>
        <el-form-item label="参数键" prop="configKey">
          <el-input v-model="form.configKey" data-test="config-key" :disabled="isBuiltin" />
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" data-test="config-value" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.valueType" :disabled="isBuiltin" style="width: 100%">
            <el-option
              v-for="option in valueTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :disabled="isBuiltin" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="save">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { refreshCache as refreshConfigCache, removeById, saveOrUpdate } from '@/api/sysConfig'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  PARAMS_PERMISSIONS,
  VALUE_TYPE_OPTIONS,
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListRecord,
} from '@/config/params'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, ParamForm, ParamListItem, ParamRecord } from '@/types/domain'
import type { FormInstance } from 'element-plus'

/**
 * 系统管理 > 参数设置（#252，ADR 0062）：键值 CRUD、筛选、手动刷新缓存。
 * 内置项由迁移种入：键/名称/类型/备注在弹窗里只读，只有值可改；删除按钮不出现。
 * 读后状态、分页与写后刷新走 `createListPage`。
 */
export default defineComponent({
  name: 'SysConfigList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<ParamRecord, ParamListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: PARAMS_PERMISSIONS,
      valueTypeOptions: VALUE_TYPE_OPTIONS,
    }
  },
  data() {
    return {
      configName: '',
      configKey: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle(): string {
      return this.form.id ? '编辑参数' : '新建参数'
    },
    isBuiltin(): boolean {
      return this.form.builtin === 1
    },
    rules() {
      return formRules()
    },
  },
  methods: {
    searchObj(): Record<string, string | number> {
      const query: Record<string, string | number> = {}
      if (this.configName) query.configName = this.configName
      if (this.configKey) query.configKey = this.configKey
      return query
    },
    search() {
      this.list.search(this.searchObj())
    },
    reset() {
      this.configName = ''
      this.configKey = ''
      this.search()
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    openEdit(id: EntityId) {
      const record = this.rows.find((row) => String(row.id) === String(id))
      if (!record) return
      this.form = presentForm(record)
      this.dialogVisible = true
    },
    validateForm(): Promise<boolean> {
      const form = this.$refs.formRef as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async save() {
      if (!(await this.validateForm())) return
      this.saving = true
      try {
        const saved = await this.list.runWrite(
          () => saveOrUpdate(this.form as ParamForm),
          '操作成功',
        )
        if (saved) this.dialogVisible = false
      } finally {
        this.saving = false
      }
    },
    refreshCache() {
      return this.list.runWrite(() => refreshConfigCache(), '刷新成功')
    },
    remove(id: EntityId) {
      return this.list.confirmRemove(
        '此操作将永久删除该参数, 是否继续?',
        () => removeById(id),
        '删除成功',
      )
    },
  },
})
</script>

<style lang="scss" scoped>
.config-page {
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

.page-actions {
  display: flex;
  gap: 8px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.filter-form {
  margin-bottom: 12px;
}
</style>

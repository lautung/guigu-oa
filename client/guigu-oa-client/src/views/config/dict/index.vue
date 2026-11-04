<template>
  <div class="config-page dict-page">
    <div class="page-header">
      <h2 class="page-title">字典管理</h2>
      <div class="page-actions">
        <el-button v-hasPermi="permissions.edit" icon="el-icon-refresh" @click="refreshCache">
          刷新缓存
        </el-button>
        <el-button
          v-hasPermi="permissions.add"
          type="primary"
          icon="el-icon-plus"
          @click="openCreateType"
        >
          新建类型
        </el-button>
      </div>
    </div>

    <div class="dict-layout">
      <section class="dict-panel dict-types">
        <header class="panel-head">
          <h3 class="panel-title">字典类型</h3>
        </header>

        <el-form :inline="true" class="filter-form" @submit.prevent="searchTypes">
          <el-form-item label="名称">
            <el-input v-model="typeQuery.dictName" placeholder="字典名称" clearable />
          </el-form-item>
          <el-form-item label="类型">
            <el-input v-model="typeQuery.dictType" placeholder="类型编码" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchTypes">搜索</el-button>
            <el-button @click="resetTypes">重置</el-button>
          </el-form-item>
        </el-form>

        <ListLoadError :status="typeView.status" :error="typeView.error" @retry="typeList.retry" />

        <el-table
          v-loading="typeView.status === 'loading'"
          :data="typeRows"
          :empty-text="typeEmptyText"
          row-key="id"
          highlight-current-row
          stripe
          border
          @row-click="selectType"
        >
          <el-table-column prop="dictName" label="名称" min-width="130" />
          <el-table-column prop="dictType" label="类型" min-width="150" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
                {{ scope.row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="scope">
              <el-button
                v-hasPermi="permissions.edit"
                link
                @click.stop="openEditType(scope.row.id)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPermi="permissions.remove"
                link
                type="danger"
                @click.stop="removeType(scope.row.id)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <ListPagination
          :page="typeView.page"
          :size="typeView.size"
          :total="typeView.total"
          @page-change="typeList.handlePageChange"
          @size-change="typeList.handleSizeChange"
        />
      </section>

      <section class="dict-panel dict-data">
        <header class="panel-head">
          <h3 class="panel-title">
            字典数据
            <span v-if="selectedType" class="selected-type">
              {{ selectedType.dictName }}（{{ selectedType.dictType }}）
            </span>
            <span v-else class="selected-type empty">先选择左侧字典类型</span>
          </h3>
          <el-button
            v-if="selectedType"
            v-hasPermi="permissions.add"
            type="primary"
            size="small"
            data-test="dict-data-create"
            @click="openCreateData"
          >
            新建数据
          </el-button>
        </header>

        <el-form v-if="selectedType" :inline="true" class="filter-form" @submit.prevent="searchData">
          <el-form-item label="标签">
            <el-input v-model="dataQuery.dictLabel" placeholder="字典标签" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="dataQuery.status" placeholder="全部" clearable>
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchData">搜索</el-button>
            <el-button @click="resetData">重置</el-button>
          </el-form-item>
        </el-form>

        <ListLoadError :status="dataView.status" :error="dataView.error" @retry="dataList.retry" />

        <el-table
          v-loading="dataView.status === 'loading'"
          :data="dataRows"
          :empty-text="dataEmptyText"
          row-key="id"
          stripe
          border
        >
          <el-table-column prop="dictLabel" label="标签" min-width="120" />
          <el-table-column prop="dictValue" label="值" min-width="100" />
          <el-table-column prop="sortValue" label="排序" width="80" align="center" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
                {{ scope.row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="scope">
              <el-button
                v-hasPermi="permissions.edit"
                link
                @click="openEditData(scope.row.id)"
              >
                编辑
              </el-button>
              <el-button
                v-hasPermi="permissions.remove"
                link
                type="danger"
                @click="removeData(scope.row.id)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <ListPagination
          :page="dataView.page"
          :size="dataView.size"
          :total="dataView.total"
          @page-change="dataList.handlePageChange"
          @size-change="dataList.handleSizeChange"
        />
      </section>
    </div>

    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="520px" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" data-test="dict-type-name" />
        </el-form-item>
        <el-form-item label="类型编码" prop="dictType">
          <el-input v-model="typeForm.dictType" data-test="dict-type-code" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="typeForm.status" style="width: 100%">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="typeDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveType">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dataDialogVisible" :title="dataDialogTitle" width="520px" destroy-on-close>
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="类型">
          <el-input :model-value="dataForm.dictType" disabled data-test="dict-data-type" />
        </el-form-item>
        <el-form-item label="标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" data-test="dict-data-label" />
        </el-form-item>
        <el-form-item label="值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" data-test="dict-data-value" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.sortValue" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="dataForm.status" style="width: 100%">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="dataDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveData">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { refreshCache, removeData, removeType, saveOrUpdateData, saveOrUpdateType } from '@/api/sysDict'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  DICT_PERMISSIONS,
  DICT_STATUS_OPTIONS,
  dataFormRules,
  dataListRequest,
  emptyDataForm,
  emptyTypeForm,
  presentDataForm,
  presentDataRecord,
  presentTypeForm,
  presentTypeRecord,
  typeFormRules,
  typeListRequest,
} from '@/config/dict'
import { clearDictCache } from '@/dict/use-dict'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  DictDataForm,
  DictDataListItem,
  DictDataRecord,
  DictTypeForm,
  DictTypeListItem,
  DictTypeRecord,
  EntityId,
} from '@/types/domain'
import type { FormInstance } from 'element-plus'

/**
 * 系统管理 > 字典管理（#251，ADR 0062）：左类型、右数据两级。
 *
 * 两栏各自一个 `createListPage`：读后状态、分页与写后刷新都走同一 module。
 * 左栏行点击把类型编码作为右栏查询条件；类型删除被服务端拒绝（有数据）时
 * 请求层统一提示 201 文案，列表不刷新。写成功后清空客户端字典缓存，
 * 业务页面下次挂载重新取数。
 */
export default defineComponent({
  name: 'SysDictList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const effects = listPageEffects()

    const typeList = createListPage<DictTypeRecord, DictTypeListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition),
      toRequest: ({ page, size, query }) => typeListRequest(page, size, query),
      present: presentTypeRecord,
      ...effects,
    })
    const dataList = createListPage<DictDataRecord, DictDataListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition),
      toRequest: ({ page, size, query }) => dataListRequest(page, size, query),
      present: presentDataRecord,
      ...effects,
    })
    onBeforeUnmount(() => {
      typeList.release()
      dataList.release()
    })
    return {
      typeList,
      dataList,
      typeView: typeList.view,
      dataView: dataList.view,
      typeRows: typeList.rows,
      dataRows: dataList.rows,
      typeEmptyText: typeList.emptyText,
      dataEmptyText: dataList.emptyText,
      permissions: DICT_PERMISSIONS,
      statusOptions: DICT_STATUS_OPTIONS,
      confirm: effects.confirm,
    }
  },
  data() {
    return {
      typeQuery: { dictName: '', dictType: '' } as Record<string, string>,
      dataQuery: { dictLabel: '', status: '' as number | '' },
      selectedType: null as DictTypeListItem | null,
      typeDialogVisible: false,
      dataDialogVisible: false,
      saving: false,
      typeForm: emptyTypeForm(),
      dataForm: emptyDataForm(),
    }
  },
  computed: {
    typeDialogTitle(): string {
      return this.typeForm.id ? '编辑字典类型' : '新建字典类型'
    },
    dataDialogTitle(): string {
      return this.dataForm.id ? '编辑字典数据' : '新建字典数据'
    },
    typeRules() {
      return typeFormRules()
    },
    dataRules() {
      return dataFormRules()
    },
  },
  methods: {
    searchTypes() {
      const query: Record<string, string | number> = {}
      if (this.typeQuery.dictName) query.dictName = this.typeQuery.dictName
      if (this.typeQuery.dictType) query.dictType = this.typeQuery.dictType
      this.typeList.search(query)
    },
    resetTypes() {
      this.typeQuery = { dictName: '', dictType: '' }
      this.searchTypes()
    },
    selectType(row: unknown) {
      const record = row as DictTypeListItem
      this.selectedType = record
      this.dataQuery = { dictLabel: '', status: '' }
      this.dataList.search({ dictType: record.dictType || '' })
    },
    clearSelection() {
      this.selectedType = null
      this.dataQuery = { dictLabel: '', status: '' }
      this.dataList.search({ dictType: '' })
    },
    searchData() {
      if (!this.selectedType) return
      const query: Record<string, string | number> = {
        dictType: this.selectedType.dictType || '',
      }
      if (this.dataQuery.dictLabel) query.dictLabel = this.dataQuery.dictLabel
      if (this.dataQuery.status !== '') query.status = this.dataQuery.status
      this.dataList.search(query)
    },
    resetData() {
      this.dataQuery = { dictLabel: '', status: '' }
      this.searchData()
    },
    openCreateType() {
      this.typeForm = emptyTypeForm()
      this.typeDialogVisible = true
    },
    openEditType(id: EntityId) {
      const record = this.typeRows.find((row) => String(row.id) === String(id))
      if (!record) return
      this.typeForm = presentTypeForm(record)
      this.typeDialogVisible = true
    },
    openCreateData() {
      if (!this.selectedType) return
      this.dataForm = emptyDataForm(this.selectedType.dictType || '')
      this.dataDialogVisible = true
    },
    openEditData(id: EntityId) {
      const record = this.dataRows.find((row) => String(row.id) === String(id))
      if (!record) return
      this.dataForm = presentDataForm(record)
      this.dataDialogVisible = true
    },
    validate(refName: 'typeFormRef' | 'dataFormRef'): Promise<boolean> {
      const form = this.$refs[refName] as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async saveType() {
      if (!(await this.validate('typeFormRef'))) return
      this.saving = true
      try {
        const saved = await this.typeList.runWrite(
          () => saveOrUpdateType(this.typeForm as DictTypeForm),
          '操作成功',
        )
        if (saved) {
          this.typeDialogVisible = false
          clearDictCache()
          if (this.selectedType) this.clearSelection()
        }
      } finally {
        this.saving = false
      }
    },
    async saveData() {
      if (!(await this.validate('dataFormRef'))) return
      this.saving = true
      try {
        const saved = await this.dataList.runWrite(
          () => saveOrUpdateData(this.dataForm as DictDataForm),
          '操作成功',
        )
        if (saved) {
          this.dataDialogVisible = false
          clearDictCache()
        }
      } finally {
        this.saving = false
      }
    },
    removeType(id: EntityId) {
      return this.typeList.confirmRemove(
        '此操作将永久删除该字典类型, 是否继续?',
        () => removeType(id),
        '删除成功',
      ).then((removed) => {
        if (removed) clearDictCache()
        return removed
      })
    },
    removeData(id: EntityId) {
      return this.dataList.confirmRemove(
        '此操作将永久删除该字典数据, 是否继续?',
        () => removeData(id),
        '删除成功',
      ).then((removed) => {
        if (removed) clearDictCache()
        return removed
      })
    },
    refreshCache() {
      return this.typeList
        .runWrite(() => refreshCache(), '刷新成功')
        .then((refreshed) => {
          if (refreshed) clearDictCache()
          return refreshed
        })
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

.dict-layout {
  display: grid;
  grid-template-columns: minmax(320px, 2fr) minmax(360px, 3fr);
  gap: 20px;
}

.dict-panel {
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.selected-type {
  margin-left: 8px;
  font-size: 13px;
  color: #606266;
  font-weight: normal;
}

.selected-type.empty {
  color: #909399;
}

.filter-form {
  margin-bottom: 8px;
}
</style>

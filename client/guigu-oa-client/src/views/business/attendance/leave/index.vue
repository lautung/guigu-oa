<template>
  <div class="config-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">假期类型与发放</h2>
        <p class="page-hint">
          类型被流水引用后只停用不删除；发放与调整 append-only，发错用负数行冲抵
        </p>
      </div>
      <div class="header-actions">
        <el-button
          v-hasPermi="permissions.typeAdd"
          type="primary"
          size="small"
          @click="openTypeCreate"
          >新建类型</el-button
        >
        <el-button v-hasPermi="permissions.grant" type="success" size="small" @click="openGrant"
          >发放/调整</el-button
        >
      </div>
    </div>

    <!-- 假期类型区块 -->
    <section class="block">
      <div class="block-header">
        <h3 class="block-title">假期类型</h3>
      </div>
      <el-table
        v-loading="typeList.view.status === 'loading'"
        :data="typeRows"
        :empty-text="typeList.emptyText.value"
        stripe
        border
      >
        <el-table-column prop="name" label="类型名称" min-width="120" />
        <el-table-column label="是否需要余额" width="120" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.balanceRequired === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.balanceRequired === 1 ? '需要余额' : '不限量' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加班入账目标" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.overtimeCredit === 1" type="warning" size="small"
              >加班入账</el-tag
            >
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
              {{ leaveTypeStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" align="center" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
            <el-button v-hasPermi="permissions.typeEdit" link @click="openTypeEdit(scope.row)"
              >编辑</el-button
            >
            <el-button v-hasPermi="permissions.typeRemove" link @click="removeType(scope.row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <ListPagination
        :page="typeList.view.page"
        :size="typeList.view.size"
        :total="typeList.view.total"
        @page-change="typeList.handlePageChange"
        @size-change="typeList.handleSizeChange"
      />
    </section>

    <!-- 发放流水区块 -->
    <section class="block">
      <div class="block-header">
        <h3 class="block-title">发放流水</h3>
      </div>
      <el-form :inline="true" class="filter-form" @submit.prevent="searchLedger">
        <el-form-item label="假期类型">
          <el-select v-model="ledgerTypeId" placeholder="全部类型" clearable style="width: 160px">
            <el-option
              v-for="type in typeOptions"
              :key="type.id"
              :label="type.name"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发放日期">
          <el-date-picker
            v-model="ledgerDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchLedger">搜索</el-button>
          <el-button @click="resetLedgerSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <ListLoadError
        :status="ledgerList.view.status"
        :error="ledgerList.view.error"
        @retry="ledgerList.retry"
      />

      <el-table
        v-loading="ledgerList.view.status === 'loading'"
        :data="ledgerRows"
        :empty-text="ledgerList.emptyText.value"
        stripe
        border
      >
        <el-table-column prop="createTime" label="发放时间" width="170" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="leaveTypeName" label="假期类型" width="120" />
        <el-table-column label="条目" width="100" align="center">
          <template #default="scope">{{ scope.row.entryTypeLabel }}</template>
        </el-table-column>
        <el-table-column label="变动小时" width="110" align="right">
          <template #default="scope">
            <span :class="{ 'is-negative': scope.row.negative }">
              {{ scope.row.changeHoursText }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="110" align="center">
          <template #default="scope">{{ scope.row.operatorName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
      </el-table>
      <ListPagination
        :page="ledgerList.view.page"
        :size="ledgerList.view.size"
        :total="ledgerList.view.total"
        @page-change="ledgerList.handlePageChange"
        @size-change="ledgerList.handleSizeChange"
      />
    </section>

    <!-- 类型表单 -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="520px" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="110px">
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="如 年假 / 调休" />
        </el-form-item>
        <el-form-item label="是否需要余额">
          <el-radio-group v-model="typeForm.balanceRequired">
            <el-radio :value="1">需要余额</el-radio>
            <el-radio :value="0">不限量</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="加班入账目标">
          <el-switch v-model="typeForm.overtimeCredit" :active-value="1" :inactive-value="0" />
          <span class="field-hint">全局唯一（仅调休开启）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="typeDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveType">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发放/调整表单 -->
    <el-dialog
      v-model="grantDialogVisible"
      title="发放/调整假期余额"
      width="560px"
      destroy-on-close
    >
      <el-form ref="grantFormRef" :model="grantForm" label-width="110px">
        <el-form-item label="假期类型" required>
          <el-select v-model="grantForm.leaveTypeId" style="width: 100%" placeholder="选择类型">
            <el-option
              v-for="type in grantableOptions"
              :key="type.id"
              :label="type.name"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发放成员" required>
          <el-select
            v-model="grantForm.userIds"
            multiple
            filterable
            remote
            clearable
            placeholder="输入姓名或用户名搜索"
            :remote-method="searchUsers"
            :loading="usersLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.label"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整小时数" required>
          <el-input-number v-model="grantForm.changeHours" :precision="2" :step="0.5" />
          <span class="field-hint">正数发放，负数冲抵错发</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="grantForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="grantDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveGrant">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import {
  fetchLeaveTypeOptions,
  fetchLedgerUsers,
  grantAttendanceLeave,
  removeLeaveType,
  saveOrUpdateLeaveType,
} from '@/api/attendance'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { ElMessage } from 'element-plus'
import {
  ATTENDANCE_PERMISSIONS,
  emptyGrantForm,
  emptyLeaveTypeForm,
  grantableLeaveTypes,
  ledgerListRequest,
  ledgerQueryOf,
  leaveTypeFormRules,
  leaveTypeListRequest,
  leaveTypeStatusLabel,
  presentLeaveTypeForm,
  presentLeaveTypeRecord,
  presentLedgerRecord,
  presentRepairUserOptions,
} from '@/config/attendance'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceLeaveTypeForm,
  AttendanceLeaveTypeListItem,
  AttendanceLeaveTypeRecord,
  AttendanceLedgerGrantForm,
  AttendanceLedgerListItem,
  AttendanceLedgerRecord,
  AttendanceRepairUserOption,
  EntityId,
} from '@/types/domain'
import type { FormInstance } from 'element-plus'

/**
 * 假期类型与发放（#280，ADR 0073）：类型维护（只停用不删、加班入账目标全局唯一）
 * + 多人发放/调整 + 发放流水列表。读端点只判页面 `/business/attendance/leave`；
 * 写动作分别挂 attendance:type:add|edit|remove 与 attendance:ledger:grant。
 * 流水五档数据范围由服务端过滤，客户端不做行级过滤。
 */
export default defineComponent({
  name: 'AttendanceLeavePage',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const typeList = createListPage<
      AttendanceLeaveTypeRecord,
      AttendanceLeaveTypeListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => leaveTypeListRequest(page, size),
      present: presentLeaveTypeRecord,
      ...listPageEffects(),
    })
    const ledgerList = createListPage<
      AttendanceLedgerRecord,
      AttendanceLedgerListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => ledgerListRequest(page, size, query),
      present: presentLedgerRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => {
      typeList.release()
      ledgerList.release()
    })
    return {
      userStore,
      typeList,
      ledgerList,
      typeRows: typeList.rows,
      ledgerRows: ledgerList.rows,
      permissions: ATTENDANCE_PERMISSIONS,
    }
  },
  data() {
    return {
      typeOptions: [] as AttendanceLeaveTypeListItem[],
      userOptions: [] as AttendanceRepairUserOption[],
      usersLoading: false,
      ledgerTypeId: '' as EntityId | '',
      ledgerDateRange: [] as string[],
      typeDialogVisible: false,
      grantDialogVisible: false,
      saving: false,
      typeForm: emptyLeaveTypeForm(),
      grantForm: emptyGrantForm(),
    }
  },
  computed: {
    typeDialogTitle(): string {
      return this.typeForm.id ? '编辑假期类型' : '新建假期类型'
    },
    typeRules() {
      return leaveTypeFormRules()
    },
    grantableOptions(): AttendanceLeaveTypeListItem[] {
      return grantableLeaveTypes(this.typeOptions)
    },
  },
  mounted() {
    // 列表首读由 createListRead 的 ready: read() 自触发；这里补两个下拉的数据源。
    void this.loadOptions()
  },
  methods: {
    leaveTypeStatusLabel,
    async loadOptions(): Promise<void> {
      const response = await fetchLeaveTypeOptions()
      this.typeOptions = (response.data || []).map(presentLeaveTypeRecord)
    },
    async searchUsers(keyword = ''): Promise<void> {
      this.usersLoading = true
      try {
        const response = await fetchLedgerUsers(keyword)
        this.userOptions = presentRepairUserOptions(response.data || [])
      } catch {
        // 请求层已上屏失败文案；候选为空时可重试。
      } finally {
        this.usersLoading = false
      }
    },
    searchLedger(): void {
      this.ledgerList.search(
        ledgerQueryOf({
          leaveTypeId: this.ledgerTypeId,
          beginDate: this.ledgerDateRange?.[0],
          endDate: this.ledgerDateRange?.[1],
        }),
      )
    },
    resetLedgerSearch(): void {
      this.ledgerTypeId = ''
      this.ledgerDateRange = []
      this.searchLedger()
    },
    openTypeCreate(): void {
      this.typeForm = emptyLeaveTypeForm()
      this.typeDialogVisible = true
    },
    openTypeEdit(row: AttendanceLeaveTypeRecord): void {
      this.typeForm = presentLeaveTypeForm(row)
      this.typeDialogVisible = true
    },
    async validateForm(refName: string): Promise<boolean> {
      const form = this.$refs[refName] as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async saveType(): Promise<void> {
      if (!(await this.validateForm('typeFormRef'))) return
      this.saving = true
      try {
        const saved = await this.typeList.runWrite(
          () => saveOrUpdateLeaveType(this.typeForm as AttendanceLeaveTypeForm),
          '操作成功',
        )
        if (saved) {
          this.typeDialogVisible = false
          await this.loadOptions()
        }
      } finally {
        this.saving = false
      }
    },
    removeType(id: EntityId) {
      return this.typeList
        .confirmRemove(
          '此操作将删除该假期类型（已被流水引用时只能停用）, 是否继续?',
          () => removeLeaveType(id),
          '删除成功',
        )
        .then((removed) => {
          if (removed) void this.loadOptions()
          return removed
        })
    },
    async openGrant(): Promise<void> {
      this.grantForm = emptyGrantForm()
      this.grantDialogVisible = true
      await this.searchUsers('')
    },
    async saveGrant(): Promise<void> {
      if (!this.grantForm.leaveTypeId) {
        ElMessage.warning('假期类型不能为空')
        return
      }
      if (!this.grantForm.userIds.length) {
        ElMessage.warning('发放成员不能为空')
        return
      }
      if (!this.grantForm.changeHours) {
        ElMessage.warning('调整小时数不能为 0')
        return
      }
      this.saving = true
      try {
        const response = await grantAttendanceLeave(this.grantForm as AttendanceLedgerGrantForm)
        const written = response.data == null ? this.grantForm.userIds.length : response.data
        ElMessage.success(`已写入 ${written} 条发放流水`)
        this.grantDialogVisible = false
        this.ledgerList.refresh()
      } finally {
        this.saving = false
      }
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
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.page-hint {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.block {
  margin-bottom: 28px;
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.filter-form {
  margin-bottom: 12px;
}

.field-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.is-negative {
  color: #f56c6c;
  font-weight: 600;
}

@media (max-width: 600px) {
  .config-page {
    padding: 16px;
  }
}
</style>

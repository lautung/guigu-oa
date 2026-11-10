<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">考勤组与班次</h2>
    </div>

    <!-- 班次区块 -->
    <div class="block">
      <div class="block-header">
        <h3 class="block-title">班次</h3>
        <el-button v-hasPermi="permissions.add" type="primary" size="small" @click="openShiftCreate"
          >新建班次</el-button
        >
      </div>
      <el-table v-loading="shiftList.view.status === 'loading'" :data="shiftRows" stripe border>
        <el-table-column prop="name" label="班次名称" />
        <el-table-column label="上班时刻" width="100" align="center">
          <template #default="scope">{{ scope.row.startTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="下班时刻" width="100" align="center">
          <template #default="scope">{{ scope.row.endTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="午休" width="140" align="center">
          <template #default="scope">
            {{ lunchText(scope.row) }}
          </template>
        </el-table-column>
        <el-table-column label="宽限(分钟)" width="100" align="center">
          <template #default="scope">{{ scope.row.graceMinutes }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
            <el-button v-hasPermi="permissions.edit" link @click="openShiftEdit(scope.row.id)"
              >编辑</el-button
            >
            <el-button v-hasPermi="permissions.remove" link @click="removeShift(scope.row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <ListPagination
        :page="shiftList.view.page"
        :size="shiftList.view.size"
        :total="shiftList.view.total"
        @page-change="shiftList.handlePageChange"
        @size-change="shiftList.handleSizeChange"
      />
    </div>

    <!-- 考勤组区块 -->
    <div class="block">
      <div class="block-header">
        <h3 class="block-title">考勤组</h3>
        <el-button v-hasPermi="permissions.add" type="primary" size="small" @click="openGroupCreate"
          >新建考勤组</el-button
        >
      </div>
      <el-form :inline="true" class="filter-form" @submit.prevent="searchGroups">
        <el-form-item label="考勤组名称">
          <el-input v-model="groupKeyword" placeholder="考勤组名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchGroups">搜索</el-button>
          <el-button @click="resetGroupSearch">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="groupList.view.status === 'loading'" :data="groupRows" stripe border>
        <el-table-column prop="name" label="考勤组名称" />
        <el-table-column label="绑定班次" width="140">
          <template #default="scope">{{ scope.row.shiftName || '已删除班次' }}</template>
        </el-table-column>
        <el-table-column label="每周工作日">
          <template #default="scope">{{ workdayLabels(scope.row.workdays) }}</template>
        </el-table-column>
        <el-table-column label="成员数" width="90" align="center">
          <template #default="scope">{{ scope.row.memberCount }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
            <el-button v-hasPermi="permissions.edit" link @click="openGroupEdit(scope.row)"
              >编辑</el-button
            >
            <el-button v-hasPermi="permissions.remove" link @click="removeGroup(scope.row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <ListPagination
        :page="groupList.view.page"
        :size="groupList.view.size"
        :total="groupList.view.total"
        @page-change="groupList.handlePageChange"
        @size-change="groupList.handleSizeChange"
      />
    </div>

    <!-- 节假日区块 -->
    <div class="block">
      <div class="block-header">
        <h3 class="block-title">节假日</h3>
        <el-button
          v-hasPermi="permissions.add"
          type="primary"
          size="small"
          @click="openHolidayCreate"
          >新建节假日</el-button
        >
      </div>
      <el-table v-loading="holidayList.view.status === 'loading'" :data="holidayRows" stripe border>
        <el-table-column prop="holidayDate" label="日期" width="130" align="center" />
        <el-table-column prop="name" label="名称" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.dayType === 1 ? 'info' : 'warning'">
              {{ holidayDayTypeLabel(scope.row.dayType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="scope">
            <el-button v-hasPermi="permissions.edit" link @click="openHolidayEdit(scope.row.id)"
              >编辑</el-button
            >
            <el-button v-hasPermi="permissions.remove" link @click="removeHoliday(scope.row.id)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <ListPagination
        :page="holidayList.view.page"
        :size="holidayList.view.size"
        :total="holidayList.view.total"
        @page-change="holidayList.handlePageChange"
        @size-change="holidayList.handleSizeChange"
      />
    </div>

    <!-- 班次表单 -->
    <el-dialog
      v-model="shiftDialogVisible"
      :title="shiftDialogTitle"
      width="520px"
      destroy-on-close
    >
      <el-form ref="shiftFormRef" :model="shiftForm" :rules="shiftRules" label-width="90px">
        <el-form-item label="班次名称" prop="name">
          <el-input v-model="shiftForm.name" />
        </el-form-item>
        <el-form-item label="上班时刻" prop="startTime">
          <el-time-picker
            v-model="shiftForm.startTime"
            value-format="HH:mm:ss"
            placeholder="上班时刻"
          />
        </el-form-item>
        <el-form-item label="下班时刻" prop="endTime">
          <el-time-picker
            v-model="shiftForm.endTime"
            value-format="HH:mm:ss"
            placeholder="下班时刻"
          />
        </el-form-item>
        <el-form-item label="午休起止">
          <el-time-picker
            v-model="shiftForm.lunchStart"
            value-format="HH:mm:ss"
            placeholder="午休开始"
          />
          <span class="time-sep">至</span>
          <el-time-picker
            v-model="shiftForm.lunchEnd"
            value-format="HH:mm:ss"
            placeholder="午休结束"
          />
        </el-form-item>
        <el-form-item label="宽限分钟">
          <el-input-number v-model="shiftForm.graceMinutes" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="shiftForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="shiftDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveShift">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 考勤组表单 -->
    <el-dialog
      v-model="groupDialogVisible"
      :title="groupDialogTitle"
      width="560px"
      destroy-on-close
    >
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="90px">
        <el-form-item label="考勤组名称" prop="name">
          <el-input v-model="groupForm.name" />
        </el-form-item>
        <el-form-item label="绑定班次" prop="shiftId">
          <el-select v-model="groupForm.shiftId" style="width: 100%" placeholder="选择班次">
            <el-option
              v-for="shift in shiftOptions"
              :key="shift.id"
              :label="shiftLabel(shift)"
              :value="shift.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="每周工作日">
          <el-checkbox-group v-model="groupForm.workdays">
            <el-checkbox v-for="option in workdayOptions" :key="option.value" :label="option.value">
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="考勤成员">
          <el-select
            v-model="groupForm.memberUserIds"
            multiple
            filterable
            style="width: 100%"
            placeholder="选择成员（一人只能加入一个考勤组）"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.label"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="groupForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="groupDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveGroup">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 节假日表单 -->
    <el-dialog
      v-model="holidayDialogVisible"
      :title="holidayDialogTitle"
      width="480px"
      destroy-on-close
    >
      <el-form ref="holidayFormRef" :model="holidayForm" :rules="holidayRules" label-width="90px">
        <el-form-item label="日期" prop="holidayDate">
          <el-date-picker
            v-model="holidayForm.holidayDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="holidayForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="holidayForm.dayType" style="width: 100%">
            <el-option
              v-for="option in holidayDayTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="holidayDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="saveHoliday">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import {
  removeGroup,
  removeHoliday,
  removeShift,
  saveOrUpdateGroup,
  saveOrUpdateHoliday,
  saveOrUpdateShift,
} from '@/api/attendance'
import ListPagination from '@/components/ListPagination.vue'
import { ElMessage } from 'element-plus'
import {
  ATTENDANCE_PERMISSIONS,
  HOLIDAY_DAY_TYPE_OPTIONS,
  WORKDAY_OPTIONS,
  emptyGroupForm,
  emptyHolidayForm,
  emptyShiftForm,
  groupFormRules,
  groupListRequest,
  groupMembersRequest,
  holidayDayTypeLabel,
  holidayFormRules,
  holidayListRequest,
  presentGroupForm,
  presentGroupRecord,
  presentHolidayForm,
  presentHolidayRecord,
  presentMemberIds,
  presentShiftForm,
  presentShiftRecord,
  presentUserOptions,
  shiftFormRules,
  shiftListRequest,
  shiftOptionsRequest,
  userOptionsRequest,
  validateShiftLunch,
  workdayLabels,
} from '@/config/attendance'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceGroupForm,
  AttendanceGroupRecord,
  AttendanceGroupListItem,
  AttendanceHolidayForm,
  AttendanceHolidayListItem,
  AttendanceHolidayRecord,
  AttendanceMemberRecord,
  AttendanceShiftForm,
  AttendanceShiftListItem,
  AttendanceShiftRecord,
  EntityId,
  UserOption,
} from '@/types/domain'
import type { FormInstance } from 'element-plus'

/**
 * 考勤组与班次（#278，ADR 0073）：班次、考勤组（含成员直接选人）与全局节假日
 * 三个区块，共用一套页面授予与 attendance:group:add|edit|remove 三个按钮点。
 * 成员候选走本页自己的只读端点（不依赖用户管理页权限）。
 */
export default defineComponent({
  name: 'AttendanceGroupPage',
  components: { ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const shiftList = createListPage<
      AttendanceShiftRecord,
      AttendanceShiftListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => shiftListRequest(page, size),
      present: presentShiftRecord,
      ...listPageEffects(),
    })
    const groupList = createListPage<
      AttendanceGroupRecord,
      AttendanceGroupListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => groupListRequest(page, size, query),
      present: presentGroupRecord,
      ...listPageEffects(),
    })
    const holidayList = createListPage<
      AttendanceHolidayRecord,
      AttendanceHolidayListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => holidayListRequest(page, size),
      present: presentHolidayRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => {
      shiftList.release()
      groupList.release()
      holidayList.release()
    })
    return {
      userStore,
      shiftList,
      groupList,
      holidayList,
      shiftRows: shiftList.rows,
      groupRows: groupList.rows,
      holidayRows: holidayList.rows,
      permissions: ATTENDANCE_PERMISSIONS,
      workdayOptions: WORKDAY_OPTIONS,
      holidayDayTypeOptions: HOLIDAY_DAY_TYPE_OPTIONS,
    }
  },
  data() {
    return {
      groupKeyword: '',
      shiftOptions: [] as AttendanceShiftListItem[],
      userOptions: [] as UserOption[],
      shiftDialogVisible: false,
      groupDialogVisible: false,
      holidayDialogVisible: false,
      saving: false,
      shiftForm: emptyShiftForm(),
      groupForm: emptyGroupForm(),
      holidayForm: emptyHolidayForm(),
    }
  },
  computed: {
    shiftDialogTitle() {
      return this.shiftForm.id ? '编辑班次' : '新建班次'
    },
    groupDialogTitle() {
      return this.groupForm.id ? '编辑考勤组' : '新建考勤组'
    },
    holidayDialogTitle() {
      return this.holidayForm.id ? '编辑节假日' : '新建节假日'
    },
    shiftRules() {
      return shiftFormRules()
    },
    groupRules() {
      return groupFormRules()
    },
    holidayRules() {
      return holidayFormRules()
    },
  },
  mounted() {
    // 列表首读由 createListRead 的 ready: read() 自触发；这里只补两个下拉的数据源。
    this.loadShiftOptions()
    this.loadUserOptions()
  },
  methods: {
    workdayLabels,
    holidayDayTypeLabel,
    lunchText(row: AttendanceShiftRecord): string {
      if (row.lunchStart && row.lunchEnd)
        return `${row.lunchStart.slice(0, 5)} - ${row.lunchEnd.slice(0, 5)}`
      return '无'
    },
    shiftLabel(shift: AttendanceShiftListItem): string {
      return `${shift.name}（${shift.startTime || ''}-${shift.endTime || ''}）`
    },
    async loadShiftOptions() {
      // 一次性读：班次选项是小配置数据，不需要快照读的状态机。
      const scope = openRequestScope(this.userStore.recognition!, { notifyNormalErrors: false })
      const response = await scope.request<AttendanceShiftRecord[]>(shiftOptionsRequest())
      this.shiftOptions = (response.data || []).map((record) => presentShiftRecord(record))
    },
    async loadUserOptions() {
      const scope = openRequestScope(this.userStore.recognition!, { notifyNormalErrors: false })
      const response = await scope.request<UserOption[]>(userOptionsRequest())
      this.userOptions = presentUserOptions(response.data || [])
    },
    searchGroups() {
      this.groupList.search(this.groupKeyword ? { name: this.groupKeyword } : {})
    },
    resetGroupSearch() {
      this.groupKeyword = ''
      this.searchGroups()
    },
    // ---- 班次 ----
    openShiftCreate() {
      this.shiftForm = emptyShiftForm()
      this.shiftDialogVisible = true
    },
    openShiftEdit(id: EntityId) {
      const record = this.shiftRows.find((row) => String(row.id) === String(id))
      if (!record) return
      this.shiftForm = presentShiftForm(record)
      this.shiftDialogVisible = true
    },
    async validateForm(refName: string): Promise<boolean> {
      const form = this.$refs[refName] as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async saveShift() {
      if (!(await this.validateForm('shiftFormRef'))) return
      const lunchProblem = validateShiftLunch(this.shiftForm as AttendanceShiftForm)
      if (lunchProblem) {
        ElMessage.warning(lunchProblem)
        return
      }
      this.saving = true
      try {
        const saved = await this.shiftList.runWrite(
          () => saveOrUpdateShift(this.shiftForm as AttendanceShiftForm),
          '操作成功',
        )
        if (saved) {
          this.shiftDialogVisible = false
          await this.loadShiftOptions()
        }
      } finally {
        this.saving = false
      }
    },
    removeShift(id: EntityId) {
      return this.shiftList
        .confirmRemove('此操作将永久删除该班次, 是否继续?', () => removeShift(id), '删除成功')
        .then((removed) => {
          if (removed) this.loadShiftOptions()
          return removed
        })
    },
    // ---- 考勤组 ----
    openGroupCreate() {
      this.groupForm = emptyGroupForm()
      this.groupDialogVisible = true
    },
    async openGroupEdit(row: AttendanceGroupRecord) {
      // 编辑回读成员：列表行只有成员数，点编辑时按组拉一次成员 id。
      const record = row as AttendanceGroupListItem
      const scope = openRequestScope(this.userStore.recognition!, { notifyNormalErrors: false })
      const response = await scope.request<AttendanceMemberRecord[]>(
        groupMembersRequest(record.id as EntityId),
      )
      this.groupForm = presentGroupForm(record, presentMemberIds(response.data || []))
      this.groupDialogVisible = true
    },
    async saveGroup() {
      if (!(await this.validateForm('groupFormRef'))) return
      this.saving = true
      try {
        const saved = await this.groupList.runWrite(
          () => saveOrUpdateGroup(this.groupForm as AttendanceGroupForm),
          '操作成功',
        )
        if (saved) this.groupDialogVisible = false
      } finally {
        this.saving = false
      }
    },
    removeGroup(id: EntityId) {
      return this.groupList.confirmRemove(
        '此操作将永久删除该考勤组(成员关系一并移除), 是否继续?',
        () => removeGroup(id),
        '删除成功',
      )
    },
    // ---- 节假日 ----
    openHolidayCreate() {
      this.holidayForm = emptyHolidayForm()
      this.holidayDialogVisible = true
    },
    openHolidayEdit(id: EntityId) {
      const record = this.holidayRows.find(
        (row: AttendanceHolidayRecord) => String(row.id) === String(id),
      )
      if (!record) return
      this.holidayForm = presentHolidayForm(record)
      this.holidayDialogVisible = true
    },
    async saveHoliday() {
      if (!(await this.validateForm('holidayFormRef'))) return
      this.saving = true
      try {
        const saved = await this.holidayList.runWrite(
          () => saveOrUpdateHoliday(this.holidayForm as AttendanceHolidayForm),
          '操作成功',
        )
        if (saved) this.holidayDialogVisible = false
      } finally {
        this.saving = false
      }
    },
    removeHoliday(id: EntityId) {
      return this.holidayList.confirmRemove(
        '此操作将永久删除该节假日, 是否继续?',
        () => removeHoliday(id),
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
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.block {
  margin-bottom: 24px;
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

.time-sep {
  margin: 0 8px;
  color: #909399;
}
</style>

<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">补卡处理</h2>
      <p class="page-hint">为指定成员补记上班/下班时间，可只补一个槽位；原打卡记录保留</p>
    </div>

    <section class="repair-card">
      <h3 class="section-title">指定成员与日期</h3>
      <el-form :inline="true" class="repair-form" @submit.prevent="loadDay">
        <el-form-item label="成员">
          <el-select
            v-model="selectedUserId"
            filterable
            remote
            clearable
            placeholder="输入姓名或用户名搜索"
            :remote-method="searchUsers"
            :loading="usersLoading"
            style="width: 260px"
          >
            <el-option v-for="user in users" :key="user.id" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="repairDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :disabled="!selectedUserId || !repairDate || loadingDay"
            @click="loadDay"
            >查询当日</el-button
          >
        </el-form-item>
      </el-form>

      <div v-if="dayView" class="day-summary">
        <span class="day-date">{{ dayView.date }} {{ dayView.weekdayLabel }}</span>
        <el-tag size="small" type="info">{{ dayView.dayTypeLabel }}</el-tag>
        <el-tag size="small" :type="attendanceDayStatusTagType(dayView.status, dayView.pending)">
          {{ dayView.statusLabel }}
        </el-tag>
        <span class="day-current">
          当前上班 {{ dayView.clockInText || '—' }} / 下班 {{ dayView.clockOutText || '—' }}
        </span>
      </div>

      <el-form v-if="dayView" :inline="true" class="time-form" @submit.prevent="save">
        <el-form-item label="上班时间">
          <el-time-picker
            v-model="clockIn"
            value-format="HH:mm:ss"
            format="HH:mm"
            placeholder="不补则留空"
            clearable
          />
        </el-form-item>
        <el-form-item label="下班时间">
          <el-time-picker
            v-model="clockOut"
            value-format="HH:mm:ss"
            format="HH:mm"
            placeholder="不补则留空"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            v-hasPermi="permissions.repair"
            type="primary"
            class="repair-save"
            :loading="saving"
            :disabled="!clockIn && !clockOut"
            @click="save"
            >保存补卡</el-button
          >
        </el-form-item>
      </el-form>
    </section>

    <section class="repair-list">
      <h3 class="section-title">近期打卡流水</h3>
      <el-form :inline="true" class="filter-form" @submit.prevent="search">
        <el-form-item label="姓名">
          <el-input v-model="name" placeholder="姓名或用户名" clearable />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <ListLoadError :status="list.view.status" :error="list.view.error" @retry="list.retry" />

      <el-table
        v-loading="list.view.status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        stripe
        border
      >
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
        <el-table-column prop="punchDate" label="打卡日期" width="120" align="center" />
        <el-table-column label="卡类型" width="100" align="center">
          <template #default="scope">{{ scope.row.slotLabel }}</template>
        </el-table-column>
        <el-table-column prop="punchTime" label="打卡时间" width="170" />
        <el-table-column label="来源" width="100" align="center">
          <template #default="scope">{{ scope.row.sourceLabel }}</template>
        </el-table-column>
        <el-table-column label="补卡人" width="110" align="center">
          <template #default="scope">{{ scope.row.operatorName || '—' }}</template>
        </el-table-column>
      </el-table>

      <ListPagination
        :page="list.view.page"
        :size="list.view.size"
        :total="list.view.total"
        @page-change="list.handlePageChange"
        @size-change="list.handleSizeChange"
      />
    </section>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchRepairDay, fetchRepairUsers, saveAttendanceRepair } from '@/api/attendance'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  ATTENDANCE_PERMISSIONS,
  attendanceDayStatusTagType,
  presentDayStatus,
  presentPunchRecord,
  presentRepairUserOptions,
  punchQueryOf,
  repairListRequest,
} from '@/config/attendance'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceDayStatusRecord,
  AttendanceDayStatusView,
  AttendancePunchListItem,
  AttendancePunchRecord,
  AttendanceRepairUserOption,
  EntityId,
} from '@/types/domain'

/**
 * 后台补卡处理（#281，ADR 0073）：按成员+日期查当前槽位，补上/下班时间（可只补
 * 一槽）。写端点挂 attendance:record:repair，补卡追加 source=2 流水、原网页行保留；
 * 数据范围五档由服务端过滤，客户端不做行级过滤。下方流水列表按数据范围分页，
 * 供 HR 核对已有打卡。
 */
export default defineComponent({
  name: 'AttendanceRepairPage',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<
      AttendancePunchRecord,
      AttendancePunchListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => repairListRequest(page, size, query),
      present: presentPunchRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => list.release())

    const users = ref<AttendanceRepairUserOption[]>([])
    const usersLoading = ref(false)
    const selectedUserId = ref<EntityId | ''>('')
    const repairDate = ref('')
    const dayRecord = ref<AttendanceDayStatusRecord | null>(null)
    const dayView = computed<AttendanceDayStatusView | null>(() =>
      dayRecord.value ? presentDayStatus(dayRecord.value) : null,
    )
    const clockIn = ref('')
    const clockOut = ref('')
    const loadingDay = ref(false)
    const saving = ref(false)

    async function searchUsers(keyword = ''): Promise<void> {
      usersLoading.value = true
      try {
        const response = await fetchRepairUsers(keyword)
        users.value = presentRepairUserOptions(response.data || [])
      } catch {
        // 请求层已上屏失败文案；候选为空时用户可重试。
      } finally {
        usersLoading.value = false
      }
    }

    function applyDay(record: AttendanceDayStatusRecord): void {
      dayRecord.value = record
      clockIn.value = record.clockInTime || ''
      clockOut.value = record.clockOutTime || ''
    }

    async function loadDay(): Promise<void> {
      if (!selectedUserId.value || !repairDate.value || loadingDay.value) return
      loadingDay.value = true
      try {
        const response = await fetchRepairDay(selectedUserId.value, repairDate.value)
        applyDay(response.data || {})
      } catch {
        dayRecord.value = null
      } finally {
        loadingDay.value = false
      }
    }

    async function save(): Promise<void> {
      if (!selectedUserId.value || !repairDate.value || saving.value) return
      if (!clockIn.value && !clockOut.value) {
        ElMessage.warning('至少补一个槽位')
        return
      }
      saving.value = true
      try {
        const response = await saveAttendanceRepair({
          userId: selectedUserId.value,
          repairDate: repairDate.value,
          clockInTime: clockIn.value,
          clockOutTime: clockOut.value,
        })
        applyDay(response.data || {})
        ElMessage.success('补卡成功')
        list.refresh()
      } catch {
        // 请求层已上屏失败文案；表单保持原值可重试。
      } finally {
        saving.value = false
      }
    }

    onMounted(() => void searchUsers(''))

    return {
      list,
      rows: list.rows,
      emptyText: list.emptyText,
      users,
      usersLoading,
      selectedUserId,
      repairDate,
      dayView,
      clockIn,
      clockOut,
      loadingDay,
      saving,
      searchUsers,
      loadDay,
      save,
      attendanceDayStatusTagType,
      permissions: ATTENDANCE_PERMISSIONS,
    }
  },
  data() {
    return {
      name: '',
      dateRange: [] as string[],
    }
  },
  methods: {
    search() {
      this.list.search(
        punchQueryOf({
          name: this.name,
          beginDate: this.dateRange?.[0],
          endDate: this.dateRange?.[1],
        }),
      )
    },
    reset() {
      this.name = ''
      this.dateRange = []
      this.search()
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

.page-hint {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.repair-card {
  padding: 16px;
  margin-bottom: 24px;
  background: #f5f7fa;
  border-radius: 8px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #303133;
}

.repair-form {
  margin-bottom: 0;
}

.day-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 10px 0 4px;
  color: #606266;
}

.day-date {
  font-weight: 600;
  color: #303133;
}

.day-current {
  font-size: 13px;
  color: #909399;
}

.time-form {
  margin-top: 8px;
}

.filter-form {
  margin-bottom: 12px;
}

@media (max-width: 600px) {
  .config-page {
    padding: 16px;
  }

  .repair-form :deep(.el-form-item),
  .time-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .repair-form :deep(.el-select),
  .repair-form :deep(.el-date-editor) {
    width: 100% !important;
  }
}
</style>

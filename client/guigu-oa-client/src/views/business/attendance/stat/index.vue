<template>
  <div class="config-page stat-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">月度统计</h2>
        <p class="page-hint">
          应出勤按工作日公式逐日计（含补班、剔除休假日）；实出勤 = 应出勤 − 请假 − 缺卡
        </p>
      </div>
      <el-date-picker
        v-model="monthText"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        :clearable="false"
        class="month-picker"
        @change="loadMonth"
      />
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
      class="stat-table"
    >
      <el-table-column prop="name" label="姓名" width="110" fixed />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
      <el-table-column prop="requiredDays" label="应出勤" width="90" align="center" />
      <el-table-column prop="actualDays" label="实出勤" width="90" align="center" />
      <el-table-column prop="lateCount" label="迟到次数" width="100" align="center" />
      <el-table-column prop="earlyCount" label="早退次数" width="100" align="center" />
      <el-table-column prop="missingDays" label="缺卡天数" width="100" align="center" />
      <el-table-column
        v-for="typeName in leaveTypeColumns"
        :key="typeName"
        :label="`${typeName}（天）`"
        width="110"
        align="center"
      >
        <template #default="scope">{{ leaveDaysOf(scope.row, typeName) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="overtimeHoursText" label="加班时长" width="100" align="center" />
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDaily(scope.row)">明细</el-button>
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

    <section v-if="selectedUser" class="daily-panel">
      <div class="daily-header">
        <h3 class="daily-title">
          {{ selectedUser.name }}（{{ selectedUser.username }}）{{ dailyMonth }} 逐日明细
        </h3>
        <el-button link @click="closeDaily">收起</el-button>
      </div>

      <ListLoadError :status="dailyStatus" :error="dailyError" @retry="loadDaily" />

      <template v-if="dailyStatus === 'ready' && dailyView">
        <div class="day-list">
          <div
            v-for="day in dailyView.days"
            :key="day.date"
            class="day-row"
            :class="{ 'is-off': !day.workday, 'is-pending': day.pending }"
          >
            <div class="day-date">
              <span class="date-text">{{ day.date }}</span>
              <span class="weekday-text">{{ day.weekdayLabel }}</span>
            </div>
            <el-tag class="day-type" size="small" type="info">{{ day.dayTypeLabel }}</el-tag>
            <el-tag
              class="day-status"
              size="small"
              :type="attendanceDayStatusTagType(day.status, day.pending)"
              >{{ day.statusLabel }}</el-tag
            >
            <div class="day-times">
              <span>上班 {{ day.clockInText || '—' }}</span>
              <span>下班 {{ day.clockOutText || '—' }}</span>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, ref } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  attendanceDayStatusTagType,
  currentMonthText,
  presentAttendanceStat,
  presentMonth,
  statDailyRequest,
  statListRequest,
} from '@/config/attendance'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { createSnapshotRead } from '@/request/snapshot-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceMonthRecord,
  AttendanceMonthView,
  AttendanceStatRecord,
  AttendanceStatView,
} from '@/types/domain'

/**
 * 后台月度统计（#283，ADR 0073 §统计口径）：读只判页面
 * `/business/attendance/stat` 授予，没有新增按钮点；数据范围五档由服务端过滤。
 * 汇总行按人列出应出勤/实出勤/迟到/早退/缺卡/请假分列/加班时长；点「明细」
 * 下钻当月逐日状态，服务端复用 #281 推导 service 并先按同一行集合与范围校验。
 */
export default defineComponent({
  name: 'AttendanceStatPage',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const monthText = ref(currentMonthText())
    const list = createListPage<
      AttendanceStatRecord,
      AttendanceStatView,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => statListRequest(page, size, String(query.month || '')),
      present: presentAttendanceStat,
      query: { month: monthText.value },
      ...listPageEffects(),
    })

    const selectedUser = ref<AttendanceStatView | null>(null)
    const daily = createSnapshotRead<AttendanceMonthView>(
      openRequestScope(recognition, { notifyNormalErrors: false }),
    )
    const dailyView = computed(() => daily.value.value)
    const dailyMonth = computed(() => dailyView.value?.month || monthText.value)

    /** 请假类型列：跨行汇总出现过的类型，保持服务端返回顺序。 */
    const leaveTypeColumns = computed(() => {
      const names: string[] = []
      for (const row of list.rows.value) {
        for (const leave of row.leaveDays) {
          if (!names.includes(leave.leaveTypeName)) names.push(leave.leaveTypeName)
        }
      }
      return names
    })

    function leaveDaysOf(row: unknown, typeName: string): number {
      const stat = row as AttendanceStatView
      return stat.leaveDays.find((leave) => leave.leaveTypeName === typeName)?.days || 0
    }

    function loadDaily(): void {
      const user = selectedUser.value
      if (!user) return
      void daily.load(async (scope) => {
        const response = await scope.request<AttendanceMonthRecord>(
          statDailyRequest(user.userId, monthText.value),
        )
        return presentMonth(response.data || {})
      })
    }

    function openDaily(row: unknown): void {
      selectedUser.value = row as AttendanceStatView
      void loadDaily()
    }

    function closeDaily(): void {
      selectedUser.value = null
      daily.reset()
    }

    function loadMonth(value?: string): void {
      const month = value || monthText.value
      monthText.value = month
      closeDaily()
      list.search({ month })
    }

    onBeforeUnmount(() => {
      list.release()
      daily.release()
    })

    return {
      monthText,
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      leaveTypeColumns,
      leaveDaysOf,
      selectedUser,
      dailyView,
      dailyMonth,
      dailyStatus: daily.status,
      dailyError: daily.error,
      loadDaily,
      openDaily,
      closeDaily,
      loadMonth,
      attendanceDayStatusTagType,
    }
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

.month-picker {
  width: 180px;
}

.daily-panel {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.daily-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.daily-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-row {
  display: grid;
  grid-template-columns: 170px 90px 110px 1fr;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;

  &.is-off {
    background: #fafafa;
  }

  &.is-pending {
    border-style: dashed;
  }
}

.day-date {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.date-text {
  color: #303133;
}

.weekday-text {
  color: #909399;
  font-size: 13px;
}

.day-times {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #606266;
  font-size: 13px;
}

@media (max-width: 768px) {
  .config-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .month-picker {
    width: 100%;
  }

  .day-row {
    grid-template-columns: 1fr;
  }
}
</style>

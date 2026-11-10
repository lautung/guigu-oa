<template>
  <div class="attendance-record-page">
    <header class="page-header">
      <div>
        <h2 class="page-title">考勤记录</h2>
        <p class="page-hint">本人当月逐日状态；未到下班时刻且缺卡显示「未打卡」</p>
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
    </header>

    <ListLoadError :status="status" :error="error" @retry="retry" />

    <template v-if="status === 'ready' && monthView">
      <div class="month-line">
        <span class="month-text">{{ monthView.month }}</span>
        <span class="month-count">共 {{ monthView.days.length }} 天</span>
      </div>

      <div class="day-list">
        <div
          v-for="day in monthView.days"
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
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import { attendanceDayStatusTagType, monthRecordsRequest, presentMonth } from '@/config/attendance'
import { createSnapshotRead } from '@/request/snapshot-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { AttendanceMonthRecord, AttendanceMonthView } from '@/types/domain'

/**
 * 办事「考勤记录」（#281，ADR 0073）：本人当月逐日状态，与后台下钻、月统计
 * 复用同一推导 service。登录即有，不套后台授权；余额区归考勤实现票④（#280）。
 * 窄屏下逐日行折成两列，日期/状态与上下班时刻都能读。
 */
export default defineComponent({
  name: 'WorkMyAttendanceRecord',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    const opening = createSnapshotRead<AttendanceMonthView>(scope)
    const monthText = ref('')
    const monthView = computed(() => opening.value.value)

    async function load(month: string): Promise<void> {
      await opening.load(async (requestScope) => {
        const response = await requestScope.request<AttendanceMonthRecord>(
          monthRecordsRequest(month),
        )
        return presentMonth(response.data || {})
      })
      if (opening.value.value?.month) monthText.value = opening.value.value.month
    }

    function loadMonth(value?: string): void {
      void load(value || monthText.value)
    }

    onMounted(() => void load(''))
    onBeforeUnmount(() => opening.release())

    return {
      monthText,
      monthView,
      loadMonth,
      status: opening.status,
      error: opening.error,
      retry: () => loadMonth(),
      attendanceDayStatusTagType,
    }
  },
})
</script>

<style lang="scss" scoped>
.attendance-record-page {
  max-width: 860px;
  margin: 0 auto;
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

.month-line {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 8px 0 12px;
  color: #606266;
}

.month-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.month-count {
  font-size: 13px;
  color: #909399;
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-row {
  display: grid;
  grid-template-columns: 140px 96px 120px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.day-row.is-off {
  background: #fafafa;
}

.day-row.is-pending .day-status {
  color: #909399;
}

.day-date {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.date-text {
  font-weight: 600;
  color: #303133;
}

.weekday-text {
  font-size: 12px;
  color: #909399;
}

.day-times {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  color: #606266;
  font-size: 13px;
}

@media (max-width: 600px) {
  .attendance-record-page {
    padding: 16px;
  }

  .day-row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 8px;
  }

  .day-times {
    grid-column: 1 / -1;
  }
}
</style>

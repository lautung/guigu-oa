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

    <section class="balance-card">
      <div class="balance-header">
        <h3 class="balance-title">假期余额</h3>
        <span class="balance-hint">可用 = 发放 + 入账 − 扣减 − 在途</span>
      </div>
      <div v-if="balanceStatus === 'failed'" class="balance-empty">{{ balanceError }}</div>
      <div v-else class="balance-grid">
        <div
          v-for="balance in balances"
          :key="balance.leaveTypeId"
          class="balance-item"
          :class="{ 'is-negative': balance.negative, 'is-off': balance.status !== 1 }"
        >
          <span class="balance-name">{{ balance.leaveTypeName }}</span>
          <span class="balance-value">{{ balance.availableHoursText }} 小时</span>
          <span v-if="balance.pendingText" class="balance-pending">{{ balance.pendingText }}</span>
        </div>
        <div v-if="balanceStatus === 'ready' && balances.length === 0" class="balance-empty">
          暂无需要余额的假期类型
        </div>
      </div>
    </section>

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
import {
  attendanceDayStatusTagType,
  balancesRequest,
  monthRecordsRequest,
  presentBalances,
  presentMonth,
} from '@/config/attendance'
import { createSnapshotRead } from '@/request/snapshot-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceBalanceRecord,
  AttendanceBalanceView,
  AttendanceMonthRecord,
  AttendanceMonthView,
} from '@/types/domain'

/**
 * 办事「考勤记录」（#281/#280，ADR 0073）：本人当月逐日状态 + 各类型可用余额。
 * 逐日状态与后台下钻、月统计复用同一推导 service；余额由账本按用户求和，
 * 在途只算审批中请假单。登录即有，不套后台授权与数据范围。
 * 窄屏下余额卡片换行、逐日行折成两列，日期/状态与上下班时刻都能读。
 */
export default defineComponent({
  name: 'WorkMyAttendanceRecord',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    const balanceScope = openRequestScope(recognition, { notifyNormalErrors: false })
    const opening = createSnapshotRead<AttendanceMonthView>(scope)
    const balanceOpening = createSnapshotRead<AttendanceBalanceView[]>(balanceScope)
    const monthText = ref('')
    const monthView = computed(() => opening.value.value)
    const balances = computed(() => balanceOpening.value.value || [])

    async function load(month: string): Promise<void> {
      await Promise.all([
        opening.load(async (requestScope) => {
          const response = await requestScope.request<AttendanceMonthRecord>(
            monthRecordsRequest(month),
          )
          return presentMonth(response.data || {})
        }),
        balanceOpening.load(async (requestScope) => {
          const response = await requestScope.request<AttendanceBalanceRecord[]>(balancesRequest())
          return presentBalances(response.data || [])
        }),
      ])
      if (opening.value.value?.month) monthText.value = opening.value.value.month
    }

    function loadMonth(value?: string): void {
      void load(value || monthText.value)
    }

    onMounted(() => void load(''))
    onBeforeUnmount(() => {
      opening.release()
      balanceOpening.release()
    })

    return {
      monthText,
      monthView,
      balances,
      loadMonth,
      status: opening.status,
      error: opening.error,
      balanceStatus: balanceOpening.status,
      balanceError: balanceOpening.error,
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

.balance-card {
  margin-bottom: 18px;
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.balance-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.balance-title {
  margin: 0;
  font-size: 15px;
  color: #303133;
}

.balance-hint {
  color: #909399;
  font-size: 12px;
}

.balance-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.balance-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.balance-item.is-off {
  opacity: 0.65;
}

.balance-name {
  color: #606266;
  font-size: 13px;
}

.balance-value {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.balance-item.is-negative .balance-value {
  color: #f56c6c;
}

.balance-pending {
  color: #e6a23c;
  font-size: 12px;
}

.balance-empty {
  color: #909399;
  font-size: 13px;
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

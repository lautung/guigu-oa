<template>
  <div class="my-attendance-page">
    <header class="page-header">
      <div>
        <h2 class="page-title">今日打卡</h2>
        <p v-if="today" class="date-line">
          <span>{{ today.date }} {{ today.weekdayLabel }}</span>
          <el-tag size="small" :type="dayTypeTagType">{{ today.dayTypeLabel }}</el-tag>
          <span v-if="today.groupName" class="group-name">{{ today.groupName }}</span>
        </p>
      </div>
    </header>

    <ListLoadError :status="status" :error="error" @retry="retry" />

    <template v-if="status === 'ready' && today">
      <el-empty v-if="!today.hasGroup" description="未加入考勤组，暂不能打卡" />

      <template v-else>
        <section class="shift-card">
          <div class="shift-name">{{ today.shift ? today.shift.name : '未绑定班次' }}</div>
          <div v-if="today.shift" class="shift-meta">
            <span>上班 {{ today.shift.timeRange }}</span>
            <span>午休 {{ today.shift.lunchText }}</span>
            <span>宽限 {{ today.shift.graceMinutes }} 分钟</span>
          </div>
        </section>

        <div class="punch-grid">
          <section class="punch-card">
            <h3 class="punch-title">上班卡</h3>
            <p class="punch-state" :class="{ 'is-done': today.clockInTime }">
              {{ today.clockInLabel }}
            </p>
            <el-button
              type="primary"
              class="punch-button"
              :disabled="!canPunch || punching"
              @click="punch(1)"
              >上班打卡</el-button
            >
          </section>
          <section class="punch-card">
            <h3 class="punch-title">下班卡</h3>
            <p class="punch-state" :class="{ 'is-done': today.clockOutTime }">
              {{ today.clockOutLabel }}
            </p>
            <el-button
              type="primary"
              class="punch-button"
              :disabled="!canPunch || punching"
              @click="punch(2)"
              >下班打卡</el-button
            >
          </section>
        </div>

        <p v-if="!today.workday" class="rest-hint">今日无排班，无需打卡</p>
      </template>
    </template>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'

import { punchAttendance } from '@/api/attendance'
import ListLoadError from '@/components/ListLoadError.vue'
import { PUNCH_SLOT_IN, PUNCH_SLOT_OUT, presentToday, todayRequest } from '@/config/attendance'
import { createSnapshotRead } from '@/request/snapshot-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { AttendanceTodayRecord } from '@/types/domain'
import { ElMessage } from 'element-plus'

/**
 * 办事打卡（#279，ADR 0073）：登录即有，不套后台页面授予与数据范围。
 * 今日班次与两张卡状态来自服务器；打卡一律服务器时间，可重复打
 * （上班取最早、下班取最晚由服务端推导）。窄屏下卡片纵向堆叠。
 */
export default defineComponent({
  name: 'WorkMyAttendance',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    const opening = createSnapshotRead<ReturnType<typeof presentToday>>(scope)
    const punching = ref(false)

    async function load(): Promise<void> {
      await opening.load(async (requestScope) => {
        const response = await requestScope.request<AttendanceTodayRecord>(todayRequest())
        return presentToday(response.data || {})
      })
    }

    const today = computed(() => {
      const value = opening.value.value
      if (!value) return null
      return {
        ...value,
        clockInLabel: value.clockInTime ? `已打卡 ${value.clockInTime}` : '未打卡',
        clockOutLabel: value.clockOutTime ? `已打卡 ${value.clockOutTime}` : '未打卡',
      }
    })
    const canPunch = computed(() =>
      Boolean(today.value?.hasGroup && today.value?.workday && today.value?.shift),
    )
    const dayTypeTagType = computed(() => {
      const dayType = today.value?.dayType
      if (dayType === 1 || dayType === 4) return 'success'
      if (dayType === 3) return 'warning'
      return 'info'
    })

    async function punch(slot: number): Promise<void> {
      if (punching.value || !canPunch.value) return
      punching.value = true
      try {
        const response = await punchAttendance(slot)
        opening.reset(presentToday(response.data || {}))
        ElMessage.success(slot === PUNCH_SLOT_OUT ? '下班打卡成功' : '上班打卡成功')
      } catch {
        // 请求层已上屏失败文案；打卡状态保持原样，用户可重试。
      } finally {
        punching.value = false
      }
    }

    onMounted(() => void load())
    onBeforeUnmount(() => opening.release())

    return {
      today,
      canPunch,
      punching,
      dayTypeTagType,
      status: opening.status,
      error: opening.error,
      retry: () => void load(),
      punch,
      PUNCH_SLOT_IN,
      PUNCH_SLOT_OUT,
    }
  },
})
</script>

<style lang="scss" scoped>
.my-attendance-page {
  max-width: 760px;
  margin: 0 auto;
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

.date-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 8px 0 0;
  color: #606266;
}

.group-name {
  color: #909399;
}

.shift-card {
  padding: 16px;
  margin-bottom: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.shift-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.shift-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
}

.punch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.punch-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.punch-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.punch-state {
  margin: 0;
  color: #909399;
}

.punch-state.is-done {
  color: #67c23a;
  font-weight: 600;
}

.punch-button {
  width: 100%;
}

.rest-hint {
  margin: 16px 0 0;
  color: #909399;
  text-align: center;
}

@media (max-width: 600px) {
  .my-attendance-page {
    padding: 16px;
  }

  .punch-grid {
    grid-template-columns: 1fr;
  }
}
</style>

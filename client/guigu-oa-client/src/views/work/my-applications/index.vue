<template>
  <div class="my-applications-page">
    <header class="page-header">
      <div>
        <h2 class="page-title">我的申请</h2>
        <p class="page-hint">请假与加班单提交即发起审批；被拒/撤回可改后重提，单据号不变</p>
      </div>
    </header>

    <el-tabs v-model="activeKind" class="apply-tabs">
      <el-tab-pane label="请假" name="leave">
        <div class="tab-toolbar">
          <el-button type="primary" @click="openLeaveCreate">新建请假</el-button>
        </div>
        <ListLoadError
          :status="leaveStatus"
          :error="leaveError"
          @retry="loadLeavePage(leavePage)"
        />
        <el-empty v-if="leaveReady && leaveRows.length === 0" description="还没有请假单" />
        <div class="apply-list">
          <article v-for="row in leaveRows" :key="row.id" class="apply-card">
            <div class="apply-main">
              <div class="apply-title">
                <span class="type-name">{{ row.typeName }}</span>
                <el-tag size="small" :type="row.statusTagType">{{ row.statusLabel }}</el-tag>
              </div>
              <div class="apply-meta">
                <span>{{ row.dateText }}</span>
                <span>{{ row.durationText }}</span>
                <span v-if="row.submitTimeText">提交 {{ row.submitTimeText }}</span>
              </div>
              <p class="apply-reason">{{ row.reason || '未填写事由' }}</p>
            </div>
            <div class="apply-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.editable" link type="primary" @click="openLeaveEdit(row)">
                编辑
              </el-button>
              <el-button
                v-if="row.submittable"
                link
                type="primary"
                :loading="submittingId === row.id"
                @click="submitLeaveRow(row)"
                >提交</el-button
              >
              <el-button v-if="row.editable" link type="danger" @click="removeLeaveRow(row)">
                删除
              </el-button>
            </div>
          </article>
        </div>
        <el-pagination
          v-if="leaveTotal > pageSize"
          class="apply-pagination"
          layout="prev, pager, next, total"
          :total="leaveTotal"
          :page-size="pageSize"
          :current-page="leavePage"
          @current-change="loadLeavePage"
        />
      </el-tab-pane>

      <el-tab-pane label="加班" name="overtime">
        <div class="tab-toolbar">
          <el-button type="primary" @click="openOvertimeCreate">新建加班</el-button>
        </div>
        <ListLoadError
          :status="overtimeStatus"
          :error="overtimeError"
          @retry="loadOvertimePage(overtimePage)"
        />
        <el-empty v-if="overtimeReady && overtimeRows.length === 0" description="还没有加班单" />
        <div class="apply-list">
          <article v-for="row in overtimeRows" :key="row.id" class="apply-card">
            <div class="apply-main">
              <div class="apply-title">
                <span class="type-name">{{ row.typeName }}</span>
                <el-tag size="small" :type="row.statusTagType">{{ row.statusLabel }}</el-tag>
              </div>
              <div class="apply-meta">
                <span>{{ row.dateText }}</span>
                <span>{{ row.durationText }}</span>
                <span v-if="row.submitTimeText">提交 {{ row.submitTimeText }}</span>
              </div>
              <p class="apply-reason">{{ row.reason || '未填写事由' }}</p>
            </div>
            <div class="apply-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.editable" link type="primary" @click="openOvertimeEdit(row)">
                编辑
              </el-button>
              <el-button
                v-if="row.submittable"
                link
                type="primary"
                :loading="submittingId === row.id"
                @click="submitOvertimeRow(row)"
                >提交</el-button
              >
              <el-button v-if="row.editable" link type="danger" @click="removeOvertimeRow(row)">
                删除
              </el-button>
            </div>
          </article>
        </div>
        <el-pagination
          v-if="overtimeTotal > pageSize"
          class="apply-pagination"
          layout="prev, pager, next, total"
          :total="overtimeTotal"
          :page-size="pageSize"
          :current-page="overtimePage"
          @current-change="loadOvertimePage"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="leaveFormVisible" :title="leaveFormTitle" width="560px">
      <el-form ref="leaveFormRef" :model="leaveForm" :rules="leaveRules" label-width="90px">
        <el-form-item label="假期类型" prop="leaveTypeId">
          <el-select v-model="leaveForm.leaveTypeId" placeholder="选择假期类型" class="full-width">
            <el-option
              v-for="type in leaveTypeOptions"
              :key="type.id"
              :label="leaveTypeOptionLabel(type)"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="leaveForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="leaveForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="事由" prop="reason">
          <el-input
            v-model="leaveForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="提交时必填"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-hint">
        天数与小时数在提交时按当时班次与节假日日历定稿（休假日剔除、补班日照算）。
      </p>
      <template #footer>
        <el-button @click="leaveFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLeave">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="overtimeFormVisible" :title="overtimeFormTitle" width="560px">
      <el-form
        ref="overtimeFormRef"
        :model="overtimeForm"
        :rules="overtimeRules"
        label-width="90px"
      >
        <el-form-item label="加班日期" prop="overtimeDate">
          <el-date-picker
            v-model="overtimeForm.overtimeDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="加班日期"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="开始时刻" prop="startTime">
          <el-time-picker
            v-model="overtimeForm.startTime"
            value-format="HH:mm:ss"
            placeholder="开始时刻"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="结束时刻" prop="endTime">
          <el-time-picker
            v-model="overtimeForm.endTime"
            value-format="HH:mm:ss"
            placeholder="结束时刻"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="事由" prop="reason">
          <el-input
            v-model="overtimeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="提交时必填"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-hint">结束必须晚于开始且同一天内；跨天请拆成两天提交，休息日也可申请。</p>
      <template #footer>
        <el-button @click="overtimeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveOvertime">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="申请详情" width="560px">
      <template v-if="detailRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="单据号">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailRow.typeName }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ detailRow.dateText }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ detailRow.durationText }}</el-descriptions-item>
          <el-descriptions-item label="事由">{{
            detailRow.reason || '未填写'
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="detailRow.statusTagType">{{
              detailRow.statusLabel
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.submitTimeText" label="提交时间">
            {{ detailRow.submitTimeText }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.finishTimeText" label="终态时间">
            {{ detailRow.finishTimeText }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.approval" label="审批">
            {{ detailRow.approval.description || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.approval?.currentApprover" label="当前审批人">
            {{ detailRow.approval.currentApprover }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'

import {
  removeMyLeaveApplication,
  removeMyOvertimeApplication,
  saveMyLeaveApplication,
  saveMyOvertimeApplication,
  submitMyLeaveApplication,
  submitMyOvertimeApplication,
  updateMyLeaveApplication,
  updateMyOvertimeApplication,
} from '@/api/attendance'
import ListLoadError from '@/components/ListLoadError.vue'
import {
  emptyLeaveApplyForm,
  emptyOvertimeApplyForm,
  leaveApplyFormRules,
  myLeaveDetailRequest,
  myLeavePageRequest,
  myLeaveTypesRequest,
  myOvertimeDetailRequest,
  myOvertimePageRequest,
  overtimeApplyFormRules,
  presentLeaveApplication,
  presentLeaveApplications,
  presentLeaveApplyForm,
  presentMyLeaveTypes,
  presentOvertimeApplication,
  presentOvertimeApplications,
  presentOvertimeApplyForm,
} from '@/config/attendance'
import { createSnapshotRead } from '@/request/snapshot-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  AttendanceApplyListItem,
  AttendanceLeaveApplicationRecord,
  AttendanceLeaveApplyForm,
  AttendanceMyLeaveTypeView,
  AttendanceOvertimeApplicationRecord,
  AttendanceOvertimeApplyForm,
  EntityId,
} from '@/types/domain'
import { ElMessage, ElMessageBox } from 'element-plus'

const PAGE_SIZE = 10

interface ApplicationPage {
  rows: AttendanceApplyListItem[]
  total: number
}

/**
 * 办事「我的申请」（#282，ADR 0073/0067）：请假/加班两页签的列表、草稿新建/编辑、
 * 提交与详情状态。登录即有，不套后台授权与数据范围；服务端构造上只读本人。
 * 被拒/撤回可改后重提（单据号不变）；提交带客户端幂等键，同键重放返回同一条审批。
 * 窄屏下卡片纵向堆叠、操作按钮换行。
 */
export default defineComponent({
  name: 'WorkMyApplications',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')

    const leaveScope = openRequestScope(recognition, { notifyNormalErrors: false })
    const overtimeScope = openRequestScope(recognition, { notifyNormalErrors: false })
    const typeScope = openRequestScope(recognition, { notifyNormalErrors: false })
    const detailScope = openRequestScope(recognition, { notifyNormalErrors: false })

    const leaveOpening = createSnapshotRead<ApplicationPage>(leaveScope)
    const overtimeOpening = createSnapshotRead<ApplicationPage>(overtimeScope)
    const typeOpening = createSnapshotRead<AttendanceMyLeaveTypeView[]>(typeScope)

    const activeKind = ref<'leave' | 'overtime'>('leave')
    const leavePage = ref(1)
    const overtimePage = ref(1)
    const saving = ref(false)
    const submittingId = ref<EntityId | ''>('')

    const leaveRows = computed(() => leaveOpening.value.value?.rows || [])
    const overtimeRows = computed(() => overtimeOpening.value.value?.rows || [])
    const leaveTotal = computed(() => leaveOpening.value.value?.total || 0)
    const overtimeTotal = computed(() => overtimeOpening.value.value?.total || 0)
    const leaveTypeOptions = computed(() => typeOpening.value.value || [])

    const leaveFormRef = ref()
    const overtimeFormRef = ref()
    const leaveFormVisible = ref(false)
    const overtimeFormVisible = ref(false)
    const leaveForm = ref<AttendanceLeaveApplyForm>(emptyLeaveApplyForm())
    const overtimeForm = ref<AttendanceOvertimeApplyForm>(emptyOvertimeApplyForm())
    const leaveRules = leaveApplyFormRules()
    const overtimeRules = overtimeApplyFormRules()

    const leaveFormTitle = computed(() => (leaveForm.value.id ? '编辑请假' : '新建请假'))
    const overtimeFormTitle = computed(() => (overtimeForm.value.id ? '编辑加班' : '新建加班'))

    const detailVisible = ref(false)
    const detailRow = ref<AttendanceApplyListItem | null>(null)

    async function loadLeavePage(page: number): Promise<void> {
      leavePage.value = page
      await leaveOpening.load(async (requestScope) => {
        const response = await requestScope.request<{
          records?: AttendanceLeaveApplicationRecord[]
          total?: number
        }>(myLeavePageRequest(page, PAGE_SIZE))
        return {
          rows: presentLeaveApplications(response.data?.records || []),
          total: response.data?.total || 0,
        }
      })
    }

    async function loadOvertimePage(page: number): Promise<void> {
      overtimePage.value = page
      await overtimeOpening.load(async (requestScope) => {
        const response = await requestScope.request<{
          records?: AttendanceOvertimeApplicationRecord[]
          total?: number
        }>(myOvertimePageRequest(page, PAGE_SIZE))
        return {
          rows: presentOvertimeApplications(response.data?.records || []),
          total: response.data?.total || 0,
        }
      })
    }

    async function loadLeaveTypes(): Promise<void> {
      await typeOpening.load(async (requestScope) => {
        const response =
          await requestScope.request<Parameters<typeof presentMyLeaveTypes>[0]>(
            myLeaveTypesRequest(),
          )
        return presentMyLeaveTypes(response.data || [])
      })
    }

    function leaveTypeOptionLabel(type: AttendanceMyLeaveTypeView): string {
      if (type.balanceRequired !== 1) return type.name
      return type.availableHoursText
        ? `${type.name}（可用 ${type.availableHoursText} 小时）`
        : type.name
    }

    function openLeaveCreate(): void {
      leaveForm.value = emptyLeaveApplyForm()
      leaveFormVisible.value = true
    }

    function openLeaveEdit(row: AttendanceApplyListItem): void {
      leaveForm.value = presentLeaveApplyForm(row.leave)
      leaveFormVisible.value = true
    }

    function openOvertimeCreate(): void {
      overtimeForm.value = emptyOvertimeApplyForm()
      overtimeFormVisible.value = true
    }

    function openOvertimeEdit(row: AttendanceApplyListItem): void {
      overtimeForm.value = presentOvertimeApplyForm(row.overtime)
      overtimeFormVisible.value = true
    }

    async function validate(formRef: unknown): Promise<boolean> {
      const candidate = formRef as { validate?: () => Promise<boolean> } | null
      if (candidate && typeof candidate.validate === 'function') {
        try {
          return await candidate.validate()
        } catch {
          return false
        }
      }
      return true
    }

    async function saveLeave(): Promise<void> {
      if (!leaveForm.value.leaveTypeId) {
        ElMessage.warning('假期类型不能为空')
        return
      }
      if (!leaveForm.value.startDate || !leaveForm.value.endDate) {
        ElMessage.warning('请假日期不能为空')
        return
      }
      if (leaveForm.value.startDate > leaveForm.value.endDate) {
        ElMessage.warning('开始日期不能晚于结束日期')
        return
      }
      if (!(await validate(leaveFormRef.value))) return
      saving.value = true
      try {
        if (leaveForm.value.id) {
          await updateMyLeaveApplication(leaveForm.value)
        } else {
          await saveMyLeaveApplication(leaveForm.value)
        }
        leaveFormVisible.value = false
        ElMessage.success('草稿已保存')
        await loadLeavePage(1)
      } catch {
        // 请求层已上屏失败文案；对话框保持打开，用户可修正后重试。
      } finally {
        saving.value = false
      }
    }

    async function saveOvertime(): Promise<void> {
      if (!overtimeForm.value.overtimeDate) {
        ElMessage.warning('加班日期不能为空')
        return
      }
      if (!overtimeForm.value.startTime || !overtimeForm.value.endTime) {
        ElMessage.warning('加班起止时刻不能为空')
        return
      }
      if (overtimeForm.value.endTime <= overtimeForm.value.startTime) {
        ElMessage.warning('结束时刻必须晚于开始时刻')
        return
      }
      if (!(await validate(overtimeFormRef.value))) return
      saving.value = true
      try {
        if (overtimeForm.value.id) {
          await updateMyOvertimeApplication(overtimeForm.value)
        } else {
          await saveMyOvertimeApplication(overtimeForm.value)
        }
        overtimeFormVisible.value = false
        ElMessage.success('草稿已保存')
        await loadOvertimePage(1)
      } catch {
        // 请求层已上屏失败文案。
      } finally {
        saving.value = false
      }
    }

    /** 每次提交生成新键；同键重放由服务端返回同一条审批（ADR 0032/0067）。 */
    function idempotencyKey(): string {
      const cryptoApi = globalThis.crypto as Crypto | undefined
      if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
        return cryptoApi.randomUUID()
      }
      return `apply-${Date.now()}-${Math.random().toString(16).slice(2)}`
    }

    function replaceLeaveRow(record: AttendanceLeaveApplicationRecord): void {
      const current = leaveOpening.value.value
      if (!current) return
      leaveOpening.value.value = {
        rows: current.rows.map((row) =>
          row.id === record.id ? presentLeaveApplication(record) : row,
        ),
        total: current.total,
      }
    }

    function replaceOvertimeRow(record: AttendanceOvertimeApplicationRecord): void {
      const current = overtimeOpening.value.value
      if (!current) return
      overtimeOpening.value.value = {
        rows: current.rows.map((row) =>
          row.id === record.id ? presentOvertimeApplication(record) : row,
        ),
        total: current.total,
      }
    }

    async function submitLeaveRow(row: AttendanceApplyListItem): Promise<void> {
      if (submittingId.value === row.id) return
      submittingId.value = row.id
      try {
        const response = await submitMyLeaveApplication(row.id, idempotencyKey())
        if (response.data) replaceLeaveRow(response.data)
        ElMessage.success('已提交审批')
      } catch {
        // 请求层已上屏失败文案；状态保持原样。
      } finally {
        submittingId.value = ''
      }
    }

    async function submitOvertimeRow(row: AttendanceApplyListItem): Promise<void> {
      if (submittingId.value === row.id) return
      submittingId.value = row.id
      try {
        const response = await submitMyOvertimeApplication(row.id, idempotencyKey())
        if (response.data) replaceOvertimeRow(response.data)
        ElMessage.success('已提交审批')
      } catch {
        // 请求层已上屏失败文案；状态保持原样。
      } finally {
        submittingId.value = ''
      }
    }

    async function removeLeaveRow(row: AttendanceApplyListItem): Promise<void> {
      try {
        await ElMessageBox.confirm('删除后不可恢复，确定删除这张请假单？', '删除确认', {
          type: 'warning',
        })
      } catch {
        return
      }
      try {
        await removeMyLeaveApplication(row.id)
        ElMessage.success('已删除')
        await loadLeavePage(leavePage.value)
      } catch {
        // 请求层已上屏失败文案。
      }
    }

    async function removeOvertimeRow(row: AttendanceApplyListItem): Promise<void> {
      try {
        await ElMessageBox.confirm('删除后不可恢复，确定删除这张加班单？', '删除确认', {
          type: 'warning',
        })
      } catch {
        return
      }
      try {
        await removeMyOvertimeApplication(row.id)
        ElMessage.success('已删除')
        await loadOvertimePage(overtimePage.value)
      } catch {
        // 请求层已上屏失败文案。
      }
    }

    async function openDetail(row: AttendanceApplyListItem): Promise<void> {
      detailRow.value = row
      detailVisible.value = true
      try {
        const response = await detailScope.request<
          AttendanceLeaveApplicationRecord | AttendanceOvertimeApplicationRecord
        >(row.kind === 'leave' ? myLeaveDetailRequest(row.id) : myOvertimeDetailRequest(row.id))
        detailRow.value =
          row.kind === 'leave'
            ? presentLeaveApplication(response.data as AttendanceLeaveApplicationRecord)
            : presentOvertimeApplication(response.data as AttendanceOvertimeApplicationRecord)
      } catch {
        // 详情读失败时保留列表行的摘要，不阻塞关闭。
      }
    }

    onMounted(() => {
      void Promise.all([loadLeavePage(1), loadOvertimePage(1), loadLeaveTypes()])
    })
    onBeforeUnmount(() => {
      leaveOpening.release()
      overtimeOpening.release()
      typeOpening.release()
    })

    return {
      activeKind,
      pageSize: PAGE_SIZE,
      leavePage,
      overtimePage,
      leaveRows,
      overtimeRows,
      leaveTotal,
      overtimeTotal,
      leaveTypeOptions,
      leaveFormVisible,
      overtimeFormVisible,
      leaveForm,
      overtimeForm,
      leaveFormRef,
      overtimeFormRef,
      leaveRules,
      overtimeRules,
      leaveFormTitle,
      overtimeFormTitle,
      saving,
      submittingId,
      detailVisible,
      detailRow,
      loadLeavePage,
      loadOvertimePage,
      openLeaveCreate,
      openLeaveEdit,
      openOvertimeCreate,
      openOvertimeEdit,
      saveLeave,
      saveOvertime,
      submitLeaveRow,
      submitOvertimeRow,
      removeLeaveRow,
      removeOvertimeRow,
      openDetail,
      leaveTypeOptionLabel,
      leaveStatus: leaveOpening.status,
      leaveError: leaveOpening.error,
      leaveReady: leaveOpening.loaded,
      overtimeStatus: overtimeOpening.status,
      overtimeError: overtimeOpening.error,
      overtimeReady: overtimeOpening.loaded,
    }
  },
})
</script>

<style lang="scss" scoped>
.my-applications-page {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  margin-bottom: 8px;
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

.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.apply-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.apply-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px 16px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafcff;
}

.apply-main {
  flex: 1 1 260px;
  min-width: 0;
}

.apply-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.type-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.apply-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}

.apply-reason {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.apply-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
}

.apply-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

.full-width {
  width: 100%;
}

.dialog-hint {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .my-applications-page {
    padding: 16px;
  }

  .apply-card {
    padding: 12px;
  }

  .apply-actions {
    width: 100%;
    justify-content: flex-end;
  }

  :deep(.el-dialog) {
    width: 92vw !important;
  }
}
</style>

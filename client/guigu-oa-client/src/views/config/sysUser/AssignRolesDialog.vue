<template>
  <el-dialog
    :model-value="modelValue"
    title="分配角色"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="handleUpdate"
  >
    <div v-if="!assignView" v-loading="true" class="assign-loading">正在打开分配角色…</div>
    <template v-else>
      <p class="assign-user">用户：{{ assignView.username || assignView.userId }}</p>

      <div v-if="assignView.phase === 'loading'" v-loading="true" class="assign-loading">
        正在读取角色…
      </div>

      <div v-else-if="assignView.phase === 'read-error'" class="result-panel" role="alert">
        <p>{{ assignView.error }}</p>
        <el-button @click="lease?.reload()">重试读取</el-button>
      </div>

      <div v-else-if="assignView.phase === 'unconfirmed'" class="result-panel" role="alert">
        <h3>分配结果待确认</h3>
        <p>
          已提交分配，但还不能确认是否已经保存。本次提交内容已保留，暂停再次保存；重新读取当前角色也不能证明原提交成功或失败。
        </p>

        <section class="role-block" aria-label="本次提交的角色">
          <h4>本次提交</h4>
          <p v-if="!assignView.submittedRoles.length" class="role-empty">空角色列表</p>
          <ul v-else>
            <li v-for="role in assignView.submittedRoles" :key="role.id">{{ role.roleName }}</li>
          </ul>
        </section>

        <section class="role-block" aria-label="最新已保存角色">
          <h4>最新已保存角色</h4>
          <template v-if="assignView.latest">
            <p v-if="!assignView.latest.roleIds.length" class="role-empty">空角色列表</p>
            <ul v-else>
              <li v-for="(name, index) in latestRoleNames" :key="index">{{ name }}</li>
            </ul>
          </template>
          <p v-else class="role-empty">尚未重新读取。</p>
          <el-button :disabled="!assignView.canReRead" @click="lease?.reload()">
            {{ assignView.reading ? '正在重新读取…' : '重新读取当前角色' }}
          </el-button>
        </section>

        <p v-if="assignView.error" class="assign-error">{{ assignView.error }}</p>
        <p>结束本次分配只清理本地记录，不判定原保存失败；原保存仍可能生效并影响后续分配。</p>
        <el-button type="warning" :disabled="!assignView.canEnd" @click="lease?.endAssignment()">
          结束本次分配
        </el-button>
      </div>

      <template v-else>
        <div class="role-picker" :inert="!assignView.canSelect">
          <p v-if="!assignView.roles.length" class="role-empty">
            当前没有可分配的角色，保存将清空该用户的角色。
          </p>
          <el-checkbox-group
            v-else
            :model-value="selectedRoleIds"
            :disabled="!assignView.canSelect"
            @update:model-value="selectRoles"
          >
            <el-checkbox v-for="role in assignView.roles" :key="role.id" :value="role.id">
              {{ role.roleName }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
        <p v-if="assignView.phase === 'saving'" role="status" class="assign-status">
          正在保存角色分配，本次提交内容已固定。离开不撤销已提交的保存。
        </p>
        <p v-if="assignView.error" class="assign-error" role="alert">{{ assignView.error }}</p>
      </template>
    </template>

    <template #footer>
      <div>
        <el-button @click="requestLeave()">
          {{ assignView?.phase === 'unconfirmed' ? '关闭' : '取消' }}
        </el-button>
        <el-button
          v-if="assignView && assignView.phase !== 'unconfirmed'"
          type="primary"
          :disabled="!assignView.canSave"
          @click="lease?.save()"
        >
          保存
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import { useAssignRoles } from '@/config/assign-roles-browser'
import type { AssignRolesLease } from '@/config/assign-roles'
import type { EntityId } from '@/types/domain'

interface AssignUser {
  id: EntityId
  username: string
}

const props = defineProps<{
  modelValue: boolean
  user: AssignUser | null
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const assignRoles = useAssignRoles()
const lease = shallowRef<AssignRolesLease>()
const assignView = computed(() => lease.value?.view.value)
const selectedRoleIds = computed<EntityId[]>(() => [...(assignView.value?.selectedRoleIds || [])])
const latestRoleNames = computed(() => {
  const latest = assignView.value?.latest
  if (!latest) return []
  const names = new Map(latest.roles.map((role) => [String(role.id), role.roleName]))
  return latest.roleIds.map((id) => names.get(String(id)) || String(id))
})

watch(
  () => [props.modelValue, props.user ? String(props.user.id) : ''] as const,
  ([open]) => {
    if (open && props.user) {
      if (!lease.value || String(lease.value.view.value.userId) !== String(props.user.id)) {
        lease.value?.release()
        lease.value = assignRoles.open({
          id: props.user.id,
          username: props.user.username,
        })
      }
    } else if (!open) {
      lease.value?.release()
      lease.value = undefined
    }
  },
  { immediate: true },
)

watch(
  () => assignView.value?.phase,
  (phase) => {
    if (phase === 'saved' || phase === 'closed') {
      emit('update:modelValue', false)
      lease.value?.release()
      lease.value = undefined
    }
  },
)

function selectRoles(value: unknown) {
  if (Array.isArray(value)) lease.value?.select(value as EntityId[])
}

async function requestLeave(force = false): Promise<boolean> {
  const current = lease.value
  if (!current) {
    if (!force) emit('update:modelValue', false)
    return true
  }
  const allowed = await current.requestLeave(force)
  if (allowed) {
    emit('update:modelValue', false)
    current.release()
    if (lease.value === current) lease.value = undefined
  }
  return allowed
}

function handleUpdate(value: boolean) {
  if (value) emit('update:modelValue', true)
  else void requestLeave()
}

onBeforeUnmount(() => {
  lease.value?.release()
  lease.value = undefined
})

defineExpose({ requestLeave })
</script>

<style lang="scss" scoped>
.assign-user {
  margin: 0 0 12px;
  color: #606266;
}

.assign-loading {
  min-height: 80px;
}

.role-picker {
  max-height: 320px;
  overflow-y: auto;
}

.role-block {
  margin: 12px 0;
}

.role-block h3,
.role-block h4 {
  margin: 0 0 8px;
}

.role-block ul {
  margin: 0 0 8px;
  padding-left: 20px;
}

.role-empty {
  color: #909399;
}

.assign-error {
  color: #f56c6c;
}

.assign-status {
  color: #e6a23c;
}

.result-panel {
  padding: 12px;
  border: 1px solid #e6a23c;
  border-radius: 6px;
}

.result-panel h3 {
  margin-top: 0;
  font-size: 16px;
}
</style>

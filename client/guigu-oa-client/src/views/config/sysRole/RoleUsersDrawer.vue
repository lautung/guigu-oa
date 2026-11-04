<template>
  <el-drawer
    :model-value="modelValue"
    title="分配用户"
    size="720px"
    :close-on-click-modal="false"
    @update:model-value="handleUpdate"
  >
    <div v-if="!assignView" v-loading="true" class="assign-loading">正在打开分配用户…</div>
    <template v-else>
      <p class="assign-role">角色：{{ assignView.roleName || assignView.roleId }}</p>

      <div v-if="assignView.phase === 'loading'" v-loading="true" class="assign-loading">
        正在读取成员与用户…
      </div>

      <div v-else-if="assignView.phase === 'read-error'" class="result-panel" role="alert">
        <p>{{ assignView.error }}</p>
        <el-button @click="lease?.reload()">重试读取</el-button>
      </div>

      <div v-else-if="assignView.phase === 'unconfirmed'" class="result-panel" role="alert">
        <h3>分配结果待确认</h3>
        <p>
          已提交成员分配，但还不能确认是否已经保存。本次提交内容已保留，暂停再次保存；重新读取当前成员也不能证明原提交成功或失败。
        </p>

        <section class="result-block" aria-label="本次提交的成员">
          <h4>本次提交</h4>
          <p v-if="!assignView.submittedUsers.length" class="empty">空成员列表</p>
          <ul v-else>
            <li v-for="user in assignView.submittedUsers" :key="user.id">
              {{ userLabel(user) }}
            </li>
          </ul>
        </section>

        <section class="result-block" aria-label="最新已保存成员">
          <h4>最新已保存成员</h4>
          <template v-if="assignView.latest">
            <p v-if="!assignView.latest.members.length" class="empty">空成员列表</p>
            <ul v-else>
              <li v-for="user in assignView.latest.members" :key="user.id">
                {{ userLabel(user) }}
              </li>
            </ul>
          </template>
          <p v-else class="empty">尚未重新读取。</p>
          <el-button :disabled="!assignView.canReRead" @click="lease?.reload()">
            {{ assignView.reading ? '正在重新读取…' : '重新读取当前成员' }}
          </el-button>
        </section>

        <p v-if="assignView.error" class="assign-error">{{ assignView.error }}</p>
        <p>结束本次分配只清理本地记录，不判定原保存失败；原保存仍可能生效并影响后续分配。</p>
        <el-button type="warning" :disabled="!assignView.canEnd" @click="lease?.endAssignment()">
          结束本次分配
        </el-button>
      </div>

      <template v-else>
        <div class="dual-list">
          <section class="pane" aria-label="未选用户">
            <h3>未选用户</h3>
            <div class="search-row">
              <el-input
                v-model="keyword"
                placeholder="用户名/姓名/手机号码"
                clearable
                :disabled="!assignView.canSearch"
                @keyup.enter="search"
              />
              <el-button :disabled="!assignView.canSearch" @click="search">搜索</el-button>
            </div>
            <ul v-if="assignView.candidates.length" class="user-list">
              <li v-for="user in assignView.candidates" :key="user.id">
                <span>{{ userLabel(user) }}</span>
                <el-button link :disabled="!assignView.canMove" @click="add([user.id])">
                  添加
                </el-button>
              </li>
            </ul>
            <p v-else class="empty">没有匹配的用户</p>
            <div class="pager">
              <el-button
                link
                :disabled="!assignView.canMove || assignView.page <= 1"
                @click="goPage(assignView.page - 1)"
              >
                上一页
              </el-button>
              <span>第 {{ assignView.page }} 页 / 共 {{ assignView.total }} 人</span>
              <el-button
                link
                :disabled="
                  !assignView.canMove || assignView.page * assignView.size >= assignView.total
                "
                @click="goPage(assignView.page + 1)"
              >
                下一页
              </el-button>
            </div>
          </section>

          <section class="pane" aria-label="已选用户">
            <h3>已选用户（{{ assignView.members.length }}）</h3>
            <ul v-if="assignView.members.length" class="user-list">
              <li v-for="user in assignView.members" :key="user.id">
                <span>{{ userLabel(user) }}</span>
                <el-button link :disabled="!assignView.canMove" @click="remove([user.id])">
                  移除
                </el-button>
              </li>
            </ul>
            <p v-else class="empty">当前没有成员，保存将清空该角色的成员。</p>
          </section>
        </div>

        <p v-if="assignView.phase === 'saving'" role="status" class="assign-status">
          正在保存成员分配，本次提交内容已固定。离开不撤销已提交的保存。
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
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoleUsers } from '@/config/role-users-browser'
import type { RoleUser, RoleUsersLease } from '@/config/role-users'
import type { EntityId } from '@/types/domain'

interface AssignRole {
  id: EntityId
  roleName?: string
}

const props = defineProps<{
  modelValue: boolean
  role: AssignRole | null
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const roleUsers = useRoleUsers()
const lease = shallowRef<RoleUsersLease>()
const assignView = computed(() => lease.value?.view.value)
const keyword = ref('')

function userLabel(user: RoleUser): string {
  return user.name ? `${user.username}（${user.name}）` : user.username
}

function close(): void {
  emit('update:modelValue', false)
  lease.value?.dispose()
  lease.value = undefined
}

watch(
  () => [props.modelValue, props.role ? String(props.role.id) : ''] as const,
  ([open]) => {
    if (open && props.role) {
      if (!lease.value || String(lease.value.view.value.roleId) !== String(props.role.id)) {
        lease.value?.dispose()
        lease.value = roleUsers.open({
          id: props.role.id,
          roleName: props.role.roleName,
        })
        keyword.value = lease.value.view.value.keyword
      }
    } else if (!open) {
      lease.value?.dispose()
      lease.value = undefined
    }
  },
  { immediate: true },
)

watch(
  () => lease.value?.released.value ?? false,
  (released) => {
    if (released) close()
  },
)

watch(
  () => assignView.value?.phase,
  (phase) => {
    if (phase === 'saved' || phase === 'closed') close()
  },
)

watch(
  () => assignView.value?.keyword,
  (value) => {
    if (value !== undefined) keyword.value = value
  },
)

function search(): void {
  void lease.value?.search(keyword.value)
}

function goPage(page: number): void {
  void lease.value?.goPage(page)
}

function add(ids: EntityId[]): void {
  lease.value?.add(ids)
}

function remove(ids: EntityId[]): void {
  lease.value?.remove(ids)
}

async function requestLeave(): Promise<boolean> {
  const current = lease.value
  if (!current) {
    emit('update:modelValue', false)
    return true
  }
  const allowed = await current.requestLeave()
  if (allowed) close()
  return allowed
}

function environmentChanged(): void {
  void lease.value?.environmentChanged()
}

function handleUpdate(value: boolean) {
  if (value) emit('update:modelValue', true)
  else void requestLeave()
}

onBeforeUnmount(() => {
  lease.value?.dispose()
  lease.value = undefined
})

defineExpose({ requestLeave, environmentChanged })
</script>

<style lang="scss" scoped>
.assign-role {
  margin: 0 0 12px;
  color: #606266;
}

.assign-loading {
  min-height: 80px;
}

.dual-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.pane {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 12px;
  min-height: 320px;
}

.pane h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.user-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 280px;
  overflow-y: auto;
}

.user-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid #f0f2f5;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}

.result-block {
  margin: 12px 0;
}

.result-block h4 {
  margin: 0 0 8px;
}

.result-block ul {
  margin: 0 0 8px;
  padding-left: 20px;
}

.empty {
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

<template>
  <el-popover v-model:visible="noticeVisible" placement="bottom-end" :width="320" trigger="click">
    <template #reference>
      <el-badge class="notice-badge" :value="badgeText" :hidden="unreadCount === 0">
        <button type="button" class="notice-bell" aria-label="公告" title="公告">
          <component :is="shellIcon('Bell')" class="notice-bell-icon" />
        </button>
      </el-badge>
    </template>
    <div class="notice-panel">
      <p v-if="unreadCount === 0 || recent.length === 0" class="notice-empty">暂无未读公告</p>
      <ul v-else class="notice-list">
        <li v-for="item in recent" :key="item.id">
          <button type="button" class="notice-item" @click="openNotice(item.id)">
            <span class="notice-item-title">{{ item.title }}</span>
            <span class="notice-item-meta">
              <DictTag :dict-type="DICT_TYPES.noticeType" :value="item.type" />
              · {{ item.publishTime }}
            </span>
          </button>
        </li>
      </ul>
      <div class="notice-footer">
        <button type="button" class="notice-all" @click="openAll">查看全部</button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DictTag from '@/components/DictTag.vue'
import { DICT_TYPES } from '@/config/dict'
import { PATHS, noticeDetailPath } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import { useNoticeUnread } from '@/notice/unread-browser'
import { shellIcon } from './shell-icons'

const router = useRouter()
const userStore = useUserStore()
const unread = useNoticeUnread()
const noticeVisible = ref(false)

const unreadCount = computed(() => unread.state.count)
/** 角标超过 99 只显示 99+（ADR 0055）。 */
const badgeText = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))
const recent = computed(() => unread.state.recent)

onMounted(() => {
  // 登录成功后外壳才挂载：这里就是「登录后立即拉一次」。
  if (userStore.token) unread.start()
})

watch(
  () => userStore.token,
  (token) => {
    if (token) unread.start()
    else unread.stop()
  },
)

onBeforeUnmount(() => unread.stop())

function openNotice(id: unknown) {
  noticeVisible.value = false
  void router.push(noticeDetailPath(id as string | number))
}

function openAll() {
  noticeVisible.value = false
  void router.push(PATHS.NOTICE)
}

defineExpose({ openNotice, openAll })
</script>

<style lang="scss" scoped>
.notice-badge {
  display: inline-flex;
  align-items: center;
}

.notice-bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 20px;
  color: var(--el-text-color-regular);
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.notice-bell-icon {
  width: 20px;
  height: 20px;
}

.notice-bell:hover,
.notice-bell:focus-visible {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  outline: none;
}

.notice-panel {
  padding: 4px 0;
}

.notice-empty {
  margin: 8px 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.notice-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 6px;
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.notice-item:hover,
.notice-item:focus-visible {
  background: var(--el-fill-color-light);
  outline: none;
}

.notice-item-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notice-footer {
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-light);
  text-align: center;
}

.notice-all {
  padding: 2px 8px;
  font-size: 13px;
  color: var(--el-color-primary);
  background: transparent;
  border: none;
  cursor: pointer;
}
</style>

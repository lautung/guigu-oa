<template>
  <header class="top-bar">
    <div class="brand">云尚办公</div>
    <div class="user-area">
      <el-popover
        v-model:visible="noticeVisible"
        placement="bottom-end"
        :width="320"
        trigger="click"
      >
        <template #reference>
          <el-badge class="notice-badge" :value="badgeText" :hidden="unreadCount === 0">
            <button type="button" class="notice-bell" aria-label="公告" title="公告">
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M18 16v-5a6 6 0 1 0-12 0v5l-1.6 2h15.2L18 16Z" />
                <path d="M10 21a2 2 0 0 0 4 0" />
              </svg>
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
                  {{ item.typeLabel }} · {{ item.publishTime }}
                </span>
              </button>
            </li>
          </ul>
          <div class="notice-footer">
            <button type="button" class="notice-all" @click="openAll">查看全部</button>
          </div>
        </div>
      </el-popover>
      <img :src="avatarSrc" class="avatar" alt="" />
      <span class="username">{{ userStore.name }}</span>
      <el-button link class="logout-btn" @click="handleLogout">退出</el-button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PATHS, noticeDetailPath } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import { useNoticeUnread } from '@/notice/unread-browser'

const DEFAULT_AVATAR = '/avatar.svg'

const router = useRouter()
const userStore = useUserStore()
const unread = useNoticeUnread()
const noticeVisible = ref(false)

const avatarSrc = computed(() => userStore.avatar || DEFAULT_AVATAR)
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

async function handleLogout() {
  await userStore.logout()
}
</script>

<style lang="scss" scoped>
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  flex-shrink: 0;
}

.brand {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

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
  color: #606266;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.notice-bell:hover,
.notice-bell:focus-visible {
  color: var(--el-color-primary);
  background: #f5f7fa;
  outline: none;
}

.notice-panel {
  padding: 4px 0;
}

.notice-empty {
  margin: 8px 4px;
  font-size: 13px;
  color: #909399;
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
  background: #f5f7fa;
  outline: none;
}

.notice-item-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item-meta {
  font-size: 12px;
  color: #909399;
}

.notice-footer {
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid #ebeef5;
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

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  font-size: 14px;
  color: #606266;
}

.logout-btn {
  padding: 0;
  margin-left: 4px;
}

@media (max-width: 768px) {
  .brand {
    font-size: 16px;
  }

  .username {
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

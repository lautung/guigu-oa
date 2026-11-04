<template>
  <div class="notice-detail">
    <div class="page-header">
      <h2 class="page-title">公告详情</h2>
      <el-button @click="backToNotice">返回全部公告</el-button>
    </div>

    <ListLoadError
      :status="status === 'failed' ? 'failed' : 'ready'"
      :error="error"
      @retry="load"
    />

    <p v-if="status === 'loading'" role="status" class="notice-loading">正在读取公告…</p>

    <div v-else-if="status === 'missing'" class="notice-missing">
      <el-empty description="公告不存在或已下线" />
      <div class="notice-missing-actions">
        <el-button type="primary" @click="backToNotice">返回全部公告</el-button>
      </div>
    </div>

    <article v-else-if="status === 'ready' && detail" class="notice-card">
      <div class="notice-head">
        <DictTag
          :dict-type="DICT_TYPES.noticeType"
          :value="detail.type"
          :tag-type="detail.typeTagType"
        />
        <h3 class="notice-title">{{ detail.title }}</h3>
        <p class="notice-meta">发布人：{{ detail.publisherName }} · {{ detail.publishTime }}</p>
      </div>
      <NoticeContent :html="detail.content" />
    </article>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

import DictTag from '@/components/DictTag.vue'
import ListLoadError from '@/components/ListLoadError.vue'
import { DICT_TYPES } from '@/config/dict'
import NoticeContent from '@/notice/NoticeContent.vue'
import { markNoticeRead } from '@/notice/api'
import { detailRequest, presentDetail } from '@/notice/config'
import { refreshNoticeUnread } from '@/notice/unread-browser'
import { openRequestScope } from '@/request/request-scope'
import { PATHS } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import type { NoticeDetailRecord, NoticeDetailView } from '@/types/domain'

/**
 * 公告详情（ADR 0055）：加载成功后自动写已读回执，幂等且每次访问只写一次；
 * 撤回/删除/不存在按不存在视图处理，不画成错误页。
 */
export default defineComponent({
  name: 'NoticeDetail',
  components: { DictTag, ListLoadError, NoticeContent },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    return {
      scope,
      DICT_TYPES,
      writeReadRequest: markNoticeRead,
      refreshUnread: refreshNoticeUnread,
    }
  },
  data() {
    return {
      status: 'loading' as 'loading' | 'ready' | 'missing' | 'failed',
      error: '',
      detail: null as NoticeDetailView | null,
      writtenIds: [] as string[],
      sequence: 0,
    }
  },
  computed: {
    noticeId(): string {
      const value = this.$route.params.id
      return Array.isArray(value) ? value[0] || '' : value || ''
    },
  },
  watch: {
    noticeId: {
      immediate: true,
      handler() {
        void this.load()
      },
    },
  },
  methods: {
    async load() {
      const id = this.noticeId
      if (!id) {
        this.status = 'missing'
        this.detail = null
        return
      }
      const number = ++this.sequence
      this.status = 'loading'
      this.error = ''
      try {
        const response = await this.scope.request<NoticeDetailRecord | null>(
          detailRequest(id),
          () => number === this.sequence && this.noticeId === id,
        )
        if (number !== this.sequence) return
        const data = response?.data
        if (!data) {
          this.detail = null
          this.status = 'missing'
          return
        }
        this.detail = presentDetail(data)
        this.status = 'ready'
        void this.writeRead(id)
      } catch (failure) {
        if (number !== this.sequence) return
        this.status = 'failed'
        this.error =
          failure && typeof failure === 'object' && 'message' in failure
            ? String((failure as { message?: unknown }).message || '')
            : ''
      }
    },
    async writeRead(id: string) {
      if (this.writtenIds.includes(id)) return
      this.writtenIds.push(id)
      try {
        await this.writeReadRequest(id)
        this.refreshUnread()
      } catch {
        this.writtenIds = this.writtenIds.filter((value) => value !== id)
      }
    },
    backToNotice() {
      void this.$router.push(PATHS.NOTICE)
    },
  },
})
</script>

<style lang="scss" scoped>
.notice-detail {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.notice-loading {
  margin: 24px 0;
  color: #909399;
}

.notice-missing {
  padding: 32px 0 16px;
}

.notice-missing-actions {
  text-align: center;
}

.notice-card {
  max-width: 860px;
}

.notice-head {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.notice-title {
  margin: 8px 0;
  font-size: 20px;
  line-height: 1.4;
  color: #303133;
  overflow-wrap: anywhere;
}

.notice-meta {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

@media (max-width: 768px) {
  .notice-detail {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>

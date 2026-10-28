<template>
  <div class="work-list-page">
    <h2 class="page-title">{{ definition.title }}</h2>

    <div v-loading="view.status === 'loading'" class="list-container">
      <ListLoadError :status="view.status" :error="view.error" @retry="retry" />

      <el-empty
        v-if="view.status === 'ready' && rows.length === 0"
        :description="definition.emptyDescription"
      />

      <div v-for="item in rows" :key="item.rowKey" class="work-list-item" @click="openDetail(item)">
        <div class="item-header">
          <h3>{{ item.title }}</h3>
          <span class="time">{{ item.createTime }}</span>
        </div>
        <div class="item-meta">
          <span v-if="item.name">发起人：{{ item.name }}</span>
          <span v-if="item.processTypeName">审批类型：{{ item.processTypeName }}</span>
        </div>
        <p v-if="item.relationLabel" class="item-relation">{{ item.relationLabel }}</p>
        <p v-if="item.progressLabel" class="item-progress">{{ item.progressLabel }}</p>
        <div v-if="item.summaryFields.length" class="item-fields">
          <p v-for="field in item.summaryFields" :key="field.key">
            {{ field.key }}：<span v-html="field.value" />
          </p>
        </div>
        <div class="item-status">
          <el-tag size="small" :type="item.statusTagType">
            {{ item.statusLabel }}
          </el-tag>
        </div>
      </div>

      <div v-if="rows.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="view.page"
          :page-size="view.size"
          :total="view.total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'

import ListLoadError from '@/components/ListLoadError.vue'
import { createListRead, type ListReadOpening, type NoListQuery } from '@/list-read'
import { openRequestScope } from '@/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { ProcessInstanceRecord, PresentedWorkListRecord } from '@/types/domain'
import {
  presentWorkList,
  workListDefinition,
  workListRequest,
  type WorkListKind,
} from '@/work/work-list'

type WorkListOpening = ListReadOpening<ProcessInstanceRecord, NoListQuery>

export default defineComponent({
  name: 'WorkListPage',
  components: { ListLoadError },
  setup() {
    const route = useRoute()
    const userStore = useUserStore()

    function kindFromRoute(): WorkListKind {
      return route.meta.workListKind as WorkListKind
    }

    function open(kind: WorkListKind): WorkListOpening {
      const recognition = userStore.recognition
      if (!recognition) throw new Error('认出 module 尚未接入')
      return createListRead<ProcessInstanceRecord, NoListQuery>({
        openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
        toRequest: ({ page, size }) => workListRequest(kind, page, size),
      })
    }

    const kind = computed(kindFromRoute)
    const opening = shallowRef<WorkListOpening>(open(kind.value))
    const view = computed(() => opening.value.view)
    const definition = computed(() => workListDefinition(kind.value))
    const rows = computed<PresentedWorkListRecord[]>(
      () => presentWorkList(kind.value, view.value).records,
    )

    watch(kind, (next) => {
      opening.value.release()
      opening.value = open(next)
    })
    onBeforeUnmount(() => opening.value.release())

    return { opening, view, definition, rows }
  },
  methods: {
    handlePageChange(page: number) {
      void this.opening.read({ page })
    },
    retry() {
      void this.opening.read()
    },
    openDetail(item: PresentedWorkListRecord) {
      this.$router.push(item.detailPath)
    },
  },
})
</script>

<style lang="scss" scoped>
.work-list-page {
  .page-title {
    margin: 0 0 16px;
    font-size: 20px;
    color: #303133;
  }
}

.work-list-item {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: pointer;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

.item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: #303133;
    flex: 1;
  }

  .time {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #909399;
}

.item-relation,
.item-progress {
  margin: 4px 0;
  font-size: 13px;
  color: #606266;
}

.item-fields {
  font-size: 14px;
  color: #606266;

  p {
    margin: 4px 0;
    line-height: 1.5;
  }

  ::v-deep img {
    max-width: 100%;
    height: auto;
  }
}

.item-status {
  margin-top: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .item-header {
    flex-direction: column;
  }
}
</style>

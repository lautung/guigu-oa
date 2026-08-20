import { createRouter, createWebHistory } from 'vue-router'
import { getDefaultPostLoginRoute, ROUTES } from '@/navigation/access-policy'
import { WORK_LIST_KINDS } from '@/work/work-list'

const workChildren = [
  {
    path: 'start',
    name: 'WorkStart',
    component: () => import('@/views/work/start/index.vue'),
    meta: { title: '发起审批' }
  },
  {
    path: 'start/:processTemplateId',
    name: 'WorkStartApply',
    component: () => import('@/views/work/start/apply.vue'),
    meta: { title: '发起审批' }
  },
  {
    path: 'pending',
    name: 'Pending',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '待处理', workListKind: WORK_LIST_KINDS.PENDING }
  },
  {
    path: 'pending/:id/:taskId',
    name: 'PendingDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', workListKind: WORK_LIST_KINDS.PENDING }
  },
  {
    path: 'processed',
    name: 'WorkProcessed',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '已处理', workListKind: WORK_LIST_KINDS.PROCESSED }
  },
  {
    path: 'processed/:id',
    name: 'ProcessedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', workListKind: WORK_LIST_KINDS.PROCESSED }
  },
  {
    path: 'started',
    name: 'WorkStarted',
    component: () => import('@/views/work/started/index.vue'),
    meta: { title: '已发起' }
  },
  {
    path: 'started/:id',
    name: 'StartedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情' }
  },
  {
    path: 'profile',
    name: 'WorkProfile',
    component: () => import('@/views/work/profile/index.vue'),
    meta: { title: '基本信息' }
  }
]

const configChildren = [
  {
    path: 'sysUser',
    name: 'SysUser',
    component: () => import('@/views/config/sysUser/index.vue'),
    meta: { title: '用户管理' }
  },
  {
    path: 'sysRole',
    name: 'SysRole',
    component: () => import('@/views/config/sysRole/index.vue'),
    meta: { title: '角色管理' }
  },
  {
    path: 'sysMenu',
    name: 'SysMenu',
    component: () => import('@/views/config/sysMenu/index.vue'),
    meta: { title: '菜单管理' }
  },
  {
    path: 'assignAuth',
    name: 'AssignAuth',
    component: () => import('@/views/config/sysRole/assignAuth.vue'),
    meta: { title: '菜单授权' },
    hidden: true
  }
]

const processSetChildren = [
  {
    path: 'processType',
    name: 'ProcessType',
    component: () => import('@/views/config/processType/index.vue'),
    meta: { title: '审批类型' }
  },
  {
    path: 'processTemplate',
    name: 'ProcessTemplate',
    component: () => import('@/views/config/processTemplate/index.vue'),
    meta: { title: '审批模板' }
  },
  {
    path: 'templateSet',
    name: 'TemplateSet',
    component: () => import('@/views/config/processTemplate/templateSet.vue'),
    meta: { title: '审批模板设置' },
    hidden: true
  }
]

export const constantRoutes = [
  {
    path: ROUTES.LOGIN,
    component: () => import('@/views/login/index.vue'),
    hidden: true
  },
  {
    path: ROUTES.MOBILE_CONFIG_BLOCK,
    component: () => import('@/views/mobile-config-block/index.vue'),
    hidden: true
  },
  {
    path: '/',
    redirect: getDefaultPostLoginRoute()
  },
  {
    path: '/work',
    component: () => import('@/layout/WorkLayout.vue'),
    redirect: getDefaultPostLoginRoute(),
    children: workChildren
  },
  {
    path: '/system',
    component: () => import('@/layout/ConfigLayout.vue'),
    redirect: '/system/sysUser',
    children: configChildren
  },
  {
    path: '/processSet',
    component: () => import('@/layout/ConfigLayout.vue'),
    redirect: '/processSet/processType',
    children: processSetChildren
  },
  {
    path: '/processMgr',
    component: () => import('@/layout/ConfigLayout.vue'),
    redirect: '/processMgr/process',
    children: [
      {
        path: 'process',
        name: 'ProcessMgr',
        component: () => import('@/views/config/processInstance/index.vue'),
        meta: { title: '审批实例查询' }
      },
      {
        path: 'process/:id',
        name: 'ProcessMgrDetail',
        component: () => import('@/views/work/pending/detail.vue'),
        meta: { title: '审批详情' },
        hidden: true
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: constantRoutes
})

export default router

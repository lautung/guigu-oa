import Vue from 'vue'
import Router from 'vue-router'
import { getDefaultPostLoginRoute, ROUTES } from '@/navigation/access-policy'

Vue.use(Router)

const workChildren = [
  {
    path: 'start',
    name: 'WorkStart',
    component: () => import('@/views/work/start/index'),
    meta: { title: '发起审批' }
  },
  {
    path: 'pending',
    name: 'Pending',
    component: () => import('@/views/work/pending/index'),
    meta: { title: '待处理' }
  },
  {
    path: 'pending/:id/:taskId',
    name: 'PendingDetail',
    component: () => import('@/views/work/pending/detail'),
    meta: { title: '审批详情' }
  },
  {
    path: 'processed',
    name: 'WorkProcessed',
    component: () => import('@/views/work/processed/index'),
    meta: { title: '已处理' }
  },
  {
    path: 'processed/:id/:taskId',
    name: 'ProcessedDetail',
    component: () => import('@/views/work/pending/detail'),
    meta: { title: '审批详情' }
  },
  {
    path: 'started',
    name: 'WorkStarted',
    component: () => import('@/views/work/started/index'),
    meta: { title: '已发起' }
  },
  {
    path: 'started/:id/:taskId',
    name: 'StartedDetail',
    component: () => import('@/views/work/pending/detail'),
    meta: { title: '审批详情' }
  },
  {
    path: 'profile',
    name: 'WorkProfile',
    component: () => import('@/views/work/StubPage'),
    meta: { title: '基本信息' }
  }
]

const configPlaceholder = () => import('@/views/config/Placeholder')

const configChildren = [
  {
    path: 'sysUser',
    name: 'SysUser',
    component: configPlaceholder,
    meta: { title: '用户管理' }
  },
  {
    path: 'sysRole',
    name: 'SysRole',
    component: configPlaceholder,
    meta: { title: '角色管理' }
  },
  {
    path: 'sysMenu',
    name: 'SysMenu',
    component: configPlaceholder,
    meta: { title: '菜单管理' }
  },
  {
    path: 'assignAuth',
    name: 'AssignAuth',
    component: configPlaceholder,
    meta: { title: '菜单授权' },
    hidden: true
  }
]

const processSetChildren = [
  {
    path: 'processType',
    name: 'ProcessType',
    component: () => import('@/views/config/processType/index'),
    meta: { title: '审批类型' }
  },
  {
    path: 'processTemplate',
    name: 'ProcessTemplate',
    component: () => import('@/views/config/processTemplate/index'),
    meta: { title: '审批模板' }
  },
  {
    path: 'templateSet',
    name: 'TemplateSet',
    component: () => import('@/views/config/processTemplate/templateSet'),
    meta: { title: '审批模板设置' },
    hidden: true
  }
]

export const constantRoutes = [
  {
    path: ROUTES.LOGIN,
    component: () => import('@/views/login/index'),
    hidden: true
  },
  {
    path: ROUTES.MOBILE_CONFIG_BLOCK,
    component: () => import('@/views/mobile-config-block/index'),
    hidden: true
  },
  {
    path: '/',
    redirect: getDefaultPostLoginRoute()
  },
  {
    path: '/work',
    component: () => import('@/layout/WorkLayout'),
    redirect: getDefaultPostLoginRoute(),
    children: workChildren
  },
  {
    path: '/system',
    component: () => import('@/layout/ConfigLayout'),
    redirect: '/system/sysUser',
    children: configChildren
  },
  {
    path: '/processSet',
    component: () => import('@/layout/ConfigLayout'),
    redirect: '/processSet/processType',
    children: processSetChildren
  },
  {
    path: '/processMgr',
    component: () => import('@/layout/ConfigLayout'),
    redirect: '/processMgr/process',
    children: [
      {
        path: 'process',
        name: 'ProcessMgr',
        component: configPlaceholder,
        meta: { title: '审批实例查询' }
      }
    ]
  }
]

const createRouter = () => new Router({
  mode: 'history',
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher
}

export default router

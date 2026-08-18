import Vue from 'vue'
import Router from 'vue-router'
import { getDefaultPostLoginRoute } from '@/navigation/access-policy'

Vue.use(Router)

export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login/index'),
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
    children: [
      {
        path: 'pending',
        name: 'Pending',
        component: () => import('@/views/work/pending/index'),
        meta: { title: '待处理' }
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

export const me = { name: '张伟', dept: '人事部', initial: '张' }

export const nav = [
  { key: 'workbench', title: '工作台', icon: 'HomeFilled' },
  {
    key: 'work',
    title: '办事',
    icon: 'Tickets',
    children: [
      { key: 'start', title: '发起审批', icon: 'EditPen' },
      { key: 'pending', title: '待处理', icon: 'AlarmClock' },
      { key: 'done', title: '已处理', icon: 'CircleCheck' },
      { key: 'mine', title: '已发起', icon: 'Promotion' },
      { key: 'cc', title: '抄送我的', icon: 'Share' },
      { key: 'profile', title: '基本信息', icon: 'User' },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    icon: 'Setting',
    children: [
      { key: 'sysUser', title: '用户管理', icon: 'User' },
      { key: 'sysRole', title: '角色管理', icon: 'Avatar' },
      { key: 'sysMenu', title: '菜单管理', icon: 'Menu' },
      { key: 'sysDept', title: '部门管理', icon: 'OfficeBuilding' },
      { key: 'sysPost', title: '岗位管理', icon: 'Postcard' },
      { key: 'sysDict', title: '字典管理', icon: 'Collection' },
      { key: 'sysConfig', title: '参数设置', icon: 'Tools' },
      { key: 'notice', title: '通知公告', icon: 'Bell' },
      {
        key: 'logs',
        title: '日志管理',
        icon: 'Document',
        children: [
          { key: 'operlog', title: '操作日志' },
          { key: 'loginlog', title: '登录日志' },
        ],
      },
      {
        key: 'approvalSetting',
        title: '审批设置',
        icon: 'SetUp',
        children: [
          { key: 'processType', title: '审批类型' },
          { key: 'template', title: '审批模板' },
          { key: 'templateSetting', title: '模板设置' },
        ],
      },
      {
        key: 'approvalAdmin',
        title: '审批管理',
        icon: 'Files',
        children: [{ key: 'instanceList', title: '审批实例查询' }],
      },
    ],
  },
  {
    key: 'monitor',
    title: '系统监控',
    icon: 'Monitor',
    children: [
      { key: 'online', title: '在线用户', icon: 'Connection' },
      { key: 'server', title: '服务监控', icon: 'Cpu' },
      { key: 'cache', title: '缓存监控', icon: 'Coin' },
      { key: 'cacheList', title: '缓存列表', icon: 'List' },
    ],
  },
  {
    key: 'tool',
    title: '系统工具',
    icon: 'Briefcase',
    children: [{ key: 'apiDocs', title: '接口文档', icon: 'Document' }],
  },
]

export const flatNav = nav.flatMap((module) => {
  if (!module.children) {
    return [{ key: module.key, title: module.title, moduleKey: module.key, trail: [module.title] }]
  }
  return module.children.flatMap((child) => {
    if (!child.children) {
      return [
        {
          key: child.key,
          title: child.title,
          moduleKey: module.key,
          groupTitle: '',
          trail: [module.title, child.title],
        },
      ]
    }
    return child.children.map((leaf) => ({
      key: leaf.key,
      title: leaf.title,
      moduleKey: module.key,
      groupTitle: child.title,
      trail: [module.title, child.title, leaf.title],
    }))
  })
})

export function findNavItem(key) {
  return flatNav.find((item) => item.key === key)
}

export function moduleOf(key) {
  return nav.find((module) => module.key === key)
}

export const announcements = [
  {
    id: 1,
    title: '关于 11 月社保缴费基数调整的通知',
    type: '通知',
    time: '今天 09:12',
    unread: true,
  },
  { id: 2, title: '办公区消防演练安排', type: '公告', time: '昨天 17:40', unread: true },
  { id: 3, title: 'Q4 差旅报销截止时间提醒', type: '通知', time: '10-30 14:05', unread: false },
]

export const instances = [
  {
    id: 'SP202611020031',
    title: '10 月加班费申请',
    type: '加班申请',
    applicant: '李静',
    dept: '财务部',
    status: '待审批',
    node: '部门经理',
    time: '11-02 10:24',
    cost: '2 小时',
  },
  {
    id: 'SP202611020028',
    title: '客户拜访差旅报销',
    type: '报销申请',
    applicant: '王强',
    dept: '市场部',
    status: '待审批',
    node: '财务复核',
    time: '11-02 09:47',
    cost: '6 小时',
  },
  {
    id: 'SP202611010096',
    title: '年假申请（11-05 至 11-07）',
    type: '请假申请',
    applicant: '陈晨',
    dept: '研发部',
    status: '已通过',
    node: '已完成',
    time: '11-01 16:32',
    cost: '1 天',
  },
  {
    id: 'SP202611010094',
    title: '研发部团建采购申请',
    type: '采购申请',
    applicant: '刘洋',
    dept: '研发部',
    status: '待审批',
    node: '总经理',
    time: '11-01 15:08',
    cost: '3 小时',
  },
  {
    id: 'SP202611010089',
    title: '用印申请（合同专用章）',
    type: '用印申请',
    applicant: '赵敏',
    dept: '行政部',
    status: '已拒绝',
    node: '已结束',
    time: '11-01 11:21',
    cost: '5 小时',
  },
  {
    id: 'SP202610310075',
    title: '10 月市内交通费报销',
    type: '报销申请',
    applicant: '李静',
    dept: '财务部',
    status: '已通过',
    node: '已完成',
    time: '10-31 17:02',
    cost: '2 天',
  },
  {
    id: 'SP202610310071',
    title: '出差申请（上海 · 11-12）',
    type: '出差申请',
    applicant: '王强',
    dept: '市场部',
    status: '已撤回',
    node: '已结束',
    time: '10-31 14:36',
    cost: '—',
  },
  {
    id: 'SP202610300062',
    title: '病假申请（10-30 下午）',
    type: '请假申请',
    applicant: '陈晨',
    dept: '研发部',
    status: '已通过',
    node: '已完成',
    time: '10-30 09:15',
    cost: '8 小时',
  },
  {
    id: 'SP202610300058',
    title: '办公用品补充采购',
    type: '采购申请',
    applicant: '赵敏',
    dept: '行政部',
    status: '待审批',
    node: '行政主管',
    time: '10-30 08:52',
    cost: '1 天',
  },
  {
    id: 'SP202610290049',
    title: '周末值班加班申请',
    type: '加班申请',
    applicant: '刘洋',
    dept: '研发部',
    status: '已通过',
    node: '已完成',
    time: '10-29 18:10',
    cost: '3 天',
  },
  {
    id: 'SP202610290044',
    title: '年度体检费用报销',
    type: '报销申请',
    applicant: '张伟',
    dept: '人事部',
    status: '已完成',
    node: '已完成',
    time: '10-29 10:05',
    cost: '4 天',
  },
  {
    id: 'SP202610280037',
    title: '国庆值班调休申请',
    type: '请假申请',
    applicant: '李静',
    dept: '财务部',
    status: '已通过',
    node: '已完成',
    time: '10-28 15:44',
    cost: '5 天',
  },
]

export const pendingRows = instances.filter((row) => row.status === '待审批').slice(0, 5)

export const myRows = [
  { id: 'SP202610290044', title: '年度体检费用报销', status: '已完成', time: '10-29' },
  { id: 'SP202610240021', title: '年假申请（10-27 至 10-28）', status: '已通过', time: '10-24' },
  { id: 'SP202610190013', title: '部门团建费用申请', status: '已拒绝', time: '10-19' },
]

export const statusTag = {
  待审批: 'warning',
  已通过: 'success',
  已拒绝: 'danger',
  已撤回: 'info',
  已完成: 'success',
}

export const typeColor = {
  请假申请: '#409eff',
  报销申请: '#e6a23c',
  采购申请: '#12a594',
  出差申请: '#5b6ef5',
  用印申请: '#f56c6c',
  加班申请: '#909399',
}

import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'ProjectList',
    component: () => import('../views/ProjectList.vue')
  },
  {
    path: '/project/:id',
    name: 'ProjectDetail',
    component: () => import('../views/ProjectDetail.vue'),
    props: true
  },
  {
    path: '/project/:id/requirements',
    name: 'RequirementList',
    component: () => import('../views/RequirementList.vue'),
    props: true
  },
  {
    path: '/project/:id/requirements/:reqId',
    name: 'RequirementDetail',
    component: () => import('../views/RequirementDetail.vue'),
    props: true
  },
  {
    path: '/project/:id/bugs',
    name: 'BugList',
    component: () => import('../views/BugList.vue'),
    props: true
  },
  {
    path: '/project/:id/bugs/:bugId',
    name: 'BugDetail',
    component: () => import('../views/BugDetail.vue'),
    props: true
  },
  {
    path: '/project/:id/design/:designId',
    name: 'DesignProposalDetail',
    component: () => import('../views/DesignProposalDetail.vue'),
    props: true
  },
  {
    path: '/project/:id/test/:testCaseId',
    name: 'TestCaseDetail',
    component: () => import('../views/TestCaseDetail.vue'),
    props: true
  },
  {
    path: '/notifications',
    name: 'NotificationList',
    component: () => import('../views/NotificationList.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

<template>
  <div class="page-container">
    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="project">
      <div class="project-header">
        <button class="back-btn" @click="$router.push('/')">
          <svg width="14" height="14" viewBox="0 0 16 16">
            <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
        <div class="project-info">
          <h1 class="project-name">{{ project.name }}</h1>
        </div>
        <ATag :variant="statusVariant(project.status)" size="small">{{ statusLabel(project.status) }}</ATag>
      </div>

      <ATabs v-model="activeTab" :tabs="tabList">
        <div v-if="activeTab === 'requirements'">
          <RequirementList :project-id="id" embedded />
        </div>
        <div v-else-if="activeTab === 'bugs'">
          <BugList :project-id="id" embedded />
        </div>
        <div v-else-if="activeTab === 'designs'">
          <DesignProposalList :project-id="id" embedded />
        </div>
        <div v-else-if="activeTab === 'tests'">
          <TestCaseList :project-id="id" embedded />
        </div>
      </ATabs>
    </template>

    <AEmpty v-else title="项目不存在" description="请检查项目ID是否正确">
      <AButton variant="primary" size="small" @click="$router.push('/')">返回项目列表</AButton>
    </AEmpty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ATag from '../components/ATag.vue'
import ATabs from '../components/ATabs.vue'
import AEmpty from '../components/AEmpty.vue'
import AButton from '../components/AButton.vue'
import RequirementList from './RequirementList.vue'
import BugList from './BugList.vue'
import DesignProposalList from './DesignProposalList.vue'
import TestCaseList from './TestCaseList.vue'
import { projectApi } from '../api'

const props = defineProps({ id: { type: [String, Number], required: true } })

const project = ref(null)
const loading = ref(false)
const activeTab = ref('requirements')

const tabList = [
  { key: 'requirements', label: '需求' },
  { key: 'designs', label: '设计方案' },
  { key: 'tests', label: '测试用例' },
  { key: 'bugs', label: 'Bug' }
]

const statusMap = {
  active: { label: '进行中', variant: 'primary' },
  completed: { label: '已完成', variant: 'success' },
  paused: { label: '已暂停', variant: 'warning' },
  archived: { label: '已归档', variant: 'default' }
}

function statusLabel(status) {
  return statusMap[status]?.label || status || '未知'
}

function statusVariant(status) {
  return statusMap[status]?.variant || 'default'
}

async function fetchProject() {
  loading.value = true
  try {
    const res = await projectApi.get(props.id)
    project.value = res.data || res
  } catch {
    project.value = null
  } finally {
    loading.value = false
  }
}

onMounted(fetchProject)
</script>

<style scoped>
.project-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border-light);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.back-btn:hover {
  background: var(--color-primary-light);
}

.project-info {
  flex: 1;
  min-width: 0;
}

.project-name {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

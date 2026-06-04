<template>
  <div class="page-container">
    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="bug">
      <div class="detail-header">
        <button class="back-btn" @click="$router.push(`/project/${projectId}/bugs`)">
          <svg width="14" height="14" viewBox="0 0 16 16">
            <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
      </div>

      <ACard>
        <div class="detail-title-row">
          <h2 class="detail-title">{{ bug.title }}</h2>
          <ATag :variant="statusVariant(bug.status)" size="small">{{ statusLabel(bug.status) }}</ATag>
        </div>
        <div class="detail-meta">
          <ATag :variant="severityVariant(bug.severity)" size="small">{{ severityLabel(bug.severity) }}</ATag>
          <span>{{ bug.creator || '—' }}</span>
          <span>{{ formatDate(bug.createdAt) }}</span>
        </div>
        <div class="divider"></div>
        <div class="detail-content">
          <p>{{ bug.description || '暂无详细描述' }}</p>
        </div>
      </ACard>

      <!-- Status Flow -->
      <ACard class="status-flow-card">
        <div class="status-flow">
          <div
            v-for="step in statusFlow"
            :key="step.key"
            class="status-step"
            :class="{ 'status-step--active': step.key === bug.status, 'status-step--done': isStepDone(step.key) }"
          >
            <div class="status-step__dot"></div>
            <span class="status-step__label">{{ step.label }}</span>
          </div>
        </div>
        <div class="status-actions">
          <AButton
            v-if="bug.status === 'open'"
            variant="primary"
            size="small"
            @click="updateStatus('in_progress')"
          >
            开始处理
          </AButton>
          <AButton
            v-if="bug.status === 'in_progress'"
            variant="primary"
            size="small"
            @click="updateStatus('fixed')"
          >
            标记已修复
          </AButton>
          <AButton
            v-if="bug.status === 'fixed'"
            variant="primary"
            size="small"
            @click="updateStatus('closed')"
          >
            关闭 Bug
          </AButton>
          <AButton
            v-if="bug.status === 'in_progress' || bug.status === 'fixed'"
            variant="danger"
            size="small"
            @click="updateStatus('open')"
          >
            重新打开
          </AButton>
        </div>
      </ACard>
    </template>

    <AEmpty v-else title="Bug 不存在" description="请检查 Bug ID 是否正确">
      <AButton variant="primary" size="small" @click="$router.push(`/project/${projectId}/bugs`)">返回 Bug 列表</AButton>
    </AEmpty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ACard from '../components/ACard.vue'
import ATag from '../components/ATag.vue'
import AEmpty from '../components/AEmpty.vue'
import AButton from '../components/AButton.vue'
import { bugApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  bugId: { type: [String, Number], required: true }
})

const bug = ref(null)
const loading = ref(false)

const statusFlow = [
  { key: 'open', label: '待处理' },
  { key: 'in_progress', label: '处理中' },
  { key: 'fixed', label: '已修复' },
  { key: 'closed', label: '已关闭' }
]

const statusOrder = ['open', 'in_progress', 'fixed', 'closed']

const statusMap = {
  open: { label: '待处理', variant: 'danger' },
  in_progress: { label: '处理中', variant: 'warning' },
  fixed: { label: '已修复', variant: 'success' },
  closed: { label: '已关闭', variant: 'default' }
}

const severityMap = {
  critical: { label: '致命', variant: 'danger' },
  major: { label: '严重', variant: 'warning' },
  minor: { label: '一般', variant: 'info' },
  trivial: { label: '轻微', variant: 'default' }
}

function statusLabel(s) { return statusMap[s]?.label || s || '未知' }
function statusVariant(s) { return statusMap[s]?.variant || 'default' }
function severityLabel(s) { return severityMap[s]?.label || s || '未知' }
function severityVariant(s) { return severityMap[s]?.variant || 'default' }

function isStepDone(key) {
  if (!bug.value) return false
  const currentIndex = statusOrder.indexOf(bug.value.status)
  const stepIndex = statusOrder.indexOf(key)
  return stepIndex < currentIndex
}

function formatDate(dateStr) {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fetchBug() {
  loading.value = true
  try {
    const res = await bugApi.get(props.projectId, props.bugId)
    bug.value = res.data || res
  } catch {
    bug.value = null
  } finally {
    loading.value = false
  }
}

async function updateStatus(status) {
  try {
    await bugApi.update(props.bugId, { status })
    await fetchBug()
  } catch {
    // handle error
  }
}

onMounted(fetchBug)
</script>

<style scoped>
.detail-header {
  margin-bottom: var(--space-3);
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

.detail-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.detail-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  flex: 1;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.detail-content {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--color-text-primary);
}

.status-flow-card {
  margin-top: var(--space-4);
}

.status-flow {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: var(--space-4);
}

.status-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  position: relative;
}

.status-step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 6px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: var(--color-border-light);
}

.status-step--done:not(:last-child)::after {
  background: var(--color-success);
}

.status-step__dot {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-full);
  background: var(--color-border-light);
  border: 2px solid var(--color-border);
  position: relative;
  z-index: 1;
  margin-bottom: var(--space-1);
  transition: all var(--transition-fast);
}

.status-step--done .status-step__dot {
  background: var(--color-success);
  border-color: var(--color-success);
}

.status-step--active .status-step__dot {
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.2);
}

.status-step__label {
  font-size: 10px;
  color: var(--color-text-tertiary);
}

.status-step--active .status-step__label {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.status-step--done .status-step__label {
  color: var(--color-success);
}

.status-actions {
  display: flex;
  gap: var(--space-2);
}

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

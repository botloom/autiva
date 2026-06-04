<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">项目</h1>
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建项目</AButton>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>

    <div v-else-if="projects.length" class="grid-container">
      <ACard
        v-for="project in projects"
        :key="project.id"
        hoverable
        @click="goToProject(project.id)"
      >
        <template #header>
          <div class="project-card-header">
            <span class="project-card-name">{{ project.name }}</span>
            <ATag :variant="statusVariant(project.status)" size="small">{{ statusLabel(project.status) }}</ATag>
          </div>
        </template>
        <p class="project-card-desc">{{ project.description || '暂无描述' }}</p>
        <div class="project-card-meta">
          <span>{{ project.owner || '—' }}</span>
          <span>{{ formatDate(project.createdAt) }}</span>
        </div>
      </ACard>
    </div>

    <AEmpty v-else title="暂无项目" description="点击「新建项目」创建你的第一个项目">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建项目</AButton>
    </AEmpty>

    <!-- Create Project Modal -->
    <AModal v-model="showCreateModal" title="新建项目">
      <div class="form-group">
        <AInput v-model="form.name" label="项目名称" placeholder="输入项目名称" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.description" label="项目描述" placeholder="输入项目描述" :rows="3" />
      </div>
      <template #footer>
        <AButton variant="secondary" @click="showCreateModal = false">取消</AButton>
        <AButton variant="primary" :loading="submitting" @click="createProject">创建</AButton>
      </template>
    </AModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AButton from '../components/AButton.vue'
import ACard from '../components/ACard.vue'
import ATag from '../components/ATag.vue'
import AEmpty from '../components/AEmpty.vue'
import AModal from '../components/AModal.vue'
import AInput from '../components/AInput.vue'
import ATextarea from '../components/ATextarea.vue'
import { projectApi } from '../api'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const showCreateModal = ref(false)
const submitting = ref(false)
const form = ref({ name: '', description: '' })

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

function formatDate(dateStr) {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function goToProject(id) {
  router.push(`/project/${id}`)
}

async function fetchProjects() {
  loading.value = true
  try {
    const res = await projectApi.list()
    projects.value = res.data || res || []
  } catch {
    projects.value = []
  } finally {
    loading.value = false
  }
}

async function createProject() {
  if (!form.value.name.trim()) return
  submitting.value = true
  try {
    await projectApi.create(form.value)
    showCreateModal.value = false
    form.value = { name: '', description: '' }
    await fetchProjects()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

onMounted(fetchProjects)
</script>

<style scoped>
.project-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.project-card-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-card-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: var(--line-height-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: var(--space-2);
}

.project-card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.form-group {
  margin-bottom: var(--space-3);
}

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

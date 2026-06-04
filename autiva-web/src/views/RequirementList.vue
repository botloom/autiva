<template>
  <div :class="{ 'page-container': !embedded }">
    <div v-if="!embedded" class="page-header">
      <h1 class="page-title">需求</h1>
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建需求</AButton>
    </div>

    <div v-else class="embedded-header">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建需求</AButton>
    </div>

    <div class="filter-bar">
      <ASelect
        v-model="filterStatus"
        :options="statusOptions"
        placeholder="全部状态"
        size="small"
      />
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>

    <div v-else-if="filteredRequirements.length" class="list-container">
      <div
        v-for="req in filteredRequirements"
        :key="req.id"
        class="list-item"
        @click="goToDetail(req.id)"
      >
        <div class="list-item-content">
          <div class="list-item-title">{{ req.title }}</div>
          <div class="list-item-meta">
            <ATag :variant="reqStatusVariant(req.status)" size="small">{{ reqStatusLabel(req.status) }}</ATag>
            <span v-if="req.priority">{{ req.priority }}</span>
            <span>{{ formatDate(req.createdAt) }}</span>
          </div>
        </div>
        <div class="list-item-actions">
          <ABadge v-if="req.reviewCount" :count="req.reviewCount" variant="primary" />
        </div>
      </div>
    </div>

    <AEmpty v-else title="暂无需求" description="点击「新建需求」创建第一个需求">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建需求</AButton>
    </AEmpty>

    <!-- Create Requirement Modal -->
    <AModal v-model="showCreateModal" title="新建需求">
      <div class="form-group">
        <AInput v-model="form.title" label="需求标题" placeholder="输入需求标题" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.description" label="需求描述" placeholder="输入需求描述" :rows="3" />
      </div>
      <div class="form-group">
        <ASelect v-model="form.priority" label="优先级" :options="priorityOptions" />
      </div>
      <template #footer>
        <AButton variant="secondary" @click="showCreateModal = false">取消</AButton>
        <AButton variant="primary" :loading="submitting" @click="createRequirement">创建</AButton>
      </template>
    </AModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AButton from '../components/AButton.vue'
import ATag from '../components/ATag.vue'
import ABadge from '../components/ABadge.vue'
import AEmpty from '../components/AEmpty.vue'
import AModal from '../components/AModal.vue'
import AInput from '../components/AInput.vue'
import ATextarea from '../components/ATextarea.vue'
import ASelect from '../components/ASelect.vue'
import { requirementApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  embedded: Boolean
})

const router = useRouter()
const requirements = ref([])
const loading = ref(false)
const showCreateModal = ref(false)
const submitting = ref(false)
const filterStatus = ref('')
const form = ref({ title: '', description: '', priority: 'medium' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'draft', label: '草稿' },
  { value: 'reviewing', label: '评审中' },
  { value: 'approved', label: '已通过' },
  { value: 'rejected', label: '已拒绝' }
]

const priorityOptions = [
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '紧急' }
]

const reqStatusMap = {
  draft: { label: '草稿', variant: 'default' },
  reviewing: { label: '评审中', variant: 'warning' },
  approved: { label: '已通过', variant: 'success' },
  rejected: { label: '已拒绝', variant: 'danger' }
}

function reqStatusLabel(status) {
  return reqStatusMap[status]?.label || status || '未知'
}

function reqStatusVariant(status) {
  return reqStatusMap[status]?.variant || 'default'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const filteredRequirements = computed(() => {
  if (!filterStatus.value) return requirements.value
  return requirements.value.filter(r => r.status === filterStatus.value)
})

function goToDetail(reqId) {
  router.push(`/project/${props.projectId}/requirements/${reqId}`)
}

async function fetchRequirements() {
  loading.value = true
  try {
    const res = await requirementApi.list(props.projectId)
    requirements.value = res.data || res || []
  } catch {
    requirements.value = []
  } finally {
    loading.value = false
  }
}

async function createRequirement() {
  if (!form.value.title.trim()) return
  submitting.value = true
  try {
    await requirementApi.create(props.projectId, form.value)
    showCreateModal.value = false
    form.value = { title: '', description: '', priority: 'medium' }
    await fetchRequirements()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

onMounted(fetchRequirements)
</script>

<style scoped>
.embedded-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-3);
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

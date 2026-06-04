<template>
  <div :class="{ 'page-container': !embedded }">
    <div v-if="!embedded" class="page-header">
      <h1 class="page-title">设计方案</h1>
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建设计方案</AButton>
    </div>

    <div v-else class="embedded-header">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建设计方案</AButton>
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

    <div v-else-if="filteredDesigns.length" class="list-container">
      <div
        v-for="design in filteredDesigns"
        :key="design.id"
        class="list-item"
        @click="goToDetail(design.id)"
      >
        <div class="list-item-content">
          <div class="list-item-title">{{ design.title }}</div>
          <div class="list-item-meta">
            <ATag :variant="statusVariant(design.status)" size="small">{{ statusLabel(design.status) }}</ATag>
            <span>{{ design.submitterId || '—' }}</span>
            <span>{{ formatDate(design.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <AEmpty v-else title="暂无设计方案" description="点击「新建设计方案」创建第一个设计方案">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建设计方案</AButton>
    </AEmpty>

    <!-- Create Design Proposal Modal -->
    <AModal v-model="showCreateModal" title="新建设计方案">
      <div class="form-group">
        <AInput v-model="form.title" label="方案标题" placeholder="输入设计方案标题" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.content" label="方案内容" placeholder="输入设计方案内容" :rows="4" />
      </div>
      <template #footer>
        <AButton variant="secondary" @click="showCreateModal = false">取消</AButton>
        <AButton variant="primary" :loading="submitting" @click="createDesign">创建</AButton>
      </template>
    </AModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AButton from '../components/AButton.vue'
import ATag from '../components/ATag.vue'
import AEmpty from '../components/AEmpty.vue'
import AModal from '../components/AModal.vue'
import AInput from '../components/AInput.vue'
import ATextarea from '../components/ATextarea.vue'
import ASelect from '../components/ASelect.vue'
import { designProposalApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  embedded: Boolean
})

const router = useRouter()
const designs = ref([])
const loading = ref(false)
const showCreateModal = ref(false)
const submitting = ref(false)
const filterStatus = ref('')
const form = ref({ title: '', content: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'IN_REVIEW', label: '评审中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' }
]

const statusMap = {
  DRAFT: { label: '草稿', variant: 'default' },
  SUBMITTED: { label: '已提交', variant: 'info' },
  IN_REVIEW: { label: '评审中', variant: 'warning' },
  APPROVED: { label: '已通过', variant: 'success' },
  REJECTED: { label: '已驳回', variant: 'danger' }
}

function statusLabel(status) {
  return statusMap[status]?.label || status || '未知'
}

function statusVariant(status) {
  return statusMap[status]?.variant || 'default'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const filteredDesigns = computed(() => {
  if (!filterStatus.value) return designs.value
  return designs.value.filter(d => d.status === filterStatus.value)
})

function goToDetail(designId) {
  router.push(`/project/${props.projectId}/design/${designId}`)
}

async function fetchDesigns() {
  loading.value = true
  try {
    const res = await designProposalApi.list(props.projectId)
    designs.value = res.data || res || []
  } catch {
    designs.value = []
  } finally {
    loading.value = false
  }
}

async function createDesign() {
  if (!form.value.title.trim()) return
  submitting.value = true
  try {
    await designProposalApi.create(props.projectId, form.value)
    showCreateModal.value = false
    form.value = { title: '', content: '' }
    await fetchDesigns()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

onMounted(fetchDesigns)
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

<template>
  <div :class="{ 'page-container': !embedded }">
    <div v-if="!embedded" class="page-header">
      <h1 class="page-title">Bug</h1>
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建 Bug</AButton>
    </div>

    <div v-else class="embedded-header">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建 Bug</AButton>
    </div>

    <div class="filter-bar">
      <ASelect v-model="filterStatus" :options="statusOptions" placeholder="全部状态" size="small" />
      <ASelect v-model="filterSeverity" :options="severityOptions" placeholder="全部严重程度" size="small" />
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>

    <div v-else-if="filteredBugs.length" class="list-container">
      <div
        v-for="bug in filteredBugs"
        :key="bug.id"
        class="list-item"
        @click="goToDetail(bug.id)"
      >
        <div class="list-item-content">
          <div class="list-item-title">{{ bug.title }}</div>
          <div class="list-item-meta">
            <ATag :variant="bugStatusVariant(bug.status)" size="small">{{ bugStatusLabel(bug.status) }}</ATag>
            <ATag :variant="severityVariant(bug.severity)" size="small">{{ severityLabel(bug.severity) }}</ATag>
            <span>{{ formatDate(bug.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <AEmpty v-else title="暂无 Bug" description="点击「新建 Bug」记录第一个缺陷">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建 Bug</AButton>
    </AEmpty>

    <!-- Create Bug Modal -->
    <AModal v-model="showCreateModal" title="新建 Bug">
      <div class="form-group">
        <AInput v-model="form.title" label="Bug 标题" placeholder="输入 Bug 标题" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.description" label="Bug 描述" placeholder="输入 Bug 描述及复现步骤" :rows="3" />
      </div>
      <div class="form-group">
        <ASelect v-model="form.severity" label="严重程度" :options="severityFormOptions" />
      </div>
      <template #footer>
        <AButton variant="secondary" @click="showCreateModal = false">取消</AButton>
        <AButton variant="primary" :loading="submitting" @click="createBug">创建</AButton>
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
import { bugApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  embedded: Boolean
})

const router = useRouter()
const bugs = ref([])
const loading = ref(false)
const showCreateModal = ref(false)
const submitting = ref(false)
const filterStatus = ref('')
const filterSeverity = ref('')
const form = ref({ title: '', description: '', severity: 'major' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'open', label: '待处理' },
  { value: 'in_progress', label: '处理中' },
  { value: 'fixed', label: '已修复' },
  { value: 'closed', label: '已关闭' }
]

const severityOptions = [
  { value: '', label: '全部严重程度' },
  { value: 'critical', label: '致命' },
  { value: 'major', label: '严重' },
  { value: 'minor', label: '一般' },
  { value: 'trivial', label: '轻微' }
]

const severityFormOptions = [
  { value: 'critical', label: '致命' },
  { value: 'major', label: '严重' },
  { value: 'minor', label: '一般' },
  { value: 'trivial', label: '轻微' }
]

const bugStatusMap = {
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

function bugStatusLabel(s) { return bugStatusMap[s]?.label || s || '未知' }
function bugStatusVariant(s) { return bugStatusMap[s]?.variant || 'default' }
function severityLabel(s) { return severityMap[s]?.label || s || '未知' }
function severityVariant(s) { return severityMap[s]?.variant || 'default' }

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const filteredBugs = computed(() => {
  return bugs.value.filter(b => {
    if (filterStatus.value && b.status !== filterStatus.value) return false
    if (filterSeverity.value && b.severity !== filterSeverity.value) return false
    return true
  })
})

function goToDetail(bugId) {
  router.push(`/project/${props.projectId}/bugs/${bugId}`)
}

async function fetchBugs() {
  loading.value = true
  try {
    const res = await bugApi.list(props.projectId)
    bugs.value = res.data || res || []
  } catch {
    bugs.value = []
  } finally {
    loading.value = false
  }
}

async function createBug() {
  if (!form.value.title.trim()) return
  submitting.value = true
  try {
    await bugApi.create(props.projectId, form.value)
    showCreateModal.value = false
    form.value = { title: '', description: '', severity: 'major' }
    await fetchBugs()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

onMounted(fetchBugs)
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

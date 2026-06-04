<template>
  <div :class="{ 'page-container': !embedded }">
    <div v-if="!embedded" class="page-header">
      <h1 class="page-title">测试用例</h1>
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建测试用例</AButton>
    </div>

    <div v-else class="embedded-header">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建测试用例</AButton>
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

    <div v-else-if="filteredTestCases.length" class="list-container">
      <div
        v-for="tc in filteredTestCases"
        :key="tc.id"
        class="list-item"
        @click="goToDetail(tc.id)"
      >
        <div class="list-item-content">
          <div class="list-item-title">{{ tc.title }}</div>
          <div class="list-item-meta">
            <ATag :variant="statusVariant(tc.status)" size="small">{{ statusLabel(tc.status) }}</ATag>
            <span>{{ tc.submitterId || '—' }}</span>
            <span>{{ formatDate(tc.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <AEmpty v-else title="暂无测试用例" description="点击「新建测试用例」创建第一个测试用例">
      <AButton variant="primary" size="small" @click="showCreateModal = true">新建测试用例</AButton>
    </AEmpty>

    <!-- Create Test Case Modal -->
    <AModal v-model="showCreateModal" title="新建测试用例">
      <div class="form-group">
        <AInput v-model="form.title" label="用例标题" placeholder="输入测试用例标题" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.steps" label="测试步骤" placeholder="输入测试步骤" :rows="3" />
      </div>
      <div class="form-group">
        <ATextarea v-model="form.expectedResult" label="预期结果" placeholder="输入预期结果" :rows="2" />
      </div>
      <template #footer>
        <AButton variant="secondary" @click="showCreateModal = false">取消</AButton>
        <AButton variant="primary" :loading="submitting" @click="createTestCase">创建</AButton>
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
import { testCaseApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  embedded: Boolean
})

const router = useRouter()
const testCases = ref([])
const loading = ref(false)
const showCreateModal = ref(false)
const submitting = ref(false)
const filterStatus = ref('')
const form = ref({ title: '', steps: '', expectedResult: '' })

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

const filteredTestCases = computed(() => {
  if (!filterStatus.value) return testCases.value
  return testCases.value.filter(tc => tc.status === filterStatus.value)
})

function goToDetail(testCaseId) {
  router.push(`/project/${props.projectId}/test/${testCaseId}`)
}

async function fetchTestCases() {
  loading.value = true
  try {
    const res = await testCaseApi.list(props.projectId)
    testCases.value = res.data || res || []
  } catch {
    testCases.value = []
  } finally {
    loading.value = false
  }
}

async function createTestCase() {
  if (!form.value.title.trim()) return
  submitting.value = true
  try {
    await testCaseApi.create(props.projectId, form.value)
    showCreateModal.value = false
    form.value = { title: '', steps: '', expectedResult: '' }
    await fetchTestCases()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

onMounted(fetchTestCases)
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

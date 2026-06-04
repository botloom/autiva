<template>
  <div class="page-container">
    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="design">
      <div class="detail-header">
        <button class="back-btn" @click="$router.push(`/project/${projectId}`)">
          <svg width="14" height="14" viewBox="0 0 16 16">
            <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
      </div>

      <ACard>
        <div class="detail-title-row">
          <h2 class="detail-title">{{ design.title }}</h2>
          <ATag :variant="statusVariant(design.status)" size="small">{{ statusLabel(design.status) }}</ATag>
        </div>
        <div class="detail-meta">
          <span>{{ design.submitterId || '—' }}</span>
          <span>{{ formatDate(design.createdAt) }}</span>
        </div>
        <div class="divider"></div>
        <div class="detail-content">
          <p>{{ design.content || '暂无方案内容' }}</p>
        </div>
      </ACard>

      <!-- Submit for Review -->
      <ACard v-if="design.status === 'DRAFT'" class="action-card">
        <div class="action-row">
          <span class="action-hint">草稿状态，提交后进入评审</span>
          <AButton variant="primary" size="small" :loading="submitting" @click="submitForReview">提交评审</AButton>
        </div>
      </ACard>

      <!-- Review Section -->
      <div v-if="design.status === 'SUBMITTED' || design.status === 'IN_REVIEW'" class="review-section">
        <ACard>
          <div class="form-group">
            <ATextarea v-model="reviewForm.comment" label="评审意见" placeholder="输入评审意见" :rows="2" />
          </div>
          <div class="review-actions">
            <AButton variant="danger" size="small" :loading="reviewing" @click="submitReview(false)">驳回</AButton>
            <AButton variant="primary" size="small" :loading="reviewing" @click="submitReview(true)">通过</AButton>
          </div>
        </ACard>
      </div>

      <!-- Review Result -->
      <div v-if="design.reviewerId || design.reviewComment" class="review-section">
        <div class="review-result-card">
          <div class="review-item-header">
            <span class="review-author">{{ design.reviewerId || '匿名' }}</span>
            <ATag :variant="design.status === 'APPROVED' ? 'success' : 'danger'" size="small">
              {{ design.status === 'APPROVED' ? '通过' : '驳回' }}
            </ATag>
          </div>
          <p class="review-comment">{{ design.reviewComment || '无评论' }}</p>
        </div>
      </div>
    </template>

    <AEmpty v-else title="设计方案不存在" description="请检查设计方案ID是否正确">
      <AButton variant="primary" size="small" @click="$router.push(`/project/${projectId}`)">返回项目</AButton>
    </AEmpty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ACard from '../components/ACard.vue'
import ATag from '../components/ATag.vue'
import AEmpty from '../components/AEmpty.vue'
import AButton from '../components/AButton.vue'
import ATextarea from '../components/ATextarea.vue'
import { designProposalApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  designId: { type: [String, Number], required: true }
})

const design = ref(null)
const loading = ref(false)
const submitting = ref(false)
const reviewing = ref(false)
const reviewForm = ref({ comment: '' })

const statusMap = {
  DRAFT: { label: '草稿', variant: 'default' },
  SUBMITTED: { label: '已提交', variant: 'info' },
  IN_REVIEW: { label: '评审中', variant: 'warning' },
  APPROVED: { label: '已通过', variant: 'success' },
  REJECTED: { label: '已驳回', variant: 'danger' }
}

function statusLabel(s) { return statusMap[s]?.label || s || '未知' }
function statusVariant(s) { return statusMap[s]?.variant || 'default' }

function formatDate(dateStr) {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fetchDesign() {
  loading.value = true
  try {
    const res = await designProposalApi.get(props.designId)
    design.value = res.data || res
  } catch {
    design.value = null
  } finally {
    loading.value = false
  }
}

async function submitForReview() {
  submitting.value = true
  try {
    await designProposalApi.submit(props.designId)
    await fetchDesign()
  } catch {
    // handle error
  } finally {
    submitting.value = false
  }
}

async function submitReview(approved) {
  reviewing.value = true
  try {
    await designProposalApi.review(
      props.designId,
      'current-user',
      reviewForm.value.comment,
      approved
    )
    reviewForm.value.comment = ''
    await fetchDesign()
  } catch {
    // handle error
  } finally {
    reviewing.value = false
  }
}

onMounted(fetchDesign)
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
  gap: var(--space-3);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.detail-content {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.action-card {
  margin-top: var(--space-4);
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.review-section {
  margin-top: var(--space-4);
}

.review-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
}

.form-group {
  margin-bottom: var(--space-3);
}

.review-result-card {
  padding: var(--space-3);
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.review-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-1);
}

.review-author {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.review-comment {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: var(--line-height-normal);
}

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

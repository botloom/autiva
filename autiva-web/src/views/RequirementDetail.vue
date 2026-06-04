<template>
  <div class="page-container">
    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="requirement">
      <div class="detail-header">
        <button class="back-btn" @click="$router.push(`/project/${projectId}/requirements`)">
          <svg width="14" height="14" viewBox="0 0 16 16">
            <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
      </div>

      <ACard>
        <div class="detail-title-row">
          <h2 class="detail-title">{{ requirement.title }}</h2>
          <ATag :variant="statusVariant(requirement.status)" size="small">{{ statusLabel(requirement.status) }}</ATag>
        </div>
        <div class="detail-meta">
          <span>{{ priorityLabel(requirement.priority) }}</span>
          <span>{{ requirement.creator || '—' }}</span>
          <span>{{ formatDate(requirement.createdAt) }}</span>
        </div>
        <div class="divider"></div>
        <div class="detail-content">
          <p>{{ requirement.description || '暂无详细描述' }}</p>
        </div>
      </ACard>

      <!-- Review Section -->
      <div class="review-section">
        <h3 class="section-title">评审记录</h3>
        <div v-if="reviews.length" class="review-list">
          <div v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-item-header">
              <span class="review-author">{{ review.author || '匿名' }}</span>
              <ATag :variant="review.result === 'approved' ? 'success' : 'danger'" size="small">
                {{ review.result === 'approved' ? '通过' : '拒绝' }}
              </ATag>
            </div>
            <p class="review-comment">{{ review.comment || '无评论' }}</p>
            <span class="review-date">{{ formatDate(review.createdAt) }}</span>
          </div>
        </div>
        <p v-else class="empty-hint">暂无评审记录</p>
      </div>

      <!-- Add Review -->
      <ACard class="review-form-card">
        <div class="form-group">
          <ATextarea v-model="reviewForm.comment" label="评审意见" placeholder="输入评审意见" :rows="2" />
        </div>
        <div class="review-actions">
          <AButton variant="danger" size="small" :loading="reviewing" @click="submitReview('rejected')">拒绝</AButton>
          <AButton variant="primary" size="small" :loading="reviewing" @click="submitReview('approved')">通过</AButton>
        </div>
      </ACard>
    </template>

    <AEmpty v-else title="需求不存在" description="请检查需求ID是否正确">
      <AButton variant="primary" size="small" @click="$router.push(`/project/${projectId}/requirements`)">返回需求列表</AButton>
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
import { requirementApi } from '../api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  reqId: { type: [String, Number], required: true }
})

const requirement = ref(null)
const reviews = ref([])
const loading = ref(false)
const reviewing = ref(false)
const reviewForm = ref({ comment: '' })

const statusMap = {
  draft: { label: '草稿', variant: 'default' },
  reviewing: { label: '评审中', variant: 'warning' },
  approved: { label: '已通过', variant: 'success' },
  rejected: { label: '已拒绝', variant: 'danger' }
}

const priorityMap = { low: '低', medium: '中', high: '高', critical: '紧急' }

function statusLabel(s) { return statusMap[s]?.label || s || '未知' }
function statusVariant(s) { return statusMap[s]?.variant || 'default' }
function priorityLabel(p) { return priorityMap[p] || p || '—' }

function formatDate(dateStr) {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fetchRequirement() {
  loading.value = true
  try {
    const res = await requirementApi.get(props.reqId)
    requirement.value = res.data || res
    reviews.value = requirement.value?.reviews || []
  } catch {
    requirement.value = null
  } finally {
    loading.value = false
  }
}

async function submitReview(result) {
  reviewing.value = true
  try {
    await requirementApi.review(props.projectId, props.reqId, {
      result,
      comment: reviewForm.value.comment
    })
    reviewForm.value.comment = ''
    await fetchRequirement()
  } catch {
    // handle error
  } finally {
    reviewing.value = false
  }
}

onMounted(fetchRequirement)
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
}

.review-section {
  margin-top: var(--space-5);
}

.section-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
}

.empty-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.review-item {
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

.review-date {
  font-size: 10px;
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
  display: block;
}

.review-form-card {
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

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

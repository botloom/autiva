<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">通知</h1>
      <AButton variant="ghost" size="small" @click="markAllRead">全部已读</AButton>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>

    <div v-else-if="notifications.length" class="list-container">
      <div
        v-for="notification in notifications"
        :key="notification.id"
        class="notification-item"
        :class="{ 'notification-item--unread': !notification.read }"
        @click="markRead(notification)"
      >
        <div class="notification-dot" :class="{ 'notification-dot--unread': !notification.read }"></div>
        <div class="notification-content">
          <div class="notification-title">{{ notification.title }}</div>
          <div class="notification-message">{{ notification.message }}</div>
          <div class="notification-time">{{ formatDate(notification.createdAt) }}</div>
        </div>
      </div>
    </div>

    <AEmpty v-else title="暂无通知" description="没有新的通知消息" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AButton from '../components/AButton.vue'
import AEmpty from '../components/AEmpty.vue'
import { notificationApi } from '../api'

const notifications = ref([])
const loading = ref(false)

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function fetchNotifications() {
  loading.value = true
  try {
    const targetClientId = localStorage.getItem('clientId') || ''
    const res = await notificationApi.list(targetClientId)
    notifications.value = res.data || res || []
  } catch {
    notifications.value = []
  } finally {
    loading.value = false
  }
}

async function markRead(notification) {
  if (notification.read) return
  try {
    await notificationApi.acknowledge(notification.id)
    notification.read = true
  } catch {
    // handle error
  }
}

async function markAllRead() {
  try {
    await notificationApi.markAllRead()
    notifications.value.forEach(n => { n.read = true })
  } catch {
    // handle error
  }
}

onMounted(fetchNotifications)
</script>

<style scoped>
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: box-shadow var(--transition-base), border-color var(--transition-base);
}

.notification-item:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.12);
}

.notification-item--unread {
  background: rgba(0, 122, 255, 0.02);
  border-color: rgba(0, 122, 255, 0.12);
}

.notification-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--color-border);
  margin-top: 6px;
  flex-shrink: 0;
}

.notification-dot--unread {
  background: var(--color-primary);
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.notification-message {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: var(--line-height-normal);
}

.notification-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
}

.loading-state {
  text-align: center;
  padding: var(--space-10);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>

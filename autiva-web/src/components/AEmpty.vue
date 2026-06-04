<template>
  <div class="a-empty">
    <div class="a-empty__icon">
      <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
        <rect x="12" y="16" width="40" height="36" rx="4" stroke="currentColor" stroke-width="2" opacity="0.2"/>
        <path d="M12 24h40" stroke="currentColor" stroke-width="2" opacity="0.15"/>
        <circle cx="20" cy="20" r="2" fill="currentColor" opacity="0.15"/>
        <circle cx="26" cy="20" r="2" fill="currentColor" opacity="0.15"/>
        <circle cx="32" cy="20" r="2" fill="currentColor" opacity="0.15"/>
      </svg>
    </div>
    <p class="a-empty__title">{{ title || '暂无数据' }}</p>
    <p v-if="description" class="a-empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="a-empty__action">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  description: String
})
</script>

<style scoped>
.a-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.a-empty__icon {
  color: var(--color-text-tertiary);
  margin-bottom: var(--space-4);
}

.a-empty__title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-2);
}

.a-empty__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  max-width: 320px;
}

.a-empty__action {
  margin-top: var(--space-4);
}
</style>

<template>
  <span class="a-tag" :class="[`a-tag--${variant}`, `a-tag--${size}`]">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'primary', 'success', 'warning', 'danger', 'info'].includes(v)
  },
  size: {
    type: String,
    default: 'medium',
    validator: (v) => ['small', 'medium'].includes(v)
  }
})
</script>

<style scoped>
.a-tag {
  display: inline-flex;
  align-items: center;
  font-weight: var(--font-weight-medium);
  border-radius: var(--radius-full);
  white-space: nowrap;
}

.a-tag--small {
  height: 20px;
  padding: 0 var(--space-2);
  font-size: 11px;
}

.a-tag--medium {
  height: 24px;
  padding: 0 var(--space-3);
  font-size: var(--font-size-xs);
}

.a-tag--default {
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
}

.a-tag--primary {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.a-tag--success {
  background: var(--color-success-light);
  color: var(--color-success);
}

.a-tag--warning {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.a-tag--danger {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.a-tag--info {
  background: var(--color-info-light);
  color: var(--color-info);
}
</style>

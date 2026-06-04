<template>
  <div class="a-card" :class="{ 'a-card--hoverable': hoverable, 'a-card--bordered': bordered }" @click="$emit('click', $event)">
    <div v-if="$slots.header" class="a-card__header">
      <slot name="header" />
    </div>
    <div class="a-card__body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="a-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  hoverable: Boolean,
  bordered: { type: Boolean, default: true }
})

defineEmits(['click'])
</script>

<style scoped>
.a-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: box-shadow var(--transition-base), border-color var(--transition-base);
}

.a-card--bordered {
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.a-card--hoverable {
  cursor: pointer;
}

.a-card--hoverable:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.12);
}

.a-card__header {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border-light);
}

.a-card__body {
  padding: var(--space-4);
}

.a-card__footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-border-light);
}
</style>

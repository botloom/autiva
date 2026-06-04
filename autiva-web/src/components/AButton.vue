<template>
  <button
    :class="[
      'a-button',
      `a-button--${variant}`,
      `a-button--${size}`,
      {
        'a-button--loading': loading,
        'a-button--disabled': disabled,
        'a-button--block': block,
        'a-button--pill': pill
      }
    ]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="a-button__spinner"></span>
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'secondary', 'danger', 'ghost'].includes(v)
  },
  size: {
    type: String,
    default: 'medium',
    validator: (v) => ['small', 'medium', 'large'].includes(v)
  },
  loading: Boolean,
  disabled: Boolean,
  block: Boolean,
  pill: Boolean
})

defineEmits(['click'])
</script>

<style scoped>
.a-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-weight: var(--font-weight-medium);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  white-space: nowrap;
  user-select: none;
  outline: none;
  position: relative;
}

.a-button:focus-visible {
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.3);
}

/* Sizes */
.a-button--small {
  height: 30px;
  padding: 0 var(--space-3);
  font-size: var(--font-size-xs);
  border-radius: var(--radius-sm);
}

.a-button--medium {
  height: 36px;
  padding: 0 var(--space-4);
  font-size: var(--font-size-sm);
}

.a-button--large {
  height: 44px;
  padding: 0 var(--space-6);
  font-size: var(--font-size-base);
  border-radius: var(--radius-lg);
}

/* Variants */
.a-button--primary {
  background-color: var(--color-primary);
  color: var(--color-primary-text);
}

.a-button--primary:hover:not(.a-button--disabled) {
  background-color: var(--color-primary-hover);
}

.a-button--primary:active:not(.a-button--disabled) {
  background-color: var(--color-primary-active);
}

.a-button--secondary {
  background-color: var(--color-bg-card);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.a-button--secondary:hover:not(.a-button--disabled) {
  background-color: var(--color-bg-hover);
  border-color: var(--color-text-secondary);
}

.a-button--danger {
  background-color: var(--color-danger);
  color: #FFFFFF;
}

.a-button--danger:hover:not(.a-button--disabled) {
  background-color: var(--color-danger-hover);
}

.a-button--ghost {
  background-color: transparent;
  color: var(--color-primary);
}

.a-button--ghost:hover:not(.a-button--disabled) {
  background-color: var(--color-primary-light);
}

/* States */
.a-button--disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.a-button--loading {
  pointer-events: none;
}

.a-button--block {
  width: 100%;
}

.a-button--pill {
  border-radius: var(--radius-full);
}

.a-button__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: a-button-spin 0.6s linear infinite;
}

@keyframes a-button-spin {
  to { transform: rotate(360deg); }
}
</style>

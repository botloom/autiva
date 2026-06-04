<template>
  <Teleport to="body">
    <Transition name="a-toast">
      <div v-if="visible" class="a-toast" :class="[`a-toast--${variant}`]">
        <span class="a-toast__icon">{{ iconMap[variant] }}</span>
        <span class="a-toast__message">{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  message: { type: String, required: true },
  variant: {
    type: String,
    default: 'info',
    validator: (v) => ['info', 'success', 'warning', 'danger'].includes(v)
  },
  duration: { type: Number, default: 3000 }
})

const visible = ref(false)
let timer = null

const iconMap = {
  info: 'ℹ',
  success: '✓',
  warning: '⚠',
  danger: '✕'
}

function show() {
  visible.value = true
  if (timer) clearTimeout(timer)
  if (props.duration > 0) {
    timer = setTimeout(() => {
      visible.value = false
    }, props.duration)
  }
}

function hide() {
  visible.value = false
  if (timer) clearTimeout(timer)
}

defineExpose({ show, hide })
</script>

<style scoped>
.a-toast {
  position: fixed;
  top: var(--space-6);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  background: var(--color-bg-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  font-size: var(--font-size-sm);
  z-index: var(--z-toast);
  min-width: 200px;
  max-width: 480px;
}

.a-toast__icon {
  font-size: var(--font-size-base);
  flex-shrink: 0;
}

.a-toast--info .a-toast__icon { color: var(--color-primary); }
.a-toast--success .a-toast__icon { color: var(--color-success); }
.a-toast--warning .a-toast__icon { color: var(--color-warning); }
.a-toast--danger .a-toast__icon { color: var(--color-danger); }

.a-toast__message {
  color: var(--color-text-primary);
}

.a-toast-enter-active {
  transition: all var(--transition-base);
}

.a-toast-leave-active {
  transition: all var(--transition-fast);
}

.a-toast-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(-12px);
}

.a-toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>

<template>
  <Teleport to="body">
    <Transition name="a-modal-backdrop">
      <div v-if="modelValue" class="a-modal-backdrop" @click="onBackdropClick"></div>
    </Transition>
    <Transition name="a-modal">
      <div v-if="modelValue" class="a-modal-wrapper">
        <div class="a-modal" :style="{ width, maxWidth }">
          <div v-if="title || $slots.header" class="a-modal__header">
            <slot name="header">
              <h3 class="a-modal__title">{{ title }}</h3>
            </slot>
            <button v-if="closable" class="a-modal__close" @click="close">
              <svg width="14" height="14" viewBox="0 0 14 14">
                <path d="M1 1l12 12M13 1L1 13" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <div class="a-modal__body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="a-modal__footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
const props = defineProps({
  modelValue: Boolean,
  title: String,
  width: { type: String, default: '520px' },
  maxWidth: { type: String, default: '90vw' },
  closable: { type: Boolean, default: true },
  closeOnBackdrop: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'close'])

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function onBackdropClick() {
  if (props.closeOnBackdrop) close()
}
</script>

<style scoped>
.a-modal-backdrop {
  position: fixed;
  inset: 0;
  background: var(--color-bg-overlay);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: var(--z-modal-backdrop);
}

.a-modal-wrapper {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: var(--space-6);
}

.a-modal {
  background: var(--color-bg-elevated);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
}

.a-modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
}

.a-modal__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.a-modal__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  color: var(--color-text-tertiary);
  transition: all var(--transition-fast);
}

.a-modal__close:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
}

.a-modal__body {
  padding: var(--space-6);
  overflow-y: auto;
  flex: 1;
}

.a-modal__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border-light);
}

/* Transitions */
.a-modal-backdrop-enter-active,
.a-modal-backdrop-leave-active {
  transition: opacity var(--transition-slow);
}

.a-modal-backdrop-enter-from,
.a-modal-backdrop-leave-to {
  opacity: 0;
}

.a-modal-enter-active {
  transition: all var(--transition-slow);
}

.a-modal-leave-active {
  transition: all var(--transition-fast);
}

.a-modal-enter-from {
  opacity: 0;
  transform: scale(0.95) translateY(10px);
}

.a-modal-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>

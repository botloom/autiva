<template>
  <div class="a-select" :class="{ 'a-select--error': error, 'a-select--disabled': disabled, 'a-select--open': isOpen }">
    <label v-if="label" class="a-select__label">{{ label }}</label>
    <div class="a-select__wrapper" @click="toggle">
      <div class="a-select__display">
        <span v-if="selectedLabel" class="a-select__value">{{ selectedLabel }}</span>
        <span v-else class="a-select__placeholder">{{ placeholder }}</span>
      </div>
      <svg class="a-select__arrow" viewBox="0 0 12 12" width="12" height="12">
        <path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
    <Transition name="a-select-dropdown">
      <div v-if="isOpen" class="a-select__dropdown">
        <div class="a-select__dropdown-inner">
          <div
            v-for="option in options"
            :key="option.value"
            class="a-select__option"
            :class="{ 'a-select__option--active': option.value === modelValue }"
            @click.stop="select(option)"
          >
            <span class="a-select__option-text">{{ option.label }}</span>
            <svg v-if="option.value === modelValue" class="a-select__check" width="14" height="14" viewBox="0 0 14 14">
              <path d="M3 7l3 3 5-5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
      </div>
    </Transition>
    <p v-if="error" class="a-select__error">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  label: String,
  placeholder: { type: String, default: '请选择' },
  disabled: Boolean,
  error: String,
  size: {
    type: String,
    default: 'medium',
    validator: (v) => ['small', 'medium'].includes(v)
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOpen = ref(false)

const selectedLabel = computed(() => {
  const opt = props.options.find(o => o.value === props.modelValue)
  return opt ? opt.label : ''
})

function toggle() {
  if (props.disabled) return
  isOpen.value = !isOpen.value
}

function select(option) {
  emit('update:modelValue', option.value)
  emit('change', option.value)
  isOpen.value = false
}

function handleClickOutside(e) {
  if (!e.target.closest('.a-select')) {
    isOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.a-select {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.a-select__label {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.a-select__wrapper {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 var(--space-3);
  background: var(--color-bg-card);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.a-select--small .a-select__wrapper {
  height: 28px;
  padding: 0 var(--space-2);
}

.a-select__wrapper:hover:not(.a-select--disabled .a-select__wrapper) {
  border-color: rgba(0, 0, 0, 0.2);
}

.a-select--open .a-select__wrapper {
  border-color: var(--color-border-focus);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.12);
}

.a-select--disabled .a-select__wrapper {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--color-bg-hover);
}

.a-select__display {
  flex: 1;
  min-width: 0;
}

.a-select__value {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.a-select__placeholder {
  font-size: var(--font-size-sm);
  color: var(--color-text-placeholder);
}

.a-select__arrow {
  flex-shrink: 0;
  margin-left: var(--space-2);
  color: var(--color-text-tertiary);
  transition: transform var(--transition-fast);
}

.a-select--open .a-select__arrow {
  transform: rotate(180deg);
  color: var(--color-primary);
}

.a-select__dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: var(--space-1);
  z-index: var(--z-dropdown);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.a-select__dropdown-inner {
  background: var(--color-bg-elevated);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.06);
  max-height: 220px;
  overflow-y: auto;
  padding: var(--space-1);
}

.a-select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.a-select__option:hover {
  background: rgba(0, 0, 0, 0.04);
}

.a-select__option--active {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  background: var(--color-primary-light);
}

.a-select__option--active:hover {
  background: var(--color-primary-light);
}

.a-select__option-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.a-select__check {
  flex-shrink: 0;
  margin-left: var(--space-2);
  color: var(--color-primary);
}

.a-select--error .a-select__wrapper {
  border-color: var(--color-danger);
}

.a-select__error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
}

/* Dropdown transition */
.a-select-dropdown-enter-active {
  transition: all var(--transition-base);
}

.a-select-dropdown-leave-active {
  transition: all var(--transition-fast);
}

.a-select-dropdown-enter-from {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.a-select-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-2px) scale(0.99);
}
</style>

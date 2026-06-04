<template>
  <div class="a-input" :class="{ 'a-input--error': error, 'a-input--disabled': disabled }">
    <label v-if="label" class="a-input__label">{{ label }}</label>
    <div class="a-input__wrapper">
      <input
        ref="inputRef"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        class="a-input__field"
        @input="$emit('update:modelValue', $event.target.value)"
        @focus="$emit('focus', $event)"
        @blur="$emit('blur', $event)"
        @keydown.enter="$emit('enter', $event)"
      />
      <button
        v-if="clearable && modelValue"
        class="a-input__clear"
        @click="$emit('update:modelValue', '')"
      >
        &times;
      </button>
    </div>
    <p v-if="error" class="a-input__error">{{ error }}</p>
    <p v-else-if="hint" class="a-input__hint">{{ hint }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: String, default: '' },
  label: String,
  placeholder: String,
  type: { type: String, default: 'text' },
  disabled: Boolean,
  error: String,
  hint: String,
  clearable: Boolean
})

defineEmits(['update:modelValue', 'focus', 'blur', 'enter'])

const inputRef = ref(null)

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style scoped>
.a-input {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.a-input__label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.a-input__wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.a-input__field {
  width: 100%;
  height: 36px;
  padding: 0 var(--space-3);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  transition: all var(--transition-fast);
  outline: none;
}

.a-input__field::placeholder {
  color: var(--color-text-placeholder);
}

.a-input__field:hover:not(:disabled) {
  border-color: var(--color-text-secondary);
}

.a-input__field:focus {
  border-color: var(--color-border-focus);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.15);
}

.a-input--error .a-input__field {
  border-color: var(--color-danger);
}

.a-input--error .a-input__field:focus {
  box-shadow: 0 0 0 3px rgba(255, 59, 48, 0.15);
}

.a-input--disabled .a-input__field {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--color-bg-hover);
}

.a-input__clear {
  position: absolute;
  right: var(--space-2);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 14px;
  color: var(--color-text-tertiary);
  border-radius: var(--radius-full);
  transition: all var(--transition-fast);
}

.a-input__clear:hover {
  color: var(--color-text-secondary);
  background: var(--color-bg-hover);
}

.a-input__error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
}

.a-input__hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>

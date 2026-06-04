<template>
  <div class="a-textarea" :class="{ 'a-textarea--error': error, 'a-textarea--disabled': disabled }">
    <label v-if="label" class="a-textarea__label">{{ label }}</label>
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :rows="rows"
      class="a-textarea__field"
      @input="$emit('update:modelValue', $event.target.value)"
      @focus="$emit('focus', $event)"
      @blur="$emit('blur', $event)"
    ></textarea>
    <p v-if="error" class="a-textarea__error">{{ error }}</p>
    <p v-else-if="hint" class="a-textarea__hint">{{ hint }}</p>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  label: String,
  placeholder: String,
  disabled: Boolean,
  error: String,
  hint: String,
  rows: { type: Number, default: 4 }
})

defineEmits(['update:modelValue', 'focus', 'blur'])
</script>

<style scoped>
.a-textarea {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.a-textarea__label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.a-textarea__field {
  width: 100%;
  padding: var(--space-3);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  resize: vertical;
  transition: all var(--transition-fast);
  outline: none;
  font-family: inherit;
}

.a-textarea__field::placeholder {
  color: var(--color-text-placeholder);
}

.a-textarea__field:hover:not(:disabled) {
  border-color: var(--color-text-secondary);
}

.a-textarea__field:focus {
  border-color: var(--color-border-focus);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.15);
}

.a-textarea--error .a-textarea__field {
  border-color: var(--color-danger);
}

.a-textarea--error .a-textarea__field:focus {
  box-shadow: 0 0 0 3px rgba(255, 59, 48, 0.15);
}

.a-textarea--disabled .a-textarea__field {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--color-bg-hover);
}

.a-textarea__error {
  font-size: var(--font-size-xs);
  color: var(--color-danger);
}

.a-textarea__hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>

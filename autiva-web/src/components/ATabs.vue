<template>
  <div class="a-tabs">
    <div class="a-tabs__nav">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="a-tabs__tab"
        :class="{ 'a-tabs__tab--active': modelValue === tab.key }"
        @click="$emit('update:modelValue', tab.key)"
      >
        {{ tab.label }}
      </button>
      <div class="a-tabs__indicator" :style="indicatorStyle"></div>
    </div>
    <div class="a-tabs__content">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'

const props = defineProps({
  modelValue: { type: String, required: true },
  tabs: { type: Array, required: true }
})

defineEmits(['update:modelValue'])

const navRef = ref(null)
const indicatorStyle = ref({})

function updateIndicator() {
  nextTick(() => {
    if (!navRef.value) return
    const activeTab = navRef.value.querySelector('.a-tabs__tab--active')
    if (activeTab) {
      indicatorStyle.value = {
        left: activeTab.offsetLeft + 'px',
        width: activeTab.offsetWidth + 'px'
      }
    }
  })
}

onMounted(updateIndicator)
watch(() => props.modelValue, updateIndicator)
</script>

<style scoped>
.a-tabs__nav {
  position: relative;
  display: flex;
  border-bottom: 1px solid var(--color-border-light);
  gap: 0;
}

.a-tabs__tab {
  position: relative;
  padding: var(--space-3) var(--space-5);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  transition: color var(--transition-fast);
  white-space: nowrap;
}

.a-tabs__tab:hover {
  color: var(--color-text-primary);
}

.a-tabs__tab--active {
  color: var(--color-primary);
}

.a-tabs__indicator {
  position: absolute;
  bottom: -1px;
  height: 2px;
  background: var(--color-primary);
  border-radius: var(--radius-full);
  transition: all var(--transition-base);
}

.a-tabs__content {
  padding-top: var(--space-5);
}
</style>

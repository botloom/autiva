<template>
  <span class="a-badge" :class="[`a-badge--${variant}`, { 'a-badge--dot': dot }]">
    <template v-if="!dot">{{ count }}</template>
  </span>
</template>

<script setup>
defineProps({
  count: { type: [Number, String], default: 0 },
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'success', 'warning', 'danger', 'info'].includes(v)
  },
  dot: Boolean
})
</script>

<style scoped>
.a-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--space-1);
  font-size: 11px;
  font-weight: var(--font-weight-semibold);
  color: #FFFFFF;
  border-radius: var(--radius-full);
  line-height: 1;
}

.a-badge--dot {
  min-width: 8px;
  height: 8px;
  padding: 0;
}

.a-badge--primary { background-color: var(--color-primary); }
.a-badge--success { background-color: var(--color-success); }
.a-badge--warning { background-color: var(--color-warning); }
.a-badge--danger { background-color: var(--color-danger); }
.a-badge--info { background-color: var(--color-info); }
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

defineOptions({
  inheritAttrs: false,
})

const props = defineProps<{
  to: string | Record<string, unknown>
  variant?: 'primary' | 'danger' | 'ghost' | 'outline'
  size?: 'sm' | 'md' | 'lg'
}>()

const linkClass = computed(() => [
  'inline-flex items-center justify-center gap-2 rounded-lg font-bold transition-all duration-200 focus:outline-none',
  props.size === 'sm'
    ? 'px-2 py-1.25 text-[11px] sm:px-3 sm:py-1.5 sm:text-sm'
    : props.size === 'lg'
      ? 'px-4 py-2.25 text-[13px] sm:px-6 sm:py-3 sm:text-base'
      : 'px-3 py-1.75 text-[12px] sm:px-4 sm:py-2 sm:text-sm',
  (!props.variant || props.variant === 'outline') ? 'border border-line bg-surface text-fg hover:border-blue-600 hover:bg-surface-2' :
  props.variant === 'primary' ? 'border border-transparent bg-blue-600 text-white shadow-lg shadow-blue-950/25 hover:bg-blue-500' :
  props.variant === 'danger' ? 'border border-transparent bg-red-600 text-white shadow-lg shadow-red-950/25 hover:bg-red-500' :
  'text-muted hover:bg-surface hover:text-fg',
])
</script>

<template>
  <RouterLink v-bind="$attrs" :to="to" :class="linkClass">
    <slot />
  </RouterLink>
</template>

<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'danger' | 'ghost' | 'outline'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>()
</script>

<template>
  <button
    v-bind="$attrs"
    :type="type ?? 'button'"
    :disabled="disabled || loading"
    :class="[
      'inline-flex items-center justify-center gap-2 rounded-lg font-bold transition-all duration-200 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50',
      size === 'sm'
        ? 'px-2 py-1.25 text-[11px] max-[390px]:px-1.75 max-[390px]:py-1 max-[390px]:text-[10px] max-[360px]:px-1.5 max-[360px]:py-0.75 max-[360px]:text-[9px] sm:px-3 sm:py-1.5 sm:text-sm'
        : size === 'lg'
          ? 'px-4 py-2.25 text-[13px] max-[390px]:px-3 max-[390px]:py-2 max-[390px]:text-[12px] max-[360px]:px-2.5 max-[360px]:py-1.75 max-[360px]:text-[11px] sm:px-6 sm:py-3 sm:text-base'
          : 'px-3 py-1.75 text-[12px] max-[390px]:px-2.5 max-[390px]:py-1.5 max-[390px]:text-[11px] max-[360px]:px-2 max-[360px]:py-1.25 max-[360px]:text-[10px] sm:px-4 sm:py-2 sm:text-sm',
      (!variant || variant === 'primary') ? 'border border-transparent bg-blue-600 text-white shadow-lg shadow-blue-950/25 hover:bg-blue-500' :
      variant === 'danger' ? 'border border-transparent bg-red-600 text-white shadow-lg shadow-red-950/25 hover:bg-red-500' :
      variant === 'ghost' ? 'text-gray-400 hover:bg-white/5 hover:text-white' :
      'border border-white/10 bg-white/5 text-white hover:border-blue-600 hover:bg-white/10',
    ]"
  >
    <span v-if="loading" class="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
    <slot />
  </button>
</template>

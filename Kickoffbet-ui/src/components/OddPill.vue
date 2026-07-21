<script setup lang="ts">
defineProps<{
  label: string
  odds: number
  selected?: boolean
  inactive?: boolean
  compact?: boolean
}>()

defineEmits<{ click: [] }>()
</script>

<template>
  <button
    :disabled="inactive"
    :tabindex="inactive ? -1 : 0"
    :aria-disabled="inactive"
    @click.stop="!inactive && $emit('click')"
    :class="[
      'group relative flex min-w-0 flex-col items-center justify-center rounded-md border font-bold transition-all duration-300 sm:rounded-lg',
      compact ? 'flex-1 basis-0 px-1 py-1 text-[10px] max-[390px]:px-0.75 max-[390px]:py-0.75 max-[390px]:text-[9px] max-[360px]:px-0.5 max-[360px]:py-0.5 max-[360px]:text-[8px]' : 'px-1.5 py-1.5 text-[10px] max-[390px]:px-1.25 max-[390px]:py-1.25 max-[390px]:text-[9px] max-[360px]:px-1 max-[360px]:py-1 max-[360px]:text-[8px] sm:text-[11px]',
      inactive
        ? 'cursor-not-allowed border-line bg-surface text-fg/35 line-through opacity-60'
        : selected
          ? 'scale-[1.02] border-blue-500 bg-blue-600 text-white shadow-lg'
          : 'cursor-pointer border-line bg-surface-2 text-fg/90 hover:border-blue-500/40 hover:bg-surface',
    ]"
  >
    <span :class="[compact ? 'text-[7px] max-[390px]:text-[6px] max-[360px]:text-[5px] sm:text-[8px]' : 'text-[8px] max-[390px]:text-[7px] max-[360px]:text-[6px] sm:text-[9px]', 'leading-tight transition-opacity', selected ? 'text-white' : 'text-fg/60 group-hover:text-fg/80']">{{ label }}</span>
    <span :class="[compact ? 'text-[10px] max-[390px]:text-[9px] max-[360px]:text-[8px] sm:text-[11px]' : 'text-[11px] max-[390px]:text-[10px] max-[360px]:text-[9px] sm:text-[12px]', 'mt-0.5 font-bold leading-tight']">{{ odds.toFixed(2) }}</span>
  </button>
</template>

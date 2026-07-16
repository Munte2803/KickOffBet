<script setup lang="ts">
defineProps<{
  label: string
  value: string
}>()
</script>

<template>
  <div class="min-w-0 overflow-hidden rounded-xl border border-white/10 bg-black/40 p-3 sm:p-4">
    <p class="text-[10px] sm:text-xs uppercase tracking-[0.2em] text-gray-500">{{ label }}</p>
    <p class="mt-2 text-sm sm:text-base font-semibold text-white break-all">{{ value }}</p>
  </div>
</template>

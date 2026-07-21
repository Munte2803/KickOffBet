<script setup lang="ts">
defineProps<{
  message?: string
  description?: string
  icon?: string
}>()
</script>

<template>
  <div class="rounded-xl border border-dashed border-line bg-surface px-2.5 py-7 text-center sm:px-4 sm:py-12">
    <div v-if="icon" class="mb-3 text-2xl opacity-50 sm:text-3xl">{{ icon }}</div>
    <p class="text-[12px] font-medium text-muted sm:text-sm">{{ message ?? 'Nu exista date disponibile.' }}</p>
    <p v-if="description" class="mt-2 text-[10px] text-subtle sm:text-xs">{{ description }}</p>
  </div>
</template>

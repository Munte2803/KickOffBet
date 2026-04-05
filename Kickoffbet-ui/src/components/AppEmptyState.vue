<script setup lang="ts">
defineProps<{
  message?: string
  description?: string
  icon?: string
}>()
</script>

<template>
  <div class="rounded-xl border border-dashed border-white/10 bg-white/5 px-2.5 py-7 text-center sm:px-4 sm:py-12">
    <div v-if="icon" class="mb-3 text-2xl opacity-50 sm:text-3xl">{{ icon }}</div>
    <p class="text-[12px] font-medium text-gray-300 sm:text-sm">{{ message ?? 'Nu exista date disponibile.' }}</p>
    <p v-if="description" class="mt-2 text-[10px] text-gray-500 sm:text-xs">{{ description }}</p>
  </div>
</template>

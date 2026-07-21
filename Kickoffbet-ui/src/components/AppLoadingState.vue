<script setup lang="ts">
defineProps<{ message?: string }>()
</script>

<template>
  <div class="py-10 text-center">
    <span class="mb-3 inline-block h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
    <p class="text-sm text-muted">{{ message ?? 'Se incarca...' }}</p>
  </div>
</template>

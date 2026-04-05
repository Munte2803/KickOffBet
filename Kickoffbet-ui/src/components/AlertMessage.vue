<script setup lang="ts">
defineProps<{
  type?: 'error' | 'success' | 'info'
  message?: string
}>()
</script>

<template>
  <p
    v-if="message"
    :class="[
      'rounded-xl border px-4 py-3 text-sm',
      type === 'success'
        ? 'border-green-500/20 bg-green-500/10 text-green-300'
        : type === 'info'
          ? 'border-blue-500/20 bg-blue-500/10 text-blue-300'
          : 'border-red-500/20 bg-red-500/10 text-red-300',
    ]"
    role="alert"
  >
    {{ message }}
  </p>
</template>

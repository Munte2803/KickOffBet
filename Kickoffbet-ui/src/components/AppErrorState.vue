<script setup lang="ts">
import { computed } from 'vue'
import AlertMessage from './AlertMessage.vue'
import AppButton from './AppButton.vue'

const props = defineProps<{
  error?: unknown
  message?: string
  retrying?: boolean
}>()

defineEmits<{ retry: [] }>()

const text = computed(() => {
  const reason = props.error instanceof Error && props.error.message ? ` ${props.error.message}` : ''
  return `${props.message ?? 'Nu am putut incarca datele.'}${reason}`
})
</script>

<template>
  <div class="space-y-3">
    <AlertMessage type="error" :message="text" />
    <AppButton variant="outline" size="sm" :loading="retrying" @click="$emit('retry')">Reincearca</AppButton>
  </div>
</template>

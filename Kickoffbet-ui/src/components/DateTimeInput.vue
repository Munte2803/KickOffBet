<script setup lang="ts">
import DateChoiceInput from '@/components/DateChoiceInput.vue'

defineProps<{
  label?: string
  modelValue?: string
  error?: string
  min?: string
  required?: boolean
}>()

defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <div class="flex flex-col gap-1">
    <label v-if="label" class="text-sm font-medium text-muted">
      {{ label }}<span v-if="required" class="ml-1 text-red-400">*</span>
    </label>
    <DateChoiceInput
      type="datetime-local"
      :model-value="modelValue"
      :min="min"
      :invalid="Boolean(error)"
      @update:model-value="$emit('update:modelValue', $event)"
    />
    <span v-if="error" class="text-xs text-red-400">{{ error }}</span>
  </div>
</template>


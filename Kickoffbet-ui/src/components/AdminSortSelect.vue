<script setup lang="ts">
defineProps<{
  label?: string
  modelValue?: string
  options: Array<{ label: string; value: string }>
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <label class="block text-sm text-gray-300">
    <span class="mb-1 block">{{ label ?? 'Sorteaza dupa' }}</span>
    <select
      :value="modelValue ?? ''"
      class="app-select-field app-select"
      @change="$emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="option in options" :key="option.value || option.label" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </label>
</template>

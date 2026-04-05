<script setup lang="ts">
import { computed } from 'vue'
import DateChoiceInput from '@/components/DateChoiceInput.vue'

const props = defineProps<{
  label?: string
  error?: string
  type?: string
  placeholder?: string
  modelValue?: string | number
  disabled?: boolean
  required?: boolean
}>()

defineEmits<{ 'update:modelValue': [value: string] }>()

const isDateLikeInput = computed(() =>
  ['date', 'datetime-local', 'time', 'month'].includes(props.type ?? 'text'),
)
</script>

<template>
  <div class="flex flex-col gap-1">
    <label v-if="label" class="text-[11px] font-medium text-gray-300 max-[390px]:text-[10px] max-[360px]:text-[9px] sm:text-sm">
      {{ label }}<span v-if="required" class="text-red-400 ml-1">*</span>
    </label>
    <div v-if="isDateLikeInput">
      <DateChoiceInput
        v-bind="$attrs"
        :type="type ?? 'text'"
        :model-value="String(modelValue ?? '')"
        :disabled="disabled"
        :invalid="Boolean(error)"
        @update:model-value="$emit('update:modelValue', $event)"
      />
    </div>
    <div v-else class="relative">
      <input
        v-bind="$attrs"
        :type="type ?? 'text'"
        :placeholder="placeholder"
        :value="modelValue"
        :disabled="disabled"
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
        :class="[
          'w-full rounded-lg border bg-black/50 px-2.5 py-2 text-[12px] text-white transition-colors focus:outline-none sm:px-3 sm:py-2.5 sm:text-sm',
          'max-[390px]:px-2 max-[390px]:py-1.75 max-[390px]:text-[11px] max-[360px]:px-1.75 max-[360px]:py-1.5 max-[360px]:text-[10px]',
          'placeholder:text-gray-600 disabled:opacity-50 disabled:cursor-not-allowed',
          error ? 'border-red-500 focus:border-red-400' : 'border-white/10 focus:border-blue-500',
        ]"
      />
    </div>
    <span v-if="error" class="text-[10px] text-red-400 max-[390px]:text-[9px] max-[360px]:text-[8px] sm:text-xs">{{ error }}</span>
  </div>
</template>


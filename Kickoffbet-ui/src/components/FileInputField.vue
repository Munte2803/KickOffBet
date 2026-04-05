<script setup lang="ts">
import { computed, ref, useAttrs, watch } from 'vue'
import AppButton from '@/components/AppButton.vue'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  label?: string
  error?: string
  accept?: string
  required?: boolean
  fileName?: string
  helper?: string
  buttonLabel?: string
  clearVersion?: number
}>(), {
  accept: 'image/*',
  required: false,
  fileName: '',
  helper: '',
  buttonLabel: 'Alege fisier',
  clearVersion: 0,
})

const emit = defineEmits<{ select: [file: File | null] }>()

const attrs = useAttrs()
const inputRef = ref<HTMLInputElement | null>(null)
const rootClass = computed(() => attrs.class)
const rootStyle = computed(() => attrs.style)
const inputAttrs = computed(() => {
  const { class: _class, style: _style, ...rest } = attrs
  return rest
})

function openPicker() {
  inputRef.value?.click()
}

function handleChange(event: Event) {
  emit('select', (event.target as HTMLInputElement).files?.[0] ?? null)
}

watch(
  () => props.clearVersion,
  () => {
    if (inputRef.value) {
      inputRef.value.value = ''
    }
  },
)
</script>

<template>
  <div :class="['flex flex-col gap-2', rootClass]" :style="rootStyle">
    <label v-if="label" class="text-sm font-medium text-gray-300">
      {{ label }}<span v-if="required" class="ml-1 text-red-400">*</span>
    </label>

    <input
      ref="inputRef"
      v-bind="inputAttrs"
      type="file"
      :accept="accept"
      class="hidden"
      @change="handleChange"
    >

    <div class="rounded-xl border border-white/10 bg-black/40 p-4">
      <div class="flex flex-wrap items-center gap-3">
        <AppButton variant="outline" @click="openPicker">
          {{ buttonLabel }}
        </AppButton>
        <span class="text-sm text-gray-400">
          {{ fileName || helper }}
        </span>
      </div>
    </div>

    <span v-if="error" class="text-xs text-red-400">{{ error }}</span>
  </div>
</template>

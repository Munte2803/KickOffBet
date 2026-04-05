<script setup lang="ts">
import AppPagination from '@/components/AppPagination.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import { translateActiveLabel } from '@/utils/labels.utils'

export interface AdminEntityPickerItem {
  id: string
  label: string
  subtitle?: string
  imageUrl?: string | null
  active?: boolean
}

const props = withDefaults(defineProps<{
  title: string
  modelValue: string | string[] | undefined
  items: AdminEntityPickerItem[]
  page: number
  totalPages: number
  loading?: boolean
  multiple?: boolean
  emptyMessage?: string
}>(), {
  loading: false,
  multiple: false,
  emptyMessage: 'Nu exista elemente disponibile.',
})

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]]
  'change-page': [page: number]
}>()

function isSelected(id: string) {
  if (props.multiple) {
    return Array.isArray(props.modelValue) && props.modelValue.includes(id)
  }

  return props.modelValue === id
}

function toggleSelection(id: string) {
  if (props.multiple) {
    const current = Array.isArray(props.modelValue) ? props.modelValue : []
    const nextValue = current.includes(id)
      ? current.filter((currentId) => currentId !== id)
      : [...current, id]

    emit('update:modelValue', nextValue)
    return
  }

  emit('update:modelValue', id)
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h3 class="text-sm font-semibold text-white">{{ title }}</h3>
        <p class="text-xs text-gray-400">
          {{ multiple ? `${Array.isArray(modelValue) ? modelValue.length : 0} selectate` : 'Alege un element din lista' }}
        </p>
      </div>
    </div>

    <div v-if="loading" class="rounded-xl border border-white/10 bg-black/30 px-4 py-8 text-center text-sm text-gray-400">
      Se incarca lista...
    </div>

    <div v-else-if="items.length" class="space-y-2">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="flex w-full items-center justify-between gap-3 rounded-xl border px-4 py-3 text-left transition-colors"
        :class="isSelected(item.id) ? 'border-blue-500/40 bg-blue-500/10' : 'border-white/10 bg-black/30 hover:border-blue-700'"
        @click="toggleSelection(item.id)"
      >
        <div class="flex min-w-0 items-center gap-3">
          <LogoFrame v-if="item.imageUrl" :src="item.imageUrl" size="sm" />
          <div class="min-w-0">
            <p class="truncate text-sm font-semibold text-white">{{ item.label }}</p>
            <p v-if="item.subtitle" class="truncate text-xs text-gray-400">{{ item.subtitle }}</p>
          </div>
        </div>

        <div class="flex flex-shrink-0 items-center gap-2">
          <span
            v-if="typeof item.active === 'boolean'"
            class="rounded-full border px-2 py-0.5 text-[10px] font-semibold"
            :class="item.active ? 'border-green-500/30 bg-green-500/10 text-green-300' : 'border-white/10 bg-white/5 text-gray-400'"
          >
            {{ translateActiveLabel(item.active) }}
          </span>
          <span
            class="flex h-5 w-5 items-center justify-center rounded-full border text-[10px]"
            :class="isSelected(item.id) ? 'border-blue-400 bg-blue-500 text-white' : 'border-white/15 text-gray-500'"
          >
            {{ isSelected(item.id) ? 'OK' : '+' }}
          </span>
        </div>
      </button>
    </div>

    <EmptyState v-else :message="emptyMessage" />

    <AppPagination :page="page" :total-pages="totalPages" @change="$emit('change-page', $event)" />
  </div>
</template>


<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  totalPages: number
}>()

defineEmits<{ change: [page: number] }>()

const visibleItems = computed(() => {
  if (props.totalPages <= 7) {
    return Array.from({ length: props.totalPages }, (_, index) => index)
  }

  const items: Array<number | 'ellipsis-left' | 'ellipsis-right'> = [0]
  const start = Math.max(1, props.page - 1)
  const end = Math.min(props.totalPages - 2, props.page + 1)

  if (start > 1) {
    items.push('ellipsis-left')
  }

  for (let current = start; current <= end; current += 1) {
    items.push(current)
  }

  if (end < props.totalPages - 2) {
    items.push('ellipsis-right')
  }

  items.push(props.totalPages - 1)
  return items
})
</script>

<template>
  <div v-if="totalPages > 1" class="mt-6 flex justify-center">
    <div class="inline-flex max-w-full items-center gap-0.5 rounded-full border border-white/10 bg-white/5 px-1 py-1 sm:gap-1 sm:px-2 sm:py-2">
      <button
        type="button"
        :disabled="page === 0"
        class="rounded-full px-2 py-1.25 text-[11px] text-gray-300 transition-colors hover:bg-white/10 hover:text-white disabled:cursor-not-allowed disabled:opacity-40 sm:px-3 sm:py-1.5 sm:text-sm"
        @click="$emit('change', page - 1)"
      >
        Prev
      </button>

      <template v-for="item in visibleItems" :key="String(item)">
        <span v-if="typeof item !== 'number'" class="px-0.5 text-[11px] text-gray-500 sm:text-sm">...</span>
        <button
          v-else
          type="button"
          :class="[
            'min-w-7 rounded-full px-2 py-1.25 text-[11px] transition-colors sm:min-w-9 sm:px-3 sm:py-1.5 sm:text-sm',
            item === page ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-white/10 hover:text-white',
          ]"
          @click="$emit('change', item)"
        >
          {{ item + 1 }}
        </button>
      </template>

      <button
        type="button"
        :disabled="page >= totalPages - 1"
        class="rounded-full px-2 py-1.25 text-[11px] text-gray-300 transition-colors hover:bg-white/10 hover:text-white disabled:cursor-not-allowed disabled:opacity-40 sm:px-3 sm:py-1.5 sm:text-sm"
        @click="$emit('change', page + 1)"
      >
        Next
      </button>
    </div>
  </div>
</template>

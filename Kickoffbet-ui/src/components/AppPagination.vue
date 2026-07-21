<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    page: number
    totalPages: number
    totalElements?: number
    scrollTarget?: string
    scrollOnChange?: boolean
  }>(),
  { scrollOnChange: false },
)

const emit = defineEmits<{ change: [page: number] }>()

const rootEl = ref<HTMLElement | null>(null)

function changePage(next: number) {
  if (next === props.page) return
  emit('change', next)
  if (!props.scrollOnChange) return
  nextTick(() => {
    let target: Element | null = null
    if (props.scrollTarget) target = document.querySelector(props.scrollTarget)
    if (!target && rootEl.value) target = rootEl.value.closest('[data-list-top]')
    if (!target && rootEl.value) target = rootEl.value.closest('[id]')
    if (!target && rootEl.value) target = rootEl.value.parentElement
    target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

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
  <div ref="rootEl" v-if="totalPages > 1 || (totalElements ?? 0) > 0" class="mt-6 flex flex-col items-center gap-2">
    <div v-if="totalPages > 1" class="inline-flex max-w-full items-center gap-0.5 rounded-full border border-line bg-surface px-1 py-1 sm:gap-1 sm:px-2 sm:py-2">
      <button
        type="button"
        :disabled="page === 0"
        class="rounded-full px-2 py-1.25 text-[11px] text-muted transition-colors hover:bg-surface-2 hover:text-fg disabled:cursor-not-allowed disabled:opacity-40 sm:px-3 sm:py-1.5 sm:text-sm"
        @click="changePage(page - 1)"
      >
        Prev
      </button>

      <template v-for="item in visibleItems" :key="String(item)">
        <span v-if="typeof item !== 'number'" class="px-0.5 text-[11px] text-subtle sm:text-sm">...</span>
        <button
          v-else
          type="button"
          :class="[
            'min-w-7 rounded-full px-2 py-1.25 text-[11px] transition-colors sm:min-w-9 sm:px-3 sm:py-1.5 sm:text-sm',
            item === page ? 'bg-blue-600 text-white' : 'text-muted hover:bg-surface-2 hover:text-fg',
          ]"
          @click="changePage(item)"
        >
          {{ item + 1 }}
        </button>
      </template>

      <button
        type="button"
        :disabled="page >= totalPages - 1"
        class="rounded-full px-2 py-1.25 text-[11px] text-muted transition-colors hover:bg-surface-2 hover:text-fg disabled:cursor-not-allowed disabled:opacity-40 sm:px-3 sm:py-1.5 sm:text-sm"
        @click="changePage(page + 1)"
      >
        Next
      </button>
    </div>
    <p v-if="totalElements != null" class="text-[11px] text-muted sm:text-xs">
      {{ totalElements.toLocaleString() }} {{ totalElements === 1 ? 'rezultat' : 'rezultate' }}
    </p>
  </div>
</template>

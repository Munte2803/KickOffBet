<script setup lang="ts" generic="T">
import { onBeforeUnmount, ref } from 'vue'
import type { ExportColumn } from '@/utils/export.utils'
import { exportToCsv, exportToPdf } from '@/utils/export.utils'

const props = defineProps<{
  fetchAll: () => Promise<T[]>
  columns: ExportColumn<T>[]
  filename: string
  title: string
  disabled?: boolean
}>()

const open = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

async function doExport(format: 'csv' | 'pdf') {
  open.value = false
  loading.value = true
  error.value = null
  try {
    const rows = await props.fetchAll()
    if (format === 'csv') {
      exportToCsv(rows, props.columns, props.filename)
    } else {
      exportToPdf(rows, props.columns, props.filename, props.title)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Export failed'
  } finally {
    loading.value = false
  }
}

const rootEl = ref<HTMLElement | null>(null)

function toggle() {
  if (loading.value || props.disabled) return
  open.value = !open.value
}

function handleDocClick(e: MouseEvent) {
  if (!open.value || !rootEl.value) return
  if (!rootEl.value.contains(e.target as Node)) {
    open.value = false
  }
}

document.addEventListener('click', handleDocClick)
onBeforeUnmount(() => document.removeEventListener('click', handleDocClick))
</script>

<template>
  <div ref="rootEl" class="relative inline-block">
    <button
      type="button"
      :disabled="loading || disabled"
      :title="error ?? 'Export'"
      class="inline-flex items-center justify-center gap-2 rounded-lg border border-white/10 bg-white/5 px-3 py-1.75 text-[12px] font-bold text-white transition-all duration-200 hover:border-blue-600 hover:bg-white/10 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50 max-[390px]:px-2.5 max-[390px]:py-1.5 max-[390px]:text-[11px] max-[360px]:px-2 max-[360px]:py-1.25 max-[360px]:text-[10px] sm:px-4 sm:py-2 sm:text-sm"
      @click="toggle"
    >
      <svg
        v-if="!loading"
        class="h-4 w-4"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        stroke-width="2"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M12 4v12m0 0l-4-4m4 4l4-4M4 20h16"
        />
      </svg>
      <svg
        v-else
        class="h-4 w-4 animate-spin"
        fill="none"
        viewBox="0 0 24 24"
      >
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path
          class="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
        />
      </svg>
      <span>{{ loading ? 'Se exporta…' : 'Export' }}</span>
    </button>

    <Transition
      enter-active-class="transition duration-100 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="open"
        class="absolute right-0 z-20 mt-2 min-w-36 origin-top-right overflow-hidden rounded-lg border border-slate-700/50 bg-slate-800 py-1 shadow-xl"
      >
        <button
          type="button"
          class="block w-full px-4 py-2.5 text-left text-sm text-gray-100 hover:bg-slate-700/70"
          @click="doExport('csv')"
        >
          CSV
        </button>
        <button
          type="button"
          class="block w-full px-4 py-2.5 text-left text-sm text-gray-100 hover:bg-slate-700/70"
          @click="doExport('pdf')"
        >
          PDF
        </button>
      </div>
    </Transition>
  </div>
</template>

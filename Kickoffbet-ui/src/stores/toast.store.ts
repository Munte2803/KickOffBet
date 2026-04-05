import { defineStore } from 'pinia'
import { ref } from 'vue'

interface Toast {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let idSeq = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])

  function show(type: Toast['type'], message: string, duration = 4000) {
    const id = ++idSeq
    toasts.value.push({ id, type, message })
    setTimeout(() => dismiss(id), duration)
  }

  const showSuccess = (message: string) => show('success', message)
  const showError = (message: string) => show('error', message)
  const showInfo = (message: string) => show('info', message)

  function dismiss(id: number) {
    const idx = toasts.value.findIndex((t) => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  return { toasts, showSuccess, showError, showInfo, dismiss }
})

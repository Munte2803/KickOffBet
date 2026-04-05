import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ConfirmDialogOptions {
  title: string
  message: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'primary' | 'danger'
}

let resolver: ((value: boolean) => void) | null = null

export const useConfirmStore = defineStore('confirm', () => {
  const open = ref(false)
  const title = ref('')
  const message = ref('')
  const description = ref('')
  const confirmLabel = ref('Confirma')
  const cancelLabel = ref('Renunta')
  const variant = ref<'primary' | 'danger'>('primary')

  function resetState() {
    open.value = false
    title.value = ''
    message.value = ''
    description.value = ''
    confirmLabel.value = 'Confirma'
    cancelLabel.value = 'Renunta'
    variant.value = 'primary'
  }

  function ask(options: ConfirmDialogOptions) {
    if (resolver) {
      resolver(false)
      resolver = null
    }

    title.value = options.title
    message.value = options.message
    description.value = options.description ?? ''
    confirmLabel.value = options.confirmLabel ?? 'Confirma'
    cancelLabel.value = options.cancelLabel ?? 'Renunta'
    variant.value = options.variant ?? 'primary'
    open.value = true

    return new Promise<boolean>((resolve) => {
      resolver = resolve
    })
  }

  function settle(value: boolean) {
    const currentResolver = resolver
    resolver = null
    resetState()
    currentResolver?.(value)
  }

  return {
    open,
    title,
    message,
    description,
    confirmLabel,
    cancelLabel,
    variant,
    ask,
    confirm: () => settle(true),
    cancel: () => settle(false),
  }
})

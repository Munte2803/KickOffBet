import { useConfirmStore, type ConfirmDialogOptions } from '@/stores/confirm.store'

export function useConfirmDialog() {
  const confirmStore = useConfirmStore()

  function confirm(options: ConfirmDialogOptions) {
    return confirmStore.ask(options)
  }

  return { confirm }
}

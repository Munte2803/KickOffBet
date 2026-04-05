import { computed, ref } from 'vue'

export function usePagination(initialSize = 12, initialSort?: string) {
  const page = ref(0)
  const size = ref(initialSize)
  const sort = ref(initialSort)

  const request = computed(() => ({
    page: page.value,
    size: size.value,
    ...(sort.value ? { sort: sort.value } : {}),
  }))

  function setPage(nextPage: number) {
    page.value = Math.max(0, nextPage)
  }

  function reset() {
    page.value = 0
  }

  return {
    page,
    size,
    sort,
    request,
    setPage,
    reset,
  }
}

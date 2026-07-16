import type { PageRequest } from '@/types/api.types'

/**
 * Returns the pageable with `sort` defaulted when callers omit it.
 * Centralizes the `pageable?.sort ?? 'createdAt,desc'` pattern duplicated across admin APIs.
 */
export function withDefaultSort(pageable?: PageRequest, fallback = 'createdAt,desc'): PageRequest {
  return { ...pageable, sort: pageable?.sort ?? fallback }
}

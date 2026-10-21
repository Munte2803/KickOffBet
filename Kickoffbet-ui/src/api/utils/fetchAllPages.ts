import type { PageRequest, PageResponse } from '@/types/api.types'

/** The backend caps every page at 50 rows (WebConfig maxPageSize), whatever `size` is requested. */
export const MAX_API_PAGE_SIZE = 50
/** Upper bound for a single export, so a runaway filter cannot fire hundreds of requests. */
export const MAX_EXPORT_ROWS = 10_000

export type ExportProgress = (loaded: number, total: number) => void

export interface FetchAllPagesOptions {
  sort?: string
  onProgress?: ExportProgress
  maxRows?: number
}

/**
 * Loads every page of a paged endpoint (50 rows at a time, until the server reports the last page)
 * and returns the concatenated rows. Refuses result sets larger than `maxRows` instead of silently
 * truncating them.
 */
export async function fetchAllPages<T>(
  fetchPage: (pageable: PageRequest) => Promise<PageResponse<T>>,
  { sort, onProgress, maxRows = MAX_EXPORT_ROWS }: FetchAllPagesOptions = {},
): Promise<T[]> {
  const rows: T[] = []
  const maxPages = Math.ceil(maxRows / MAX_API_PAGE_SIZE)

  for (let page = 0; page < maxPages; page++) {
    const pageable: PageRequest = { page, size: MAX_API_PAGE_SIZE }
    if (sort) pageable.sort = sort
    const response = await fetchPage(pageable)

    if (response.totalElements > maxRows) {
      throw new Error(
        `Exportul are ${response.totalElements} randuri, peste limita de ${maxRows}. Restrange filtrele si incearca din nou.`,
      )
    }

    rows.push(...response.content)
    onProgress?.(rows.length, response.totalElements)

    if (response.last || !response.content.length) {
      return rows
    }
  }

  throw new Error(`Exportul depaseste limita de ${maxRows} randuri. Restrange filtrele si incearca din nou.`)
}

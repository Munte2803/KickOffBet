import { describe, expect, it, vi } from 'vitest'
import { fetchAllPages, MAX_API_PAGE_SIZE, withIdTiebreaker } from '@/api/utils/fetchAllPages'
import api from '@/api/axios'
import type { PageRequest, PageResponse } from '@/types/api.types'

function pagedSource(total: number) {
  const rows = Array.from({ length: total }, (_, index) => index)
  return vi.fn(async ({ page = 0, size = 20 }: PageRequest): Promise<PageResponse<number>> => {
    const content = rows.slice(page * size, page * size + size)
    return {
      content,
      totalElements: total,
      totalPages: Math.ceil(total / size),
      number: page,
      size,
      first: page === 0,
      last: (page + 1) * size >= total,
      sort: { sorted: true, unsorted: false, empty: false },
    }
  })
}

describe('fetchAllPages', () => {
  it('walks the pages at the backend maximum size until the last one and concatenates them', async () => {
    const fetchPage = pagedSource(120)
    const onProgress = vi.fn()

    const rows = await fetchAllPages(fetchPage, { sort: 'createdAt,desc', onProgress })

    expect(rows).toEqual(Array.from({ length: 120 }, (_, index) => index))
    expect(fetchPage.mock.calls.map(([pageable]) => pageable)).toEqual([
      { page: 0, size: MAX_API_PAGE_SIZE, sort: ['createdAt,desc', 'id,asc'] },
      { page: 1, size: MAX_API_PAGE_SIZE, sort: ['createdAt,desc', 'id,asc'] },
      { page: 2, size: MAX_API_PAGE_SIZE, sort: ['createdAt,desc', 'id,asc'] },
    ])
    expect(onProgress.mock.calls).toEqual([[50, 120], [100, 120], [120, 120]])
  })

  it('refuses result sets above the limit instead of silently truncating them', async () => {
    const fetchPage = pagedSource(120)
    await expect(fetchAllPages(fetchPage, { sort: 'name,asc', maxRows: 100 })).rejects.toThrow('peste limita de 100')
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('adds id as a tie-breaker so ties in the sort field cannot repeat or skip rows between pages', () => {
    expect(withIdTiebreaker('startTime,desc')).toEqual(['startTime,desc', 'id,asc'])
    expect(withIdTiebreaker('id,desc')).toEqual(['id,desc'])
  })

  it('sends the sorts as repeated params, the form Spring binds', () => {
    const url = api.getUri({ url: '/api/admin/matches/search', params: { page: 0, sort: withIdTiebreaker('startTime,desc') } })
    expect(decodeURIComponent(url)).toContain('page=0&sort=startTime,desc&sort=id,asc')
  })
})

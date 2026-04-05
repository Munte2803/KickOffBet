import api from '@/api/axios'
import type { CreateLeagueRequest, UpdateLeagueRequest, LeagueDetail, LeagueList } from '../types/league.types'
import type { PageRequest, PageResponse } from '@/types/api.types'

export const createLeague = (
  data: CreateLeagueRequest,
  emblem?: File
): Promise<LeagueDetail> => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (emblem) {
    formData.append('emblem', emblem)
  }
  return api.post('/api/admin/leagues', formData).then(res => res.data)
}

export const getAllLeagues = (): Promise<LeagueList[]> =>
  api.get('/api/admin/leagues').then(res => res.data)

export const getLeaguePage = (pageable?: PageRequest): Promise<PageResponse<LeagueList>> =>
  api.get('/api/admin/leagues/paged', { params: pageable })
    .then(res => res.data)
    .catch(async (error) => {
      const message = error instanceof Error ? error.message : ''
      if (!message.includes('404')) {
        throw error
      }

      const leagues = await getAllLeagues()
      const page = pageable?.page ?? 0
      const size = pageable?.size ?? 10
      const start = page * size
      const content = leagues.slice(start, start + size)

      return {
        content,
        totalPages: Math.max(1, Math.ceil(leagues.length / size)),
        totalElements: leagues.length,
        number: page,
        size,
        first: page === 0,
        last: start + size >= leagues.length,
        sort: {
          sorted: false,
          unsorted: true,
          empty: true,
        },
      }
    })

export const getLeagueByCode = (code: string): Promise<LeagueDetail> =>
  api.get(`/api/admin/leagues/${code}`).then(res => res.data)

export const updateLeague = (
  code: string,
  data: UpdateLeagueRequest,
  emblem?: File
): Promise<LeagueDetail> => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (emblem) {
    formData.append('emblem', emblem)
  }
  return api.put(`/api/admin/leagues/${code}`, formData).then(res => res.data)
}

export const toggleLeagueStatus = (code: string, active: boolean): Promise<void> =>
  api.patch(`/api/admin/leagues/${code}/switch-active`, undefined, { params: { active } }).then(() => {})


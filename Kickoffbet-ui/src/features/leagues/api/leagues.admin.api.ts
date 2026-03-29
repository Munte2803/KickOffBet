import api from '@/api/axios'
import type { CreateLeagueRequest, UpdateLeagueRequest, LeagueDetail } from '../types/league.types'

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

export const getAllLeagues = (): Promise<LeagueDetail[]> =>
  api.get('/api/admin/leagues').then(res => res.data)

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

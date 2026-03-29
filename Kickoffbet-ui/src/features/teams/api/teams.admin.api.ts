import api from '@/api/axios'
import type { CreateTeamRequest, UpdateTeamRequest, TeamList, TeamDetail } from '../types/team.types'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const createTeam = (
  data: CreateTeamRequest,
  emblem?: File
): Promise<TeamDetail> => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (emblem) {
    formData.append('emblem', emblem)
  }
  return api.post('/api/admin/teams', formData).then(res => res.data)
}

export const getAllTeams = (pageable?: PageRequest): Promise<PageResponse<TeamList>> =>
  api.get('/api/admin/teams', { params: pageable }).then(res => res.data)

export const getTeamById = (id: string): Promise<TeamDetail> =>
  api.get(`/api/admin/teams/${id}`).then(res => res.data)

export const updateTeam = (
  id: string,
  data: UpdateTeamRequest,
  emblem?: File
): Promise<TeamDetail> => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (emblem) {
    formData.append('emblem', emblem)
  }
  return api.put(`/api/admin/teams/${id}`, formData).then(res => res.data)
}

export const toggleTeamStatus = (id: string, active: boolean): Promise<void> =>
  api.patch(`/api/admin/teams/${id}/switch-active`, undefined, { params: { active } }).then(() => {})

import api from '@/api/axios'
import type { TeamList, TeamDetail } from '../types/team.types'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const getTeams = (pageable?: PageRequest): Promise<PageResponse<TeamList>> =>
  api.get('/api/teams', { params: pageable }).then(res => res.data)

export const getTeamById = (id: string): Promise<TeamDetail> =>
  api.get(`/api/teams/${id}`).then(res => res.data)

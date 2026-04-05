import api from '@/api/axios'
import type { LeagueList, LeagueDetail, LeaguePreview } from '../types/league.types'
import type { MatchStatus } from '@/types/enums'

export const getLeagues = (): Promise<LeagueList[]> =>
  api.get('/api/leagues').then(res => res.data)

export const getLeaguePreviews = (
  status: MatchStatus = 'SCHEDULED',
  size = 3,
): Promise<LeaguePreview[]> =>
  api.get('/api/leagues/previews', {
    params: { status, size },
  }).then(res => res.data)

export const getLeagueByCode = (code: string): Promise<LeagueDetail> =>
  api.get(`/api/leagues/${code}`).then(res => res.data)


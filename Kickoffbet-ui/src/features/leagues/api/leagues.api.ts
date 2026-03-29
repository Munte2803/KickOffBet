import api from '@/api/axios'
import type { LeagueList, LeagueDetail } from '../types/league.types'

export const getLeagues = (): Promise<LeagueList[]> =>
  api.get('/api/leagues').then(res => res.data)

export const getLeagueByCode = (code: string): Promise<LeagueDetail> =>
  api.get(`/api/leagues/${code}`).then(res => res.data)

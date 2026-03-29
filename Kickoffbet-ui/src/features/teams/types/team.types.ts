import type { LeagueList } from '@/features/leagues/types/league.types'

export interface TeamList {
  id: string
  name: string
  shortName: string
  tla: string
  crestUrl: string | null
  active: boolean
}

export interface TeamDetail extends TeamList {
  createdAt: string
  updatedAt: string
  leagues: LeagueList[]
}

export interface CreateTeamRequest {
  name: string
  shortName: string
  tla: string
  leagueIds?: string[]
}

export interface UpdateTeamRequest {
  name: string
  shortName: string
  tla: string
  leagueIds?: string[]
}
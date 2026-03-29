import type { TeamList } from '@/features/teams/types/team.types'

export interface LeagueList {
  id: string
  name: string
  code: string
  emblemUrl: string | null
  active: boolean
}

export interface LeagueDetail extends LeagueList {
  createdAt: string
  updatedAt: string
  teams: TeamList[]
}

export interface CreateLeagueRequest {
  name: string
  code: string
  teamIds?: string[]
}

export interface UpdateLeagueRequest {
  name: string
  teamIds?: string[]
}
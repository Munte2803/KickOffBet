import type { TeamList } from '@/types/team.types'
import type { MatchList } from '@/types/match.types'

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

export interface LeaguePreview extends LeagueList {
  previewMatches: MatchList[]
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


import type { MatchStatus, MarketType, BetOption } from '@/shared/types/enums'

export interface MarketOffer {
  id: string
  matchId: string
  marketType: MarketType
  option: BetOption
  line: number | null
  odds: number
  active: boolean
}

export interface MatchList {
  id: string
  leagueId: string
  leagueName: string
  leagueLogo: string | null
  homeTeamId: string
  homeTeamLogo: string | null
  homeTeamName: string
  awayTeamId: string
  awayTeamLogo: string | null
  awayTeamName: string
  startTime: string
  status: MatchStatus
  ftHome: number | null
  ftAway: number | null
  active: boolean
  odd1: MarketOffer | null
  oddX: MarketOffer | null
  odd2: MarketOffer | null
}

export interface MatchDetail extends Omit<MatchList, 'odd1' | 'oddX' | 'odd2'> {
  createdAt: string
  updatedAt: string
  availableOffers: MarketOffer[]
}

export interface CreateMarketOfferRequest {
  marketType: MarketType
  option: BetOption
  line?: number | null
  odds: number
}

export interface CreateMatchRequest {
  startTime: string
  leagueId: string
  homeTeamId: string
  awayTeamId: string
  availableOffers?: CreateMarketOfferRequest[]
}

export interface UpdateMarketOfferRequest {
  id?: string
  odds: number
}

export interface UpdateMatchRequest {
  availableOffers?: UpdateMarketOfferRequest[]
}

export interface UpdateMatchStatusRequest {
  status: MatchStatus
  ftHome?: number | null
  ftAway?: number | null
}

export interface MatchSearchRequest {
  leagueCode?: string
  status?: MatchStatus
  teamId?: string
  active?: boolean
  startDate?: string
  endDate?: string
  manualUpdate?: boolean
  startTimeBefore?: string
}
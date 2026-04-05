import type { TicketStatus, TicketSelectionStatus, MarketType, BetOption } from '@/types/enums'

export interface TicketSelection {
  id: string
  matchId: string
  homeTeamName: string
  homeTeamLogo: string | null
  awayTeamName: string
  awayTeamLogo: string | null
  matchStartTime: string
  ftHome: number | null
  ftAway: number | null
  marketType: MarketType
  selectedOption: BetOption
  line: number | null
  oddsAtPlacement: number
  status: TicketSelectionStatus
}

export interface Ticket {
  id: string
  userId: string
  userEmail: string
  stake: number
  totalOdd: number
  potentialPayout: number
  status: TicketStatus
  createdAt: string
  updatedAt: string
  selections: TicketSelection[]
}

export interface TicketSelectionRequest {
  marketOfferId: string
  oddsAtPlacement: number
}

export interface PlaceTicketRequest {
  stake: number
  selections: TicketSelectionRequest[]
}


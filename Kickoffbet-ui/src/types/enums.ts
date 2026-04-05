export type MatchStatus = 'SCHEDULED' | 'LIVE' | 'FINISHED' | 'CANCELLED' | 'POSTPONED' | 'SUSPENDED' | 'UNKNOWN'

export type TicketStatus = 'PENDING' | 'WON' | 'LOST' | 'CANCELLED'

export type TicketSelectionStatus = 'PENDING' | 'WON' | 'LOST' | 'CANCELLED'

export type UserStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DECLINED' | 'DEACTIVATED'

export type UserRole = 'USER' | 'ADMIN'

export type MarketType = 'H2H' | 'OVER_UNDER' | 'BTTS' | 'DOUBLE_CHANCE'

export type BetOption =
  | 'HOME' | 'DRAW' | 'AWAY'
  | 'OVER' | 'UNDER'
  | 'YES' | 'NO'
  | 'HOME_OR_DRAW' | 'AWAY_OR_DRAW' | 'HOME_OR_AWAY'

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'BET' | 'PAYOUT' | 'REFUND'

export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'REJECTED'
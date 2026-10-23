/**
 * The backend enums, as const arrays (for <option> lists, tabs and validation) with the union types
 * derived from them, so a value list and its type can never drift apart. Labels: translateEnumLabel.
 */

export const MATCH_STATUSES = ['SCHEDULED', 'LIVE', 'FINISHED', 'POSTPONED', 'SUSPENDED', 'CANCELLED', 'UNKNOWN'] as const
export type MatchStatus = (typeof MATCH_STATUSES)[number]

/** The statuses an admin can set on a match (UNKNOWN only comes from the data provider). */
export const EDITABLE_MATCH_STATUSES = MATCH_STATUSES.filter(
  (status): status is Exclude<MatchStatus, 'UNKNOWN'> => status !== 'UNKNOWN',
)

export const TICKET_STATUSES = ['PENDING', 'WON', 'LOST', 'CANCELLED'] as const
export type TicketStatus = (typeof TICKET_STATUSES)[number]

export const TICKET_SELECTION_STATUSES = ['PENDING', 'WON', 'LOST', 'CANCELLED'] as const
export type TicketSelectionStatus = (typeof TICKET_SELECTION_STATUSES)[number]

export const USER_STATUSES = ['PENDING', 'ACTIVE', 'SUSPENDED', 'DECLINED', 'DEACTIVATED'] as const
export type UserStatus = (typeof USER_STATUSES)[number]

export const USER_ROLES = ['USER', 'ADMIN'] as const
export type UserRole = (typeof USER_ROLES)[number]

export const MARKET_TYPES = ['H2H', 'OVER_UNDER', 'BTTS', 'DOUBLE_CHANCE'] as const
export type MarketType = (typeof MARKET_TYPES)[number]

export const BET_OPTIONS = [
  'HOME', 'DRAW', 'AWAY',
  'OVER', 'UNDER',
  'YES', 'NO',
  'HOME_OR_DRAW', 'AWAY_OR_DRAW', 'HOME_OR_AWAY',
] as const
export type BetOption = (typeof BET_OPTIONS)[number]

export const TRANSACTION_TYPES = ['DEPOSIT', 'WITHDRAWAL', 'BET', 'PAYOUT', 'REFUND'] as const
export type TransactionType = (typeof TRANSACTION_TYPES)[number]

export const TRANSACTION_STATUSES = ['PENDING', 'COMPLETED', 'REJECTED'] as const
export type TransactionStatus = (typeof TRANSACTION_STATUSES)[number]

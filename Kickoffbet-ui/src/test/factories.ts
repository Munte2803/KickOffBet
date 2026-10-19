import type { MarketOffer, MatchList } from '@/types/match.types'
import type { BetOption, MarketType } from '@/types/enums'
import type { UserProfile } from '@/types/profile.types'
import type { Transaction } from '@/types/transaction.types'
import type { Ticket } from '@/types/ticket.types'

export function makeOffer(overrides: Partial<MarketOffer> & { id: string }): MarketOffer {
  return {
    matchId: 'match-1',
    marketType: 'H2H' as MarketType,
    option: 'HOME' as BetOption,
    line: null,
    odds: 2,
    active: true,
    ...overrides,
  }
}

export function makeMatch(overrides: Partial<MatchList> = {}): MatchList {
  const id = overrides.id ?? 'match-1'
  return {
    id,
    leagueId: 'league-1',
    leagueCode: 'PL',
    leagueName: 'Premier League',
    leagueLogo: null,
    homeTeamId: 'team-home',
    homeTeamLogo: null,
    homeTeamName: 'Arsenal FC',
    homeTeamTla: 'ARS',
    awayTeamId: 'team-away',
    awayTeamLogo: null,
    awayTeamName: 'Chelsea FC',
    awayTeamTla: 'CHE',
    startTime: '2026-07-01T18:00:00',
    status: 'SCHEDULED',
    ftHome: null,
    ftAway: null,
    active: true,
    odd1: makeOffer({ id: `${id}-1`, matchId: id, option: 'HOME', odds: 1.8 }),
    oddX: makeOffer({ id: `${id}-x`, matchId: id, option: 'DRAW', odds: 3.4 }),
    odd2: makeOffer({ id: `${id}-2`, matchId: id, option: 'AWAY', odds: 4.2 }),
    ...overrides,
  }
}

export function makeProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: 'user-1',
    firstName: 'Ana',
    lastName: 'Pop',
    email: 'ana@example.com',
    balance: 500,
    birthDate: '1990-01-01',
    role: 'USER',
    status: 'ACTIVE',
    emailVerified: true,
    idCardVerified: true,
    idCardUrl: null,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

export function makeTransaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 'tx-1',
    userId: 'user-1',
    userEmail: 'ana@example.com',
    userFirstName: 'Ana',
    userLastName: 'Pop',
    amount: 150,
    referenceId: null,
    referenceType: null,
    transactionType: 'DEPOSIT',
    status: 'COMPLETED',
    createdAt: '2026-07-01T09:30:00',
    updatedAt: '2026-07-01T09:30:00',
    ...overrides,
  }
}

export function makeTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 'ticket-1',
    userId: 'user-1',
    userEmail: 'ana@example.com',
    stake: 10,
    totalOdd: 2,
    potentialPayout: 20,
    status: 'PENDING',
    createdAt: '2026-07-01T09:30:00',
    updatedAt: '2026-07-01T09:30:00',
    selections: [],
    ...overrides,
  }
}

import { describe, expect, it } from 'vitest'
import { adminTopTabs, getAdminResource } from '@/constants/admin-navigation'
import {
  MAX_DEPOSIT,
  MAX_STAKE,
  MAX_TICKET_SELECTIONS,
  MAX_WIN,
  MAX_WITHDRAWAL,
  MIN_DEPOSIT,
  MIN_STAKE,
  MIN_WITHDRAWAL,
} from '@/constants/betting.constants'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import router from '@/router'

describe('betting constants mirror the backend validation limits', () => {
  // DepositRequest / WithdrawRequest / PlaceTicketRequest / TicketServiceImpl.MAX_WIN
  it('deposit and withdrawal limits', () => {
    expect([MIN_DEPOSIT, MAX_DEPOSIT]).toEqual([20, 200000])
    expect([MIN_WITHDRAWAL, MAX_WITHDRAWAL]).toEqual([20, 200000])
  })

  it('stake, win and selection limits', () => {
    expect([MIN_STAKE, MAX_STAKE]).toEqual([2, 50000])
    expect(MAX_WIN).toBe(200000)
    expect(MAX_TICKET_SELECTIONS).toBe(20)
  })
})

describe('PAGE_SIZES', () => {
  it('are positive integers in ascending order', () => {
    const values = Object.values(PAGE_SIZES)
    for (const value of values) {
      expect(Number.isInteger(value) && value > 0).toBe(true)
    }
    expect([...values]).toEqual([...values].sort((a, b) => a - b))
  })
})

describe('admin navigation', () => {
  it('every top tab points to an existing named route', () => {
    for (const tab of adminTopTabs) {
      const name = (tab.to as { name: string }).name
      expect(router.hasRoute(name), name).toBe(true)
      expect(getAdminResource(name)).toBe(tab.resource)
    }
  })

  it('maps every admin route to its resource', () => {
    const expectations: Record<string, string> = {
      'admin-matches': 'matches',
      'admin-matches-create': 'matches',
      'admin-match-detail': 'matches',
      'admin-leagues': 'leagues',
      'admin-league-detail': 'leagues',
      'admin-teams': 'teams',
      'admin-team-detail': 'teams',
      'admin-users': 'users',
      'admin-user-detail': 'users',
      'admin-transactions': 'transactions',
      'admin-transaction-detail': 'transactions',
      'admin-tickets': 'tickets',
      'admin-ticket-detail': 'tickets',
      'admin-sync': 'sync',
    }
    for (const [routeName, resource] of Object.entries(expectations)) {
      expect(router.hasRoute(routeName), routeName).toBe(true)
      expect(getAdminResource(routeName), routeName).toBe(resource)
    }
  })

  it('returns null for the dashboard, non-admin or missing route names', () => {
    expect(getAdminResource('admin-dashboard')).toBeNull()
    expect(getAdminResource('home')).toBeNull()
    expect(getAdminResource(undefined)).toBeNull()
    expect(getAdminResource('')).toBeNull()
  })
})

import { describe, expect, it } from 'vitest'
import { formatMoney } from '@/utils/money.utils'
import { formatBetLabel, getMarketDisplayName, sortMarketOffers } from '@/utils/odds.utils'
import { enumLabelMap, translateActiveLabel, translateEnumLabel } from '@/utils/labels.utils'
import { getTeamTag } from '@/utils/team.utils'
import type { MarketOffer } from '@/types/match.types'
import type { BetOption, MarketType } from '@/types/enums'

// Intl uses a narrow no-break space in some locales; normalise whitespace for comparisons.
const norm = (value: string) => value.replace(/\s/g, ' ')

describe('formatMoney', () => {
  it('formats numbers as RON with ro-RO separators and two decimals', () => {
    expect(norm(formatMoney(1234.5))).toBe('1.234,50 RON')
    expect(norm(formatMoney(0))).toBe('0,00 RON')
    expect(norm(formatMoney(200000))).toBe('200.000,00 RON')
  })

  it('rounds to two decimals', () => {
    expect(norm(formatMoney(10.005))).toMatch(/^10,0[01] RON$/)
    expect(norm(formatMoney(2.499))).toBe('2,50 RON')
  })

  it('accepts numeric strings (BigDecimal values serialised as strings)', () => {
    expect(norm(formatMoney('15.5'))).toBe('15,50 RON')
  })

  it('formats negative amounts', () => {
    expect(norm(formatMoney(-25))).toBe('-25,00 RON')
  })

  it('falls back to 0,00 RON for null/undefined', () => {
    expect(formatMoney(null)).toBe('0,00 RON')
    expect(formatMoney(undefined)).toBe('0,00 RON')
  })
})

describe('formatBetLabel', () => {
  it.each<[BetOption, MarketType, string]>([
    ['HOME', 'H2H', '1'],
    ['DRAW', 'H2H', 'X'],
    ['AWAY', 'H2H', '2'],
    ['HOME_OR_DRAW', 'DOUBLE_CHANCE', '1X'],
    ['AWAY_OR_DRAW', 'DOUBLE_CHANCE', 'X2'],
    ['HOME_OR_AWAY', 'DOUBLE_CHANCE', '12'],
    ['YES', 'BTTS', 'Da'],
    ['NO', 'BTTS', 'Nu'],
  ])('%s in %s -> %s', (option, market, expected) => {
    expect(formatBetLabel(option, market)).toBe(expected)
  })

  it('includes the line for over/under markets', () => {
    expect(formatBetLabel('OVER', 'OVER_UNDER', 2.5)).toBe('Peste 2.5')
    expect(formatBetLabel('UNDER', 'OVER_UNDER', 0.5)).toBe('Sub 0.5')
  })

  it('falls back to the raw option for over/under without a line', () => {
    expect(formatBetLabel('OVER', 'OVER_UNDER', null)).toBe('OVER')
    expect(formatBetLabel('UNDER', 'OVER_UNDER')).toBe('UNDER')
  })
})

describe('getMarketDisplayName', () => {
  it('returns a display name for every market type', () => {
    expect(getMarketDisplayName('H2H')).toBe('Rezultat Final')
    expect(getMarketDisplayName('OVER_UNDER')).toBe('Total Goluri')
    expect(getMarketDisplayName('BTTS')).toBe('Ambele Marchează')
    expect(getMarketDisplayName('DOUBLE_CHANCE')).toBe('Șansă Dublă')
  })

  it('falls back to the raw value for unknown market types', () => {
    expect(getMarketDisplayName('CORNERS' as MarketType)).toBe('CORNERS')
  })
})

describe('sortMarketOffers', () => {
  const offer = (id: string, marketType: MarketType, option: BetOption, line: number | null = null): MarketOffer => ({
    id,
    matchId: 'm1',
    marketType,
    option,
    line,
    odds: 1.5,
    active: true,
  })

  it('orders 1 / X / 2', () => {
    const sorted = sortMarketOffers([offer('a', 'H2H', 'AWAY'), offer('h', 'H2H', 'HOME'), offer('d', 'H2H', 'DRAW')])
    expect(sorted.map((o) => o.option)).toEqual(['HOME', 'DRAW', 'AWAY'])
  })

  it('orders double chance as 1X / 12 / X2', () => {
    const sorted = sortMarketOffers([
      offer('1', 'DOUBLE_CHANCE', 'AWAY_OR_DRAW'),
      offer('2', 'DOUBLE_CHANCE', 'HOME_OR_AWAY'),
      offer('3', 'DOUBLE_CHANCE', 'HOME_OR_DRAW'),
    ])
    expect(sorted.map((o) => o.option)).toEqual(['HOME_OR_DRAW', 'HOME_OR_AWAY', 'AWAY_OR_DRAW'])
  })

  it('orders over/under by line ascending, then OVER before UNDER', () => {
    const sorted = sortMarketOffers([
      offer('u35', 'OVER_UNDER', 'UNDER', 3.5),
      offer('o15', 'OVER_UNDER', 'OVER', 1.5),
      offer('u15', 'OVER_UNDER', 'UNDER', 1.5),
      offer('o35', 'OVER_UNDER', 'OVER', 3.5),
    ])
    expect(sorted.map((o) => o.id)).toEqual(['o15', 'u15', 'o35', 'u35'])
  })

  it('orders YES before NO and does not mutate the input', () => {
    const input = [offer('n', 'BTTS', 'NO'), offer('y', 'BTTS', 'YES')]
    const sorted = sortMarketOffers(input)
    expect(sorted.map((o) => o.id)).toEqual(['y', 'n'])
    expect(input.map((o) => o.id)).toEqual(['n', 'y'])
  })
})

describe('labels', () => {
  it('translates known enum values to Romanian', () => {
    expect(translateEnumLabel('SCHEDULED')).toBe('Programat')
    expect(translateEnumLabel('WON')).toBe('Castigat')
    expect(translateEnumLabel('WITHDRAWAL')).toBe('Retragere')
    expect(translateEnumLabel('DOUBLE_CHANCE')).toBe('Sansa dubla')
    expect(translateEnumLabel('HOME_OR_DRAW')).toBe('1X')
  })

  it('covers every enum value used by the frontend types', () => {
    const values = [
      'SCHEDULED', 'LIVE', 'FINISHED', 'CANCELLED', 'POSTPONED', 'SUSPENDED', 'UNKNOWN',
      'PENDING', 'WON', 'LOST', 'ACTIVE', 'DECLINED', 'DEACTIVATED', 'USER', 'ADMIN',
      'H2H', 'OVER_UNDER', 'BTTS', 'DOUBLE_CHANCE',
      'HOME', 'DRAW', 'AWAY', 'OVER', 'UNDER', 'YES', 'NO', 'HOME_OR_DRAW', 'AWAY_OR_DRAW', 'HOME_OR_AWAY',
      'DEPOSIT', 'WITHDRAWAL', 'BET', 'PAYOUT', 'REFUND', 'COMPLETED', 'REJECTED',
    ]
    for (const value of values) {
      expect(enumLabelMap[value], value).toBeTruthy()
    }
  })

  it('returns the raw value for unknown keys and empty string for empty input', () => {
    expect(translateEnumLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(translateEnumLabel('')).toBe('')
    expect(translateEnumLabel(null)).toBe('')
    expect(translateEnumLabel(undefined)).toBe('')
  })

  it('translateActiveLabel', () => {
    expect(translateActiveLabel(true)).toBe('Activ')
    expect(translateActiveLabel(false)).toBe('Inactiv')
  })
})

describe('getTeamTag', () => {
  it('prefers the trimmed, upper-cased TLA', () => {
    expect(getTeamTag('Real Madrid', 'rma')).toBe('RMA')
    expect(getTeamTag('Real Madrid', '  FCB ')).toBe('FCB')
  })

  it('builds initials from up to three words when no TLA is available', () => {
    expect(getTeamTag('Real Madrid')).toBe('RM')
    expect(getTeamTag('Paris Saint Germain Football Club', null)).toBe('PSG')
    expect(getTeamTag('  steaua   bucuresti ', '   ')).toBe('SB')
    expect(getTeamTag('Arsenal', '')).toBe('A')
  })
})

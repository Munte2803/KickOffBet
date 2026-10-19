import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addLocalDays,
  buildLocalDate,
  formatDateLabel,
  formatDateShort,
  formatDateTime,
  formatDayMonth,
  formatForInput,
  formatTime,
  getDayLabel,
  getLocalDateParam,
  getStartOfLocalDay,
  getUtcApiRange,
  getUtcDateParamsForLocalDay,
  groupByLocalDay,
  inputToUtcApiDate,
  isToday,
  parseUtcDate,
  toApiDate,
  toDateParam,
  toLocalInputValue,
  toUtcDateParam,
} from '@/utils/date.utils'

// Tests run with TZ=Europe/Bucharest (see vitest.config.ts): UTC+2 in winter, UTC+3 in summer.

afterEach(() => {
  vi.useRealTimers()
})

describe('parseUtcDate', () => {
  it('treats timestamps without an offset as UTC (backend sends UTC LocalDateTime)', () => {
    expect(parseUtcDate('2026-07-01T09:30:00').toISOString()).toBe('2026-07-01T09:30:00.000Z')
  })

  it('keeps an explicit Z suffix', () => {
    expect(parseUtcDate('2026-07-01T09:30:00Z').toISOString()).toBe('2026-07-01T09:30:00.000Z')
  })

  it('honours an explicit numeric offset', () => {
    expect(parseUtcDate('2026-07-01T12:30:00+03:00').toISOString()).toBe('2026-07-01T09:30:00.000Z')
    expect(parseUtcDate('2026-07-01T07:30:00-02:00').toISOString()).toBe('2026-07-01T09:30:00.000Z')
  })

  it('returns an invalid date for an empty string', () => {
    expect(Number.isNaN(parseUtcDate('').getTime())).toBe(true)
  })
})

describe('local date helpers', () => {
  it('buildLocalDate creates local midnight for a YYYY-MM-DD string', () => {
    const date = buildLocalDate('2026-03-10')
    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([2026, 2, 10, 0])
  })

  it('getStartOfLocalDay strips the time component', () => {
    const date = getStartOfLocalDay(new Date(2026, 4, 17, 18, 45, 12))
    expect(date).toEqual(new Date(2026, 4, 17, 0, 0, 0))
  })

  it('addLocalDays adds calendar days without mutating the input, also across DST', () => {
    const original = new Date(2026, 2, 28, 0, 0) // day before DST starts in Romania
    const next = addLocalDays(original, 1)
    expect(next.getDate()).toBe(29)
    expect(next.getHours()).toBe(0)
    expect(original.getDate()).toBe(28)
    expect(addLocalDays(new Date(2026, 0, 1), -1)).toEqual(new Date(2025, 11, 31))
  })

  it('toLocalInputValue formats a date for datetime-local inputs', () => {
    expect(toLocalInputValue(new Date(2026, 0, 5, 7, 8))).toBe('2026-01-05T07:08')
  })

  it('toDateParam uses local calendar fields, toUtcDateParam uses UTC fields', () => {
    const lateEvening = new Date(2026, 6, 1, 1, 0) // 2026-06-30T22:00Z
    expect(toDateParam(lateEvening)).toBe('2026-07-01')
    expect(toUtcDateParam(lateEvening)).toBe('2026-06-30')
  })
})

describe('API date conversion', () => {
  it('toApiDate serialises to a UTC LocalDateTime string without suffix', () => {
    expect(toApiDate(new Date(Date.UTC(2026, 0, 5, 7, 8, 9)))).toBe('2026-01-05T07:08:09')
  })

  it('toApiDate accepts strings and returns empty string for invalid input', () => {
    expect(toApiDate('2026-01-05T07:08:09Z')).toBe('2026-01-05T07:08:09')
    expect(toApiDate('not a date')).toBe('')
  })

  it('getUtcApiRange converts both local bounds to UTC', () => {
    const range = getUtcApiRange(new Date(2026, 0, 1, 0, 0, 0), new Date(2026, 0, 1, 23, 59, 59))
    expect(range).toEqual({ from: '2025-12-31T22:00:00', to: '2026-01-01T21:59:59' })
  })

  it('inputToUtcApiDate converts a local datetime-local value to UTC', () => {
    expect(inputToUtcApiDate('2026-07-01T12:30')).toBe('2026-07-01T09:30:00')
    expect(inputToUtcApiDate('2026-01-15T12:30')).toBe('2026-01-15T10:30:00')
  })

  it('inputToUtcApiDate returns undefined for empty or invalid values', () => {
    expect(inputToUtcApiDate('')).toBeUndefined()
    expect(inputToUtcApiDate(null)).toBeUndefined()
    expect(inputToUtcApiDate(undefined)).toBeUndefined()
    expect(inputToUtcApiDate('garbage')).toBeUndefined()
  })

  it('formatForInput converts a UTC API value to a local datetime-local value', () => {
    expect(formatForInput('2026-07-01T09:30:00')).toBe('2026-07-01T12:30')
    expect(formatForInput('')).toBe('')
    expect(formatForInput('garbage')).toBe('')
  })

  it('formatForInput and inputToUtcApiDate round-trip', () => {
    const api = '2026-11-20T18:05:00'
    expect(inputToUtcApiDate(formatForInput(api))).toBe(api)
  })
})

describe('local day mapping', () => {
  it('getLocalDateParam returns the local calendar day of a UTC timestamp', () => {
    expect(getLocalDateParam('2026-07-01T22:30:00')).toBe('2026-07-02')
    expect(getLocalDateParam('2026-07-01T10:00:00')).toBe('2026-07-01')
    expect(getLocalDateParam('')).toBe('')
    expect(getLocalDateParam('garbage')).toBe('')
  })

  it('getUtcDateParamsForLocalDay returns both UTC days a local day spans', () => {
    // Local 2026-07-02 00:00 = 2026-07-01T21:00Z; 23:59 = 2026-07-02T20:59Z
    expect(getUtcDateParamsForLocalDay('2026-07-02')).toEqual(['2026-07-01', '2026-07-02'])
    expect(getUtcDateParamsForLocalDay('')).toEqual([])
  })

  it('groupByLocalDay groups by local day, preserving first-seen order and skipping invalid dates', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 1, 12, 0))

    const items = [
      { id: 'a', startTime: '2026-07-01T10:00:00' },
      { id: 'b', startTime: '2026-07-01T22:30:00' }, // already 2 July locally
      { id: 'c', startTime: '' },
      { id: 'd', startTime: '2026-07-01T15:00:00' },
    ]

    const groups = groupByLocalDay(items, (item) => item.startTime)

    expect(groups.map((group) => group.dateStr)).toEqual(['2026-07-01', '2026-07-02'])
    expect(groups[0].items.map((item) => item.id)).toEqual(['a', 'd'])
    expect(groups[1].items.map((item) => item.id)).toEqual(['b'])
    expect(groups[0].label).toBe('Azi')
    expect(groups[1].label).toBe('Maine')
  })

  it('getDayLabel returns relative labels around today and a long date otherwise', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 1, 23, 30))

    expect(getDayLabel('2026-07-01')).toBe('Azi')
    expect(getDayLabel('2026-06-30')).toBe('Ieri')
    expect(getDayLabel('2026-07-02')).toBe('Maine')
    expect(getDayLabel('2026-07-10')).toBe('vineri, 10 iulie')
  })

  it('getDayLabel is not confused by a DST transition day', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 2, 29, 12, 0)) // DST starts on 29 March 2026
    expect(getDayLabel('2026-03-28')).toBe('Ieri')
    expect(getDayLabel('2026-03-30')).toBe('Maine')
  })

  it('isToday compares in the local timezone', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 2, 0, 30)) // 2026-07-01T21:30Z
    expect(isToday('2026-07-01T21:15:00')).toBe(true) // 00:15 local on 2 July
    expect(isToday('2026-07-01T20:00:00')).toBe(false) // 23:00 local on 1 July
    expect(isToday('')).toBe(false)
  })
})

describe('display formatters (ro-RO, local time)', () => {
  const iso = '2026-07-01T09:30:00' // 12:30 local

  it('formatDateShort', () => {
    expect(formatDateShort(iso)).toBe('01.07.2026, 12:30')
  })

  it('formatTime', () => {
    expect(formatTime(iso)).toBe('12:30')
  })

  it('formatDayMonth', () => {
    expect(formatDayMonth(iso)).toBe('01.07')
  })

  it('formatDateLabel', () => {
    expect(formatDateLabel(iso)).toBe('miercuri, 1 iulie')
  })

  it('formatDateTime renders "<weekday>, <day> <month> la <time>" with a single "la"', () => {
    expect(formatDateTime(iso)).toBe('miercuri, 1 iulie la 12:30')
  })

  it('all formatters return an empty string for empty input', () => {
    for (const formatter of [formatDateShort, formatTime, formatDayMonth, formatDateLabel, formatDateTime]) {
      expect(formatter('')).toBe('')
    }
  })
})

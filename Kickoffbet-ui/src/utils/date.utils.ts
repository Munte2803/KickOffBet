// Backend sends LocalDateTime as ISO string without timezone (e.g. "2024-03-15T14:30:00")
// JavaScript treats bare ISO strings as local time, but our backend values are UTC-origin.
const UTC_SUFFIX_REGEX = /(Z|[+-]\d{2}:\d{2})$/i

export function parseUtcDate(dateString: string): Date {
  if (!dateString) return new Date(NaN)
  return new Date(UTC_SUFFIX_REGEX.test(dateString) ? dateString : `${dateString}Z`)
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function buildLocalDate(dateStr: string): Date {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function getStartOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function addLocalDays(date: Date, amount: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}

function toUtcLocalDateTimeString(date: Date): string {
  return [
    `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`,
    `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`,
  ].join('T')
}

function toLocalInputValue(date: Date): string {
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    `${pad(date.getHours())}:${pad(date.getMinutes())}`,
  ].join('T')
}

const fullDateFormatter = new Intl.DateTimeFormat('ro-RO', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
})

const shortDateFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const timeOnlyFormatter = new Intl.DateTimeFormat('ro-RO', {
  hour: '2-digit',
  minute: '2-digit',
})

const dayMonthFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit',
  month: '2-digit',
})

const dateOnlyFormatter = new Intl.DateTimeFormat('ro-RO', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})

export const formatDateTime = (iso: string): string => {
  if (!iso) return ''
  return fullDateFormatter.format(parseUtcDate(iso)).replace(',', ' la')
}

export const formatDateShort = (iso: string): string => {
  if (!iso) return ''
  return shortDateFormatter.format(parseUtcDate(iso))
}

export const formatTime = (iso: string): string => {
  if (!iso) return ''
  return timeOnlyFormatter.format(parseUtcDate(iso))
}

export const formatDayMonth = (iso: string): string => {
  if (!iso) return ''
  return dayMonthFormatter.format(parseUtcDate(iso))
}

export const formatDateLabel = (iso: string): string => {
  if (!iso) return ''
  return dateOnlyFormatter.format(parseUtcDate(iso))
}

export const isToday = (iso: string): boolean => {
  if (!iso) return false
  const date = parseUtcDate(iso)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
  )
}

export const formatForInput = (iso: string): string => {
  if (!iso) return ''
  const date = parseUtcDate(iso)
  if (Number.isNaN(date.getTime())) return ''
  return toLocalInputValue(date)
}

export const toApiDate = (date: Date | string): string => {
  const parsed = typeof date === 'string' ? new Date(date) : date
  if (Number.isNaN(parsed.getTime())) return ''
  return toUtcLocalDateTimeString(parsed)
}

export const getUtcApiRange = (fromLocal: Date, toLocal: Date): { from: string; to: string } => ({
  from: toApiDate(fromLocal),
  to: toApiDate(toLocal),
})

export const inputToUtcApiDate = (value?: string | null): string | undefined => {
  if (!value) return undefined
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return undefined
  return toUtcLocalDateTimeString(parsed)
}

export const toDateParam = (date: Date): string => {
  const y = date.getFullYear()
  const m = pad(date.getMonth() + 1)
  const d = pad(date.getDate())
  return `${y}-${m}-${d}`
}

export const toUtcDateParam = (date: Date): string => {
  const y = date.getUTCFullYear()
  const m = pad(date.getUTCMonth() + 1)
  const d = pad(date.getUTCDate())
  return `${y}-${m}-${d}`
}

export const getLocalDateParam = (iso: string): string => {
  if (!iso) return ''
  const date = parseUtcDate(iso)
  if (Number.isNaN(date.getTime())) return ''
  return toDateParam(date)
}

export const getUtcDateParamsForLocalDay = (dateStr: string): string[] => {
  if (!dateStr) return []

  const startOfLocalDay = buildLocalDate(dateStr)
  startOfLocalDay.setHours(0, 0, 0, 0)

  const endOfLocalDay = buildLocalDate(dateStr)
  endOfLocalDay.setHours(23, 59, 59, 999)

  const firstUtcDate = toUtcDateParam(startOfLocalDay)
  const lastUtcDate = toUtcDateParam(endOfLocalDay)

  return firstUtcDate === lastUtcDate ? [firstUtcDate] : [firstUtcDate, lastUtcDate]
}

export const getDayLabel = (dateStr: string): string => {
  const target = buildLocalDate(dateStr)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const diff = Math.round((target.getTime() - today.getTime()) / 86400000)

  if (diff === 0) return 'Azi'
  if (diff === -1) return 'Ieri'
  if (diff === 1) return 'Maine'

  return target.toLocaleDateString('ro-RO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}

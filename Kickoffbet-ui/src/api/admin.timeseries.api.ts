import api from '@/api/axios'
import type { TicketStatus, TransactionType } from '@/types/enums'

export interface TimeSeriesPoint {
  date: string
  count: number
  totalAmount: number | null
}

export const getTransactionsTimeSeries = (
  start: string,
  end: string,
  type: TransactionType,
): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/transactions/timeseries', { params: { start, end, type } })
    .then((res) => res.data)

export const getTicketsTimeSeries = (
  start: string,
  end: string,
  status?: TicketStatus,
): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/tickets/timeseries', { params: { start, end, status } })
    .then((res) => res.data)

export const getUsersTimeSeries = (start: string, end: string): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/users/timeseries', { params: { start, end } })
    .then((res) => res.data)

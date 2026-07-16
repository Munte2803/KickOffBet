import api from '@/api/axios'

export interface TimeSeriesPoint {
  date: string
  count: number
  totalAmount: number | null
}

export type TransactionTimeSeriesType = 'DEPOSIT' | 'WITHDRAWAL' | 'BET' | 'PAYOUT' | 'REFUND'

export const getTransactionsTimeSeries = (
  start: string,
  end: string,
  type: TransactionTimeSeriesType,
): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/transactions/timeseries', { params: { start, end, type } })
    .then((res) => res.data)

export type TicketTimeSeriesStatus = 'PENDING' | 'WON' | 'LOST' | 'CANCELLED'

export const getTicketsTimeSeries = (
  start: string,
  end: string,
  status?: TicketTimeSeriesStatus,
): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/tickets/timeseries', { params: { start, end, status } })
    .then((res) => res.data)

export const getUsersTimeSeries = (start: string, end: string): Promise<TimeSeriesPoint[]> =>
  api
    .get('/api/admin/users/timeseries', { params: { start, end } })
    .then((res) => res.data)

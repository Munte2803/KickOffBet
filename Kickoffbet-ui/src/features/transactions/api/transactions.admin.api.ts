import api from '@/api/axios'
import type {
  Transaction,
  TransactionSearchRequest,
  TransactionReport,
  UserDepositSummary,
  UserTransactionSummary
} from '../types/transaction.types'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const searchTransactions = (
  filters: TransactionSearchRequest,
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get('/api/admin/transactions/search', { params: { ...filters, ...pageable } }).then(res => res.data)

export const getPendingTransactions = (
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get('/api/admin/transactions/pending', { params: pageable }).then(res => res.data)

export const getTransactionById = (id: string): Promise<Transaction> =>
  api.get(`/api/admin/transactions/${id}`).then(res => res.data)

export const approveTransaction = (id: string): Promise<Transaction> =>
  api.patch(`/api/admin/transactions/${id}/approve`).then(res => res.data)

export const rejectTransaction = (id: string): Promise<Transaction> =>
  api.patch(`/api/admin/transactions/${id}/reject`).then(res => res.data)

export const getTransactionReport = (
  startDate: string,
  endDate: string
): Promise<TransactionReport> =>
  api.get('/api/admin/transactions/reports', { params: { start: startDate, end: endDate } }).then(res => res.data)

export const getTopDepositors = (
  startDate: string,
  endDate: string,
  pageable?: PageRequest
): Promise<PageResponse<UserDepositSummary>> =>
  api.get('/api/admin/transactions/top-depositors', { params: { start: startDate, end: endDate, ...pageable } }).then(res => res.data)

export const getUserTransactionSummary = (
  userId: string,
  startDate?: string,
  endDate?: string
): Promise<UserTransactionSummary> =>
  api.get(`/api/admin/transactions/users/${userId}/summary`, { params: { start: startDate, end: endDate } }).then(res => res.data)

export const getUserTransactions = (
  userId: string,
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get(`/api/admin/transactions/users/${userId}`, { params: pageable }).then(res => res.data)

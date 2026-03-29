import api from '@/api/axios'
import type { Transaction } from '../types/transaction.types'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const getTransactionHistory = (
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get('/api/wallet/transactions', { params: pageable }).then(res => res.data)

export const getTransactionDetails = (id: string): Promise<Transaction> =>
  api.get(`/api/wallet/transactions/${id}`).then(res => res.data)

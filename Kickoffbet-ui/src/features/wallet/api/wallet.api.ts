import api from '@/api/axios'
import type { DepositRequest, WithdrawRequest } from '../types/wallet.types'
import type { Transaction } from '@/features/transactions/types/transaction.types'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const deposit = (request: DepositRequest): Promise<Transaction> =>
  api.post('/api/wallet/deposit', request).then(res => res.data)

export const withdraw = (request: WithdrawRequest): Promise<Transaction> =>
  api.post('/api/wallet/withdraw', request).then(res => res.data)

export const getTransactions = (
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get('/api/wallet/transactions', { params: pageable }).then(res => res.data)

export const getTransaction = (id: string): Promise<Transaction> =>
  api.get(`/api/wallet/transactions/${id}`).then(res => res.data)

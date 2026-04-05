import api from '@/api/axios'
import type { DepositRequest, WithdrawRequest } from '../types/wallet.types'
import type { Transaction, TransactionTypeFilter } from '@/types/transaction.types'
import type { PageRequest, PageResponse } from '@/types/api.types'

export const deposit = (request: DepositRequest): Promise<Transaction> =>
  api.post('/api/wallet/deposit', request).then(res => res.data)

export const withdraw = (request: WithdrawRequest): Promise<Transaction> =>
  api.post('/api/wallet/withdraw', request).then(res => res.data)

export const getTransactions = (
  transactionType?: TransactionTypeFilter,
  pageable?: PageRequest
): Promise<PageResponse<Transaction>> =>
  api.get('/api/wallet/transactions', {
    params: {
      type: transactionType && transactionType !== 'ALL' ? transactionType : undefined,
      sort: pageable?.sort ?? 'createdAt,desc',
      ...pageable,
    },
  }).then(res => res.data)

export const getTransaction = (id: string): Promise<Transaction> =>
  api.get(`/api/wallet/transactions/${id}`).then(res => res.data)


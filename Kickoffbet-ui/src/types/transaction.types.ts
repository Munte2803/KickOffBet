import type { TransactionType, TransactionStatus } from '@/types/enums'

export interface Transaction {
  id: string
  userId: string
  userEmail: string
  userFirstName: string
  userLastName: string
  amount: number
  referenceId: string | null
  referenceType: 'TICKET' | 'TRANSACTION' | null
  transactionType: TransactionType
  status: TransactionStatus
  createdAt: string
  updatedAt: string
}

export interface TransactionReport {
  startDate: string
  endDate: string
  totalDeposited: number
  totalWithdrawn: number
  totalStaked: number
  totalWon: number
  totalRefunded: number
}

export interface UserDepositSummary {
  userId: string
  firstName: string
  lastName: string
  totalDeposited: number
}

export interface UserTransactionSummary {
  userId: string
  firstName: string
  lastName: string
  totalDeposited: number
  totalWithdrawn: number
  totalStaked: number
  totalWon: number
  totalRefunded: number
}

export interface TransactionSearchRequest {
  userId?: string
  transactionType?: TransactionType
  transactionStatus?: TransactionStatus
  startDate?: string
  endDate?: string
  minAmount?: number
  maxAmount?: number
}

export type TransactionTypeFilter = TransactionType | 'ALL'

export interface TransactionReportRequest {
  startDate: string
  endDate: string
}


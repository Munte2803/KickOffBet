import type { UserRole, UserStatus } from '@/shared/types/enums'

export interface UserProfile {
  id: string
  firstName: string
  lastName: string
  email: string
  balance: number
  birthDate: string
  role: UserRole
  status: UserStatus
  emailVerified: boolean
  idCardVerified: boolean
  idCardUrl: string | null
  createdAt: string
  updatedAt: string
}
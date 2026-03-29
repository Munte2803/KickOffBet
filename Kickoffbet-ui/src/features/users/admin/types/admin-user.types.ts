import type { UserStatus } from '@/shared/types/enums'

export interface UserList {
  id: string
  firstName: string
  lastName: string
  email: string
  status: UserStatus
  createdAt: string
  updatedAt: string
}
import api from '@/api/axios'
import type { AuthResponse, RegisterRequest } from '@/features/auth/types/auth.types'
import type { UserProfile } from '@/features/profile/types/profile.types'
import type { UserList } from '../types/admin-user.types'
import type { UserStatus } from '@/shared/types/enums'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const createAdmin = (data: RegisterRequest): Promise<AuthResponse> =>
  api.post('/api/admin/users/create-admin', data).then(res => res.data)

export const getUsers = (pageable?: PageRequest): Promise<PageResponse<UserList>> =>
  api.get('/api/admin/users', { params: pageable }).then(res => res.data)

export const getUsersByStatus = (
  status: UserStatus,
  pageable?: PageRequest
): Promise<PageResponse<UserList>> =>
  api.get(`/api/admin/users/status/${status}`, { params: pageable }).then(res => res.data)

export const getPendingVerification = (
  pageable?: PageRequest
): Promise<PageResponse<UserList>> =>
  api.get('/api/admin/users/pending-verification', { params: pageable }).then(res => res.data)

export const searchUsers = (
  email: string,
  pageable?: PageRequest
): Promise<PageResponse<UserList>> =>
  api.get('/api/admin/users/search', { params: { email, ...pageable } }).then(res => res.data)

export const getUserById = (id: string): Promise<UserProfile> =>
  api.get(`/api/admin/users/${id}`).then(res => res.data)

export const getUserIdCard = (id: string): Promise<Blob> =>
  api.get(`/api/admin/users/${id}/id-card`, { responseType: 'blob' }).then(res => res.data)

export const approveUser = (id: string): Promise<void> =>
  api.patch(`/api/admin/users/${id}/approve`).then(() => {})

export const rejectUser = (id: string): Promise<void> =>
  api.patch(`/api/admin/users/${id}/reject`).then(() => {})

export const suspendUser = (id: string): Promise<void> =>
  api.patch(`/api/admin/users/${id}/suspend`).then(() => {})

export const activateUser = (id: string): Promise<void> =>
  api.patch(`/api/admin/users/${id}/activate`).then(() => {})

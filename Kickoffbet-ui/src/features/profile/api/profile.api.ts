import api from '@/api/axios'
import type { UserProfile } from '../types/profile.types'

export const getProfile = (): Promise<UserProfile> =>
  api.get('/api/users/me').then(res => res.data)

export const uploadIdCard = (file: File): Promise<void> => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/api/users/me/id-card', formData).then(() => {})
}

export const getIdCard = (): Promise<Blob> =>
  api.get('/api/users/me/id-card', { responseType: 'blob' }).then(res => res.data)

export const deactivateAccount = (): Promise<void> =>
  api.delete('/api/users/me').then(() => {})

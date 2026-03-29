import api from '@/api/axios';
import type { AuthResponse, LoginRequest, RegisterRequest, ResetPasswordRequest } from '../types/auth.types';

export const login = (data: LoginRequest): Promise<AuthResponse> =>
  api.post('/api/auth/login', data).then(res => res.data)

export const register = (data: RegisterRequest, idCard?: File): Promise<AuthResponse> => {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (idCard) {
    formData.append('idCard', idCard)
  }
  return api.post('/api/auth/register', formData).then(res => res.data)
}

export const confirmEmail = (token: string): Promise<void> =>
  api.get('/api/auth/confirm-email', { params: { token } })

export const resendConfirmationEmail = (email: string): Promise<void> =>
  api.post('/api/auth/resend-verification', { email }).then(() => {})

export const forgotPassword = (email: string): Promise<void> =>
  api.post('/api/auth/forgot-password', { email }).then(() => {})

export const resetPassword = (data: ResetPasswordRequest): Promise<void> =>
  api.post('/api/auth/reset-password', data).then(() => {})
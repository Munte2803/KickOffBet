import type { UserRole } from '@/shared/types/enums'


export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  password: string
  birthDate: string          
}

export interface EmailRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  password: string
  newPassword: string
  confirmPassword: string
}

export interface AuthResponse {
  token: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
}
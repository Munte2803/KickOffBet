import type { UserRole } from '@/types/enums'


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
  confirmPassword: string
}

export interface AuthResponse {
  token: string
  refreshToken: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
}


import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserRole } from '@/shared/types/enums'
import api from '@/api/axios'

export interface AuthUser {
  email: string
  firstName: string
  lastName: string
  role: UserRole
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(sessionStorage.getItem('accessToken'))
  const user = ref<AuthUser | null>(JSON.parse(sessionStorage.getItem('authUser') ?? 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const fullName = computed(() =>
    user.value ? `${user.value.firstName} ${user.value.lastName}` : ''
  )

  function setAuth(newToken: string, newUser: AuthUser) {
    token.value = newToken
    user.value = newUser
    sessionStorage.setItem('accessToken', newToken)
    sessionStorage.setItem('authUser', JSON.stringify(newUser))
  }

  async function logout() {
    try {
      // Notify backend of logout
      await api.post('/api/auth/logout')
    } catch (error) {
      console.warn('Logout notification failed:', error)
    }
    
    clearAuth()
  }

  function clearAuth() {
    token.value = null
    user.value = null
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('authUser')
  }

  return { 
    token, 
    user, 
    isAuthenticated, 
    isAdmin, 
    fullName, 
    setAuth, 
    logout,
    clearAuth 
  }
})

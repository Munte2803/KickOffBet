import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserRole } from '@/shared/types/enums'

export interface AuthUser {
  email: string
  firstName: string
  lastName: string
  role: UserRole
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<AuthUser | null>(JSON.parse(localStorage.getItem('authUser') ?? 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const fullName = computed(() =>
    user.value ? `${user.value.firstName} ${user.value.lastName}` : ''
  )

  function setAuth(newToken: string, newUser: AuthUser) {
    token.value = newToken
    user.value = newUser
    localStorage.setItem('token', newToken)
    localStorage.setItem('authUser', JSON.stringify(newUser))
  }

  function clearAuth() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('authUser')
  }

  return { token, user, isAuthenticated, isAdmin, fullName, setAuth, clearAuth }
})

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserRole } from '@/types/enums'
import { logout as logoutRequest } from '@/api/auth.api'
import type { UserProfile } from '@/types/profile.types'
import { getProfile } from '@/api/profile.api'
import { useTicketStore } from '@/stores/ticket.store'
import { clearUserScopedState } from '@/utils/user-state.utils'
import { ACCOUNT_QUERY_KEYS, queryClient } from '@/query/query-client'
import { queryKeys } from '@/query/query-keys'
import {
  bindAuthSessionListener,
  clearStoredAuthSession,
  getStoredAccessToken,
  getStoredAuthUser,
  getStoredRefreshToken,
  storeAuthSession,
  storeAuthTokens,
  storeAuthUser,
} from '@/utils/auth-session.utils'

export interface AuthUser {
  id?: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  balance?: number
  status?: UserProfile['status']
  emailVerified?: boolean
  idCardVerified?: boolean
  idCardUrl?: string | null
}

let authSessionListenerBound = false
let profileRefreshPromise: Promise<void> | null = null
let lastProfileRefreshAt = 0
const PROFILE_REFRESH_COOLDOWN_MS = 15000

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getStoredAccessToken())
  const refreshToken = ref<string | null>(getStoredRefreshToken())
  const user = ref<AuthUser | null>(getStoredAuthUser<AuthUser>())

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const fullName = computed(() =>
    user.value ? `${user.value.firstName} ${user.value.lastName}` : ''
  )
  const balance = computed(() => user.value?.balance ?? 0)

  function resetAccountState() {
    clearUserScopedState()
    useTicketStore().clearSelections()
  }

  function syncFromStorage() {
    const previousEmail = user.value?.email ?? null
    token.value = getStoredAccessToken()
    refreshToken.value = getStoredRefreshToken()
    user.value = getStoredAuthUser<AuthUser>()

    // Another tab signed out or switched account: cached queries belong to the previous user
    if (previousEmail && previousEmail !== (user.value?.email ?? null)) {
      resetAccountState()
    }
  }

  function setAuth(newToken: string, newRefreshToken: string, newUser: AuthUser) {
    storeAuthSession(newToken, newRefreshToken, newUser)
    syncFromStorage()
  }

  function setTokens(newToken: string, newRefreshToken: string) {
    storeAuthTokens(newToken, newRefreshToken)
    syncFromStorage()
  }

  function updateBalance(newBalance: number) {
    if (user.value) {
      user.value = { ...user.value, balance: newBalance }
      storeAuthUser(user.value)
    }
  }

  function hydrateProfile(profile: UserProfile) {
    if (!user.value) return

    user.value = {
      ...user.value,
      id: profile.id,
      balance: profile.balance,
      status: profile.status,
      emailVerified: profile.emailVerified,
      idCardVerified: profile.idCardVerified,
      idCardUrl: profile.idCardUrl,
    }

    storeAuthUser(user.value)
  }

  async function refreshProfile(force = false) {
    if (!token.value || !user.value) {
      return
    }

    if (!force && Date.now() - lastProfileRefreshAt < PROFILE_REFRESH_COOLDOWN_MS) {
      return
    }

    if (profileRefreshPromise) {
      return profileRefreshPromise
    }

    profileRefreshPromise = (async () => {
      const profile = await getProfile()
      hydrateProfile(profile)
      lastProfileRefreshAt = Date.now()
    })().finally(() => {
      profileRefreshPromise = null
    })

    return profileRefreshPromise
  }

  /**
   * After an operation that moved money (ticket placed or cancelled, deposit, withdrawal): marks the
   * account's cached tickets, wallet history and profile as stale and reloads the profile once, through
   * the query cache so a mounted profile query shares that single request. Resolves to false when the
   * profile could not be reloaded (the balance shown may then lag until the next refresh).
   */
  async function refreshAccountAfterMutation(): Promise<boolean> {
    for (const key of ACCOUNT_QUERY_KEYS) {
      // The profile is not refetched by the invalidation: fetchQuery below loads it exactly once
      void queryClient.invalidateQueries({
        queryKey: [key],
        refetchType: key === queryKeys.profile.root ? 'none' : 'active',
      })
    }

    try {
      const profile = await queryClient.fetchQuery({
        queryKey: queryKeys.profile(),
        queryFn: getProfile,
        staleTime: 0,
        retry: false,
      })
      hydrateProfile(profile)
      lastProfileRefreshAt = Date.now()
      return true
    } catch {
      return false
    }
  }

  async function logout() {
    try {
      await logoutRequest()
    } catch (error) {
      console.warn('Logout notification failed:', error)
    }
    clearAuth()
  }

  /** Signs out locally: drops the session plus every piece of state cached for this account. */
  function clearAuth() {
    clearStoredAuthSession()
    resetAccountState()
    syncFromStorage()
  }

  if (!authSessionListenerBound) {
    bindAuthSessionListener(syncFromStorage)
    authSessionListenerBound = true
  }

  return {
    token,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    fullName,
    balance,
    syncFromStorage,
    setAuth,
    setTokens,
    updateBalance,
    hydrateProfile,
    refreshProfile,
    refreshAccountAfterMutation,
    logout,
    clearAuth,
  }
})


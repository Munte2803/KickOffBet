import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { makeProfile } from '@/test/factories'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  getProfile: vi.fn(),
}))

vi.mock('@/api/axios', () => ({ default: { post: mocks.post } }))
vi.mock('@/api/profile.api', () => ({ getProfile: mocks.getProfile }))

const user = { email: 'ana@example.com', firstName: 'Ana', lastName: 'Pop', role: 'USER' as const }
const admin = { email: 'boss@example.com', firstName: 'Ion', lastName: 'Ionescu', role: 'ADMIN' as const }

/**
 * The store keeps module-level state (listener binding, profile refresh cooldown), so each test
 * gets a freshly evaluated module and a fresh Pinia instance.
 */
async function loadStore() {
  vi.resetModules()
  const pinia = await import('pinia')
  pinia.setActivePinia(pinia.createPinia())
  const authModule = await import('@/stores/auth.store')
  const sessionModule = await import('@/utils/auth-session.utils')
  return { store: authModule.useAuthStore(), session: sessionModule }
}

beforeEach(() => {
  mocks.post.mockReset()
  mocks.getProfile.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('auth store', () => {
  it('starts logged out when nothing is stored', async () => {
    const { store } = await loadStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.isAdmin).toBe(false)
    expect(store.user).toBeNull()
    expect(store.fullName).toBe('')
    expect(store.balance).toBe(0)
  })

  it('restores the session from sessionStorage on creation', async () => {
    sessionStorage.setItem('accessToken', 'access')
    sessionStorage.setItem('refreshToken', 'refresh')
    sessionStorage.setItem('authUser', JSON.stringify({ ...admin, balance: 42 }))

    const { store } = await loadStore()
    expect(store.token).toBe('access')
    expect(store.refreshToken).toBe('refresh')
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(store.fullName).toBe('Ion Ionescu')
    expect(store.balance).toBe(42)
  })

  it('setAuth persists tokens and user to sessionStorage', async () => {
    const { store } = await loadStore()
    store.setAuth('access', 'refresh', user)

    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(false)
    expect(store.fullName).toBe('Ana Pop')
    expect(sessionStorage.getItem('accessToken')).toBe('access')
    expect(sessionStorage.getItem('refreshToken')).toBe('refresh')
    expect(JSON.parse(sessionStorage.getItem('authUser')!)).toEqual(user)
  })

  it('setTokens replaces only the tokens', async () => {
    const { store } = await loadStore()
    store.setAuth('access', 'refresh', user)
    store.setTokens('access-2', 'refresh-2')
    expect(store.token).toBe('access-2')
    expect(store.refreshToken).toBe('refresh-2')
    expect(store.user).toEqual(user)
  })

  it('stays in sync when another module (e.g. the axios refresh flow) rewrites the session', async () => {
    const { store, session } = await loadStore()
    store.setAuth('access', 'refresh', user)

    session.storeAuthTokens('rotated-access', 'rotated-refresh')
    expect(store.token).toBe('rotated-access')
    expect(store.refreshToken).toBe('rotated-refresh')

    session.clearStoredAuthSession()
    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
  })

  it('updateBalance updates and persists the balance, and is a no-op when logged out', async () => {
    const { store } = await loadStore()
    store.updateBalance(100)
    expect(store.user).toBeNull()
    expect(sessionStorage.getItem('authUser')).toBeNull()

    store.setAuth('access', 'refresh', user)
    store.updateBalance(250.5)
    expect(store.balance).toBe(250.5)
    expect(JSON.parse(sessionStorage.getItem('authUser')!).balance).toBe(250.5)
  })

  it('hydrateProfile merges profile fields into the user and persists them', async () => {
    const { store } = await loadStore()
    store.setAuth('access', 'refresh', user)
    const profile = makeProfile({ id: 'u-9', balance: 77, status: 'PENDING', emailVerified: false, idCardVerified: false, idCardUrl: '/id.png' })

    store.hydrateProfile(profile)

    expect(store.user).toEqual({
      ...user,
      id: 'u-9',
      balance: 77,
      status: 'PENDING',
      emailVerified: false,
      idCardVerified: false,
      idCardUrl: '/id.png',
    })
    expect(JSON.parse(sessionStorage.getItem('authUser')!).id).toBe('u-9')
  })

  it('hydrateProfile is ignored when logged out', async () => {
    const { store } = await loadStore()
    store.hydrateProfile(makeProfile())
    expect(store.user).toBeNull()
  })

  describe('refreshProfile', () => {
    it('does nothing when logged out', async () => {
      const { store } = await loadStore()
      await store.refreshProfile()
      expect(mocks.getProfile).not.toHaveBeenCalled()
    })

    it('fetches the profile and hydrates the user', async () => {
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      mocks.getProfile.mockResolvedValue(makeProfile({ balance: 999 }))

      await store.refreshProfile()

      expect(mocks.getProfile).toHaveBeenCalledTimes(1)
      expect(store.balance).toBe(999)
    })

    it('shares one request between concurrent callers', async () => {
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      let resolve!: (value: unknown) => void
      mocks.getProfile.mockReturnValue(new Promise((r) => { resolve = r }))

      const first = store.refreshProfile()
      const second = store.refreshProfile(true)
      resolve(makeProfile({ balance: 5 }))
      await Promise.all([first, second])

      expect(mocks.getProfile).toHaveBeenCalledTimes(1)
      expect(store.balance).toBe(5)
    })

    it('honours a 15s cooldown unless forced', async () => {
      vi.useFakeTimers({ toFake: ['Date'] })
      vi.setSystemTime(new Date('2026-01-01T10:00:00Z'))
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      mocks.getProfile.mockResolvedValue(makeProfile())

      await store.refreshProfile()
      await store.refreshProfile()
      expect(mocks.getProfile).toHaveBeenCalledTimes(1)

      await store.refreshProfile(true)
      expect(mocks.getProfile).toHaveBeenCalledTimes(2)

      vi.setSystemTime(new Date('2026-01-01T10:00:16Z'))
      await store.refreshProfile()
      expect(mocks.getProfile).toHaveBeenCalledTimes(3)
    })

    it('propagates errors and allows a retry afterwards', async () => {
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      mocks.getProfile.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(makeProfile({ balance: 1 }))

      await expect(store.refreshProfile()).rejects.toThrow('boom')
      await store.refreshProfile()
      expect(store.balance).toBe(1)
    })
  })

  describe('logout', () => {
    it('notifies the backend and clears the session', async () => {
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      mocks.post.mockResolvedValue({ status: 204 })

      await store.logout()

      expect(mocks.post).toHaveBeenCalledWith('/api/auth/logout')
      expect(store.isAuthenticated).toBe(false)
      expect(store.user).toBeNull()
      expect(sessionStorage.getItem('accessToken')).toBeNull()
      expect(sessionStorage.getItem('refreshToken')).toBeNull()
      expect(sessionStorage.getItem('authUser')).toBeNull()
    })

    it('still clears the local session when the backend call fails', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
      const { store } = await loadStore()
      store.setAuth('access', 'refresh', user)
      mocks.post.mockRejectedValue(new Error('offline'))

      await store.logout()

      expect(store.isAuthenticated).toBe(false)
      expect(sessionStorage.getItem('accessToken')).toBeNull()
      expect(warn).toHaveBeenCalled()
    })
  })

  it('clearAuth clears without calling the backend', async () => {
    const { store } = await loadStore()
    store.setAuth('access', 'refresh', user)
    store.clearAuth()
    expect(store.isAuthenticated).toBe(false)
    expect(mocks.post).not.toHaveBeenCalled()
  })
})

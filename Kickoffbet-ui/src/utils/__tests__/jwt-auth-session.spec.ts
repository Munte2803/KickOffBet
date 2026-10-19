import { afterEach, describe, expect, it, vi } from 'vitest'
import { getJwtExpiryMs, isJwtExpired } from '@/utils/jwt.utils'
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
import { jwtExpiringIn, makeJwt } from '@/test/jwt'

afterEach(() => {
  vi.useRealTimers()
})

describe('jwt utils', () => {
  it('reads the exp claim in milliseconds', () => {
    expect(getJwtExpiryMs(makeJwt({ exp: 1_800_000_000 }))).toBe(1_800_000_000_000)
  })

  it('decodes base64url payloads containing - and _ characters', () => {
    // "~~~" style characters produce '+' and '/' in plain base64, i.e. '-' and '_' in base64url.
    const token = makeJwt({ exp: 1_800_000_000, name: '???>>>~~~' })
    expect(token.split('.')[1]).toMatch(/[-_]/)
    expect(getJwtExpiryMs(token)).toBe(1_800_000_000_000)
  })

  it('returns null for malformed tokens or a missing/non-numeric exp', () => {
    expect(getJwtExpiryMs('not-a-jwt')).toBeNull()
    expect(getJwtExpiryMs('a.%%%.c')).toBeNull()
    expect(getJwtExpiryMs(makeJwt({ sub: 'x' }))).toBeNull()
    expect(getJwtExpiryMs(makeJwt({ exp: '123' }))).toBeNull()
  })

  it('isJwtExpired treats missing or undecodable tokens as expired', () => {
    expect(isJwtExpired(null)).toBe(true)
    expect(isJwtExpired(undefined)).toBe(true)
    expect(isJwtExpired('')).toBe(true)
    expect(isJwtExpired('garbage')).toBe(true)
  })

  it('isJwtExpired compares exp with the current time and an optional buffer', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    const token = jwtExpiringIn(60)

    expect(isJwtExpired(token)).toBe(false)
    expect(isJwtExpired(token, 30_000)).toBe(false)
    expect(isJwtExpired(token, 60_000)).toBe(true)

    vi.setSystemTime(new Date('2026-01-01T00:01:00Z'))
    expect(isJwtExpired(token)).toBe(true)
  })
})

describe('auth session storage', () => {
  const user = { email: 'a@b.ro', firstName: 'Ana', lastName: 'Pop', role: 'USER' }

  it('stores and reads the full session in sessionStorage', () => {
    storeAuthSession('access', 'refresh', user)
    expect(getStoredAccessToken()).toBe('access')
    expect(getStoredRefreshToken()).toBe('refresh')
    expect(getStoredAuthUser()).toEqual(user)
    expect(localStorage.length).toBe(0)
  })

  it('storeAuthTokens only replaces the tokens', () => {
    storeAuthSession('access', 'refresh', user)
    storeAuthTokens('access-2', 'refresh-2')
    expect(getStoredAccessToken()).toBe('access-2')
    expect(getStoredRefreshToken()).toBe('refresh-2')
    expect(getStoredAuthUser()).toEqual(user)
  })

  it('storeAuthUser only replaces the user', () => {
    storeAuthSession('access', 'refresh', user)
    storeAuthUser({ ...user, firstName: 'Maria' })
    expect(getStoredAuthUser<typeof user>()?.firstName).toBe('Maria')
    expect(getStoredAccessToken()).toBe('access')
  })

  it('returns null for a missing or corrupt stored user', () => {
    expect(getStoredAuthUser()).toBeNull()
    sessionStorage.setItem('authUser', '{not json')
    expect(getStoredAuthUser()).toBeNull()
  })

  it('clearStoredAuthSession removes everything', () => {
    storeAuthSession('access', 'refresh', user)
    clearStoredAuthSession()
    expect(getStoredAccessToken()).toBeNull()
    expect(getStoredRefreshToken()).toBeNull()
    expect(getStoredAuthUser()).toBeNull()
  })

  it('notifies bound listeners on every change and stops after unbinding', () => {
    const listener = vi.fn()
    const unbind = bindAuthSessionListener(listener)

    storeAuthSession('a', 'r', user)
    storeAuthTokens('a2', 'r2')
    storeAuthUser(user)
    clearStoredAuthSession()
    expect(listener).toHaveBeenCalledTimes(4)

    unbind()
    storeAuthTokens('a3', 'r3')
    expect(listener).toHaveBeenCalledTimes(4)
  })
})

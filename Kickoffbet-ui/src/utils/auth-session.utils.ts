const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const AUTH_USER_KEY = 'authUser'
const AUTH_SESSION_EVENT = 'auth-session-changed'

function notifyAuthSessionChanged() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(AUTH_SESSION_EVENT))
}

export function getStoredAccessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getStoredRefreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY)
}

export function getStoredAuthUser<T>() {
  const rawValue = sessionStorage.getItem(AUTH_USER_KEY)
  if (!rawValue) return null

  try {
    return JSON.parse(rawValue) as T
  } catch {
    return null
  }
}

export function storeAuthSession(accessToken: string, refreshToken: string, user: unknown) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
  notifyAuthSessionChanged()
}

export function storeAuthTokens(accessToken: string, refreshToken: string) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  notifyAuthSessionChanged()
}

export function storeAuthUser(user: unknown) {
  sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
  notifyAuthSessionChanged()
}

export function clearStoredAuthSession() {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  sessionStorage.removeItem(AUTH_USER_KEY)
  notifyAuthSessionChanged()
}

export function bindAuthSessionListener(listener: () => void) {
  if (typeof window === 'undefined') {
    return () => undefined
  }

  window.addEventListener(AUTH_SESSION_EVENT, listener)
  return () => window.removeEventListener(AUTH_SESSION_EVENT, listener)
}

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const AUTH_USER_KEY = 'authUser'
const AUTH_SESSION_EVENT = 'auth-session-changed'

// sessionStorage access throws when storage is blocked (privacy settings, sandboxed iframes): the app
// then behaves as signed out instead of failing to start
function readItem(key: string): string | null {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function writeItem(key: string, value: string) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    // storage unavailable or full: the session lasts only until the next reload
  }
}

function removeItem(key: string) {
  try {
    sessionStorage.removeItem(key)
  } catch {
    // storage unavailable: nothing was stored
  }
}

function notifyAuthSessionChanged() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(AUTH_SESSION_EVENT))
}

export function getStoredAccessToken() {
  return readItem(ACCESS_TOKEN_KEY)
}

export function getStoredRefreshToken() {
  return readItem(REFRESH_TOKEN_KEY)
}

export function getStoredAuthUser<T>() {
  const rawValue = readItem(AUTH_USER_KEY)
  if (!rawValue) return null

  try {
    return JSON.parse(rawValue) as T
  } catch {
    return null
  }
}

export function storeAuthSession(accessToken: string, refreshToken: string, user: unknown) {
  writeItem(ACCESS_TOKEN_KEY, accessToken)
  writeItem(REFRESH_TOKEN_KEY, refreshToken)
  writeItem(AUTH_USER_KEY, JSON.stringify(user))
  notifyAuthSessionChanged()
}

export function storeAuthTokens(accessToken: string, refreshToken: string) {
  writeItem(ACCESS_TOKEN_KEY, accessToken)
  writeItem(REFRESH_TOKEN_KEY, refreshToken)
  notifyAuthSessionChanged()
}

export function storeAuthUser(user: unknown) {
  writeItem(AUTH_USER_KEY, JSON.stringify(user))
  notifyAuthSessionChanged()
}

export function clearStoredAuthSession() {
  removeItem(ACCESS_TOKEN_KEY)
  removeItem(REFRESH_TOKEN_KEY)
  removeItem(AUTH_USER_KEY)
  notifyAuthSessionChanged()
}

export function bindAuthSessionListener(listener: () => void) {
  if (typeof window === 'undefined') {
    return () => undefined
  }

  window.addEventListener(AUTH_SESSION_EVENT, listener)
  return () => window.removeEventListener(AUTH_SESSION_EVENT, listener)
}

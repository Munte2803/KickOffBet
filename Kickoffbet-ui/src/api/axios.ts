import axios from 'axios'
import {
  clearStoredAuthSession,
  getStoredAccessToken,
  getStoredRefreshToken,
  storeAuthTokens,
} from '@/utils/auth-session.utils'
import { isJwtExpired } from '@/utils/jwt.utils'
import { ApiError } from '@/api/api-error'
import { clearUserScopedState } from '@/utils/user-state.utils'
import { readErrorBody, translateErrorCode } from '@/api/error-messages'

function resolveApiBaseUrl() {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

  if (configuredBaseUrl) {
    return configuredBaseUrl
  }

  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }

  return ''
}

const baseUrl = resolveApiBaseUrl()

const api = axios.create({
  baseURL: baseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
  timeout: 30_000,
  // Arrays as repeated params (sort=a,desc&sort=id,asc), the form Spring binds; not sort[]=...
  paramsSerializer: { indexes: null },
})

const authEndpoints = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/confirm-email',
  '/api/auth/refresh-token',
]

const SESSION_EXPIRED_MESSAGE = 'Sesiunea a expirat. Autentifica-te din nou.'

/** Raised once the session has been ended (tokens cleared, login redirect started). */
export class SessionExpiredError extends Error {
  constructor() {
    super(SESSION_EXPIRED_MESSAGE)
    this.name = 'SessionExpiredError'
  }
}

/** HTTP statuses with which the refresh endpoint says the refresh token itself is no longer valid. */
const REFRESH_REJECTED_STATUSES = [400, 401, 403, 404]

class MissingAccessTokenError extends Error {}

/**
 * Only a definitive answer from the refresh endpoint ends the session. A network error, a timeout or
 * a 5xx says nothing about the token, so the session is kept and the next attempt retries.
 */
function isRefreshRejected(error: unknown) {
  if (error instanceof MissingAccessTokenError) return true
  const status = (error as { response?: { status?: number } })?.response?.status
  return status !== undefined && REFRESH_REJECTED_STATUSES.includes(status)
}

function endSession() {
  clearStoredAuthSession()
  redirectToLogin()
}

let refreshInFlight: Promise<{ token: string; refreshToken: string }> | null = null

function isAuthRequest(url: string) {
  return authEndpoints.some((endpoint) => url.includes(endpoint))
}

function redirectToLogin() {
  // The session is gone: don't leave the account's cached data or bet slip for whoever signs in next
  clearUserScopedState()
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return

  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.href =
    currentPath === '/' ? '/login' : `/login?redirect=${encodeURIComponent(currentPath)}`
}

export async function refreshSession() {
  const refreshToken = getStoredRefreshToken()

  if (!refreshToken || isJwtExpired(refreshToken)) {
    endSession()
    throw new SessionExpiredError()
  }

  if (!refreshInFlight) {
    refreshInFlight = axios
      .post(
        `${baseUrl}/api/auth/refresh-token`,
        { refreshToken },
        { withCredentials: true },
      )
      .then((response) => {
        const nextToken = response.data.token as string | undefined
        const nextRefreshToken = (response.data.refreshToken as string | undefined) ?? refreshToken

        if (!nextToken) {
          throw new MissingAccessTokenError('Refresh response did not include an access token.')
        }

        storeAuthTokens(nextToken, nextRefreshToken)
        return {
          token: nextToken,
          refreshToken: nextRefreshToken,
        }
      })
      .catch((error) => {
        if (isRefreshRejected(error)) {
          endSession()
          throw new SessionExpiredError()
        }
        // Transient failure: keep the session (and the bet slip) so a later attempt can retry
        throw error
      })
      .finally(() => {
        refreshInFlight = null
      })
  }

  return refreshInFlight
}

export async function ensureFreshSession(bufferMs = 20_000) {
  const accessToken = getStoredAccessToken()
  const refreshToken = getStoredRefreshToken()

  if (!accessToken) {
    if (refreshToken && !isJwtExpired(refreshToken)) {
      return refreshSession()
    }

    return null
  }

  if (!isJwtExpired(accessToken, bufferMs)) {
    return {
      token: accessToken,
      refreshToken: refreshToken ?? '',
    }
  }

  return refreshSession()
}

api.interceptors.request.use(async (config) => {
  const requestUrl = String(config.url ?? '')

  if (!isAuthRequest(requestUrl)) {
    await ensureFreshSession()
  }

  const token = getStoredAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  if (config.data instanceof FormData && config.headers) {
    delete config.headers['Content-Type']
  }

  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Errors raised by the request interceptor (e.g. an expired session) never reached the
    // server and already carry a user-facing message: pass them through unchanged.
    if (!error?.isAxiosError) {
      return Promise.reject(error)
    }

    const originalRequest = error.config
    const requestUrl = String(originalRequest?.url ?? '')
    const hasAccessToken = Boolean(getStoredAccessToken())
    const refreshToken = getStoredRefreshToken()
    const status = error.response?.status
    const errorBody = await readErrorBody(error.response?.data)
    const errorCode = errorBody?.errorCode
    const serverMessage = errorBody?.error
    // A 403 FORBIDDEN is a business rule (e.g. an unverified account may not bet): the session is
    // fine, so a refresh would not change the answer; only other 401/403s mean an expired token
    const isBusinessRefusal = status === 403 && errorCode === 'FORBIDDEN'
    const shouldAttemptRefresh = (status === 401 || status === 403) && !isBusinessRefusal

    if (shouldAttemptRefresh && hasAccessToken && refreshToken && !isAuthRequest(requestUrl) && !originalRequest?._retry) {
      originalRequest._retry = true

      try {
        const session = await refreshSession()
        originalRequest.headers = originalRequest.headers ?? {}
        originalRequest.headers.Authorization = `Bearer ${session.token}`

        return api(originalRequest)
      } catch (refreshError) {
        if (refreshError instanceof SessionExpiredError) {
          // Only a message: the refresh error's config carries the refresh token in its body
          console.error('Token refresh failed: the session is no longer valid')
          return Promise.reject(refreshError)
        }
        console.error('Token refresh failed: the refresh endpoint could not be reached')
        return Promise.reject(new ApiError('Eroare de conexiune'))
      }
    }

    const message = translateErrorCode(errorCode, serverMessage) ?? serverMessage ?? 'Eroare de conexiune'
    return Promise.reject(new ApiError(message, errorCode))
  }
)

export default api

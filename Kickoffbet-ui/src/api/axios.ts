import axios from 'axios'
import {
  clearStoredAuthSession,
  getStoredAccessToken,
  getStoredRefreshToken,
  storeAuthTokens,
} from '@/utils/auth-session.utils'
import { isJwtExpired } from '@/utils/jwt.utils'

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
})

const authEndpoints = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/confirm-email',
  '/api/auth/refresh-token',
]

let refreshInFlight: Promise<{ token: string; refreshToken: string }> | null = null

function isAuthRequest(url: string) {
  return authEndpoints.some((endpoint) => url.includes(endpoint))
}

function redirectToLogin() {
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return

  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
}

export async function refreshSession() {
  const refreshToken = getStoredRefreshToken()

  if (!refreshToken || isJwtExpired(refreshToken)) {
    clearStoredAuthSession()
    redirectToLogin()
      throw new Error('Sesiunea a expirat. Autentifica-te din nou.')
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
          throw new Error('Refresh response did not include an access token.')
        }

        storeAuthTokens(nextToken, nextRefreshToken)
        return {
          token: nextToken,
          refreshToken: nextRefreshToken,
        }
      })
      .catch((error) => {
        clearStoredAuthSession()
        redirectToLogin()
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
    const originalRequest = error.config
    const requestUrl = String(originalRequest?.url ?? '')
    const hasAccessToken = Boolean(getStoredAccessToken())
    const refreshToken = getStoredRefreshToken()
    const shouldAttemptRefresh = [401, 403].includes(error.response?.status)

    if (shouldAttemptRefresh && hasAccessToken && refreshToken && !isAuthRequest(requestUrl) && !originalRequest?._retry) {
      originalRequest._retry = true

      try {
        const session = await refreshSession()
        originalRequest.headers = originalRequest.headers ?? {}
        originalRequest.headers.Authorization = `Bearer ${session.token}`

        return api(originalRequest)
      } catch (refreshError) {
       
        const errorMessage = (refreshError as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Token refresh failed'
        console.error('🔴 Token refresh failed:', errorMessage, refreshError)

        clearStoredAuthSession()
        redirectToLogin()
    return Promise.reject(new Error('Sesiunea a expirat. Autentifica-te din nou.'))
      }
    }

    const message = error.response?.data?.error ?? 'Eroare de conexiune'
    return Promise.reject(new Error(message))
  }
)

export default api




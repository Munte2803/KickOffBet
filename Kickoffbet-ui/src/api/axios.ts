import axios from 'axios'
import {
  clearStoredAuthSession,
  getStoredAccessToken,
  getStoredRefreshToken,
  storeAuthTokens,
} from '@/utils/auth-session.utils'
import { isJwtExpired } from '@/utils/jwt.utils'

const errorCodeMessages: Record<string, string> = {
  CONCURRENT_MODIFICATION: 'Operatiunea a esuat din cauza unei modificari concurente. Incearca din nou.',
  TICKET_PLACEMENT_ERROR: 'Nu am putut plasa biletul. Verifica selectiile si incearca din nou.',
  ACCOUNT_LOCKED: 'Contul tau este blocat. Contacteaza suportul.',
  INVALID_CREDENTIALS: 'Email sau parola incorecta.',
  RESOURCE_NOT_FOUND: 'Resursa solicitata nu a fost gasita.',
  STORAGE_ERROR: 'Eroare la incarcarea fisierului. Incearca din nou.',
  EXTERNAL_API_ERROR: 'Serviciu extern indisponibil. Incearca mai tarziu.',
  INTERNAL_ERROR: 'Eroare interna. Incearca mai tarziu.',
}

function translateErrorCode(errorCode: string | undefined): string | undefined {
  if (!errorCode) return undefined
  return errorCodeMessages[errorCode]
}

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
  window.location.href =
    currentPath === '/' ? '/login' : `/login?redirect=${encodeURIComponent(currentPath)}`
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

    const errorCode = error.response?.data?.errorCode as string | undefined
    const serverMessage = error.response?.data?.error as string | undefined
    const message = translateErrorCode(errorCode) ?? serverMessage ?? 'Eroare de conexiune'
    return Promise.reject(new Error(message))
  }
)

export default api




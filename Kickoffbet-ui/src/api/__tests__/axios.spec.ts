import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/api-error'
import axios, { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import api, { ensureFreshSession, refreshSession } from '@/api/axios'
import { getStoredAccessToken, getStoredAuthUser, getStoredRefreshToken, storeAuthSession } from '@/utils/auth-session.utils'
import { jwtExpiringIn } from '@/test/jwt'

type Reply = { status: number; data?: unknown }
type Handler = (config: InternalAxiosRequestConfig) => Reply | Promise<Reply>

const requests: InternalAxiosRequestConfig[] = []
let handler: Handler

const adapter: AxiosAdapter = async (config) => {
  // Snapshot: the interceptor later mutates error.config.headers before retrying.
  requests.push({ ...config, headers: new AxiosHeaders(config.headers) })
  const reply = await handler(config)
  const response: AxiosResponse = {
    data: reply.data ?? {},
    status: reply.status,
    statusText: String(reply.status),
    headers: {},
    config,
  }
  if (reply.status >= 200 && reply.status < 300) return response
  throw new AxiosError(`Request failed with status code ${reply.status}`, 'ERR_BAD_RESPONSE', config, null, response)
}

const originalApiAdapter = api.defaults.adapter
const originalAxiosAdapter = axios.defaults.adapter

const isRefresh = (config: InternalAxiosRequestConfig) => String(config.url).endsWith('/api/auth/refresh-token')
const refreshCalls = () => requests.filter(isRefresh)
const apiCalls = (path: string) => requests.filter((config) => String(config.url) === path)
const authHeader = (config: InternalAxiosRequestConfig) => AxiosHeaders.from(config.headers as AxiosHeaders).get('Authorization')

const location = {
  pathname: '/tickets',
  search: '?status=PENDING',
  hash: '',
  href: 'http://localhost:3000/tickets?status=PENDING',
  origin: 'http://localhost:3000',
}

const user = { email: 'ana@example.com', firstName: 'Ana', lastName: 'Pop', role: 'USER' }

beforeEach(() => {
  requests.length = 0
  handler = () => ({ status: 200, data: { ok: true } })
  api.defaults.adapter = adapter
  axios.defaults.adapter = adapter
  location.pathname = '/tickets'
  location.search = '?status=PENDING'
  location.href = 'http://localhost:3000/tickets?status=PENDING'
  vi.stubGlobal('location', location)
  vi.spyOn(console, 'error').mockImplementation(() => undefined)
})

afterEach(() => {
  api.defaults.adapter = originalApiAdapter
  axios.defaults.adapter = originalAxiosAdapter
})

describe('request interceptor', () => {
  it('attaches the stored access token as a Bearer header', async () => {
    const access = jwtExpiringIn(3600)
    storeAuthSession(access, jwtExpiringIn(86400), user)

    await api.get('/api/users/me')

    expect(authHeader(apiCalls('/api/users/me')[0])).toBe(`Bearer ${access}`)
    expect(refreshCalls()).toHaveLength(0)
  })

  it('sends no Authorization header when logged out', async () => {
    await api.get('/api/leagues')
    expect(authHeader(requests[0])).toBeFalsy()
  })

  it('removes the JSON Content-Type for FormData so the browser can set the multipart boundary', async () => {
    const form = new FormData()
    form.append('file', new Blob(['x']), 'id.png')
    await api.post('/api/auth/register', form)
    expect(AxiosHeaders.from(requests[0].headers as AxiosHeaders).get('Content-Type')).not.toBe('application/json')
  })

  it('refreshes proactively when the access token is about to expire', async () => {
    const refresh = jwtExpiringIn(86400)
    const newAccess = jwtExpiringIn(3600)
    const newRefresh = jwtExpiringIn(86400 * 2)
    storeAuthSession(jwtExpiringIn(10), refresh, user) // inside the 20s buffer
    handler = (config) =>
      isRefresh(config) ? { status: 200, data: { token: newAccess, refreshToken: newRefresh } } : { status: 200 }

    await api.get('/api/tickets')

    expect(refreshCalls()).toHaveLength(1)
    expect(JSON.parse(refreshCalls()[0].data)).toEqual({ refreshToken: refresh })
    expect(authHeader(apiCalls('/api/tickets')[0])).toBe(`Bearer ${newAccess}`)
    expect(getStoredAccessToken()).toBe(newAccess)
    expect(getStoredRefreshToken()).toBe(newRefresh)
  })

  it('does not try to refresh before auth endpoints such as login', async () => {
    storeAuthSession(jwtExpiringIn(-60), jwtExpiringIn(86400), user)
    await api.post('/api/auth/login', { email: 'a', password: 'b' })
    expect(refreshCalls()).toHaveLength(0)
  })
})

describe('response interceptor: 401 -> refresh -> retry', () => {
  function sessionWithRevokedAccess() {
    const oldAccess = jwtExpiringIn(3600)
    const refresh = jwtExpiringIn(86400)
    storeAuthSession(oldAccess, refresh, user)
    return { oldAccess, refresh }
  }

  it.each([401, 403])('refreshes the session on %s and retries the request once with the new token', async (status) => {
    const { oldAccess } = sessionWithRevokedAccess()
    const newAccess = jwtExpiringIn(3600)
    handler = (config) => {
      if (isRefresh(config)) return { status: 200, data: { token: newAccess, refreshToken: jwtExpiringIn(86400) } }
      return authHeader(config) === `Bearer ${newAccess}` ? { status: 200, data: { id: 'me' } } : { status }
    }

    const response = await api.get('/api/users/me')

    expect(response.data).toEqual({ id: 'me' })
    const calls = apiCalls('/api/users/me')
    expect(calls).toHaveLength(2)
    expect(authHeader(calls[0])).toBe(`Bearer ${oldAccess}`)
    expect(authHeader(calls[1])).toBe(`Bearer ${newAccess}`)
    expect(refreshCalls()).toHaveLength(1)
    expect(getStoredAccessToken()).toBe(newAccess)
  })

  it('keeps the existing refresh token when the refresh response omits one', async () => {
    const { refresh } = sessionWithRevokedAccess()
    const newAccess = jwtExpiringIn(3600)
    handler = (config) => {
      if (isRefresh(config)) return { status: 200, data: { token: newAccess } }
      return authHeader(config) === `Bearer ${newAccess}` ? { status: 200 } : { status: 401 }
    }

    await api.get('/api/users/me')
    expect(getStoredRefreshToken()).toBe(refresh)
  })

  it('does not loop: a second 401 after the retry is returned as an error', async () => {
    sessionWithRevokedAccess()
    handler = (config) =>
      isRefresh(config)
        ? { status: 200, data: { token: jwtExpiringIn(3600), refreshToken: jwtExpiringIn(86400) } }
        : { status: 401, data: { error: 'Acces interzis' } }

    await expect(api.get('/api/users/me')).rejects.toThrow('Acces interzis')
    expect(refreshCalls()).toHaveLength(1)
    expect(apiCalls('/api/users/me')).toHaveLength(2)
  })

  it('shares a single refresh between concurrent failing requests', async () => {
    sessionWithRevokedAccess()
    const newAccess = jwtExpiringIn(3600)
    handler = async (config) => {
      if (isRefresh(config)) {
        await new Promise((resolve) => setTimeout(resolve, 5))
        return { status: 200, data: { token: newAccess, refreshToken: jwtExpiringIn(86400) } }
      }
      return authHeader(config) === `Bearer ${newAccess}` ? { status: 200, data: config.url } : { status: 401 }
    }

    const results = await Promise.all([api.get('/api/a'), api.get('/api/b'), api.get('/api/c')])

    expect(results.map((r) => r.data)).toEqual(['/api/a', '/api/b', '/api/c'])
    expect(refreshCalls()).toHaveLength(1)
  })

  it('logs out and redirects to login (keeping the current path) when the refresh fails', async () => {
    sessionWithRevokedAccess()
    localStorage.setItem('kickoffbet.betslip', JSON.stringify({ selections: [], stake: 10 }))
    handler = (config) => (isRefresh(config) ? { status: 401, data: { error: 'Invalid refresh token' } } : { status: 401 })

    await expect(api.get('/api/users/me')).rejects.toThrow('Sesiunea a expirat. Autentifica-te din nou.')

    expect(getStoredAccessToken()).toBeNull()
    expect(getStoredRefreshToken()).toBeNull()
    expect(getStoredAuthUser()).toBeNull()
    expect(localStorage.getItem('kickoffbet.betslip')).toBeNull()
    expect(location.href).toBe(`/login?redirect=${encodeURIComponent('/tickets?status=PENDING')}`)
  })

  it('treats a refresh response without an access token as a failure', async () => {
    sessionWithRevokedAccess()
    handler = (config) => (isRefresh(config) ? { status: 200, data: {} } : { status: 401 })

    await expect(api.get('/api/users/me')).rejects.toThrow('Sesiunea a expirat. Autentifica-te din nou.')
    expect(getStoredAccessToken()).toBeNull()
  })

  it('does not attempt a refresh when logged out', async () => {
    handler = () => ({ status: 401, data: { error: 'Unauthorized' } })
    await expect(api.get('/api/users/me')).rejects.toThrow('Unauthorized')
    expect(refreshCalls()).toHaveLength(0)
  })

  it('does not attempt a refresh for failed auth requests (wrong credentials)', async () => {
    storeAuthSession(jwtExpiringIn(3600), jwtExpiringIn(86400), user)
    handler = () => ({ status: 401, data: { errorCode: 'INVALID_CREDENTIALS', error: 'Bad credentials' } })

    await expect(api.post('/api/auth/login', {})).rejects.toThrow('Email sau parola incorecta.')
    expect(refreshCalls()).toHaveLength(0)
  })
})

describe('error mapping', () => {
  it('translates known backend error codes to Romanian messages', async () => {
    handler = () => ({ status: 409, data: { errorCode: 'CONCURRENT_MODIFICATION', error: 'Optimistic lock' } })
    await expect(api.get('/api/x')).rejects.toThrow('Operatiunea a esuat din cauza unei modificari concurente. Incearca din nou.')
  })

  it('falls back to the server error message for unknown codes', async () => {
    handler = () => ({ status: 400, data: { errorCode: 'VALIDATION_ERROR', error: 'Miza minima este 2 RON' } })
    await expect(api.get('/api/x')).rejects.toThrow('Miza minima este 2 RON')
  })

  it('uses a generic connection message when there is no response body', async () => {
    handler = () => {
      throw new AxiosError('Network Error', 'ERR_NETWORK')
    }
    await expect(api.get('/api/x')).rejects.toThrow('Eroare de conexiune')
  })

  it('rejects with an Error instance', async () => {
    handler = () => ({ status: 500, data: { errorCode: 'INTERNAL_ERROR' } })
    const error = await api.get('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(Error)
    expect((error as Error).message).toBe('Eroare interna. Incearca mai tarziu.')
  })

  it('keeps the backend error code on the rejection', async () => {
    handler = () => ({ status: 400, data: { errorCode: 'INSUFFICIENT_FUNDS', error: 'Insufficient funds!' } })
    const error = await api.get('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('INSUFFICIENT_FUNDS')
    expect((error as ApiError).message).toBe('Fonduri insuficiente. Alimenteaza portofelul pentru a plasa biletul.')
  })

  it('shows the specific ticket placement reason instead of a generic text', async () => {
    handler = () => ({ status: 409, data: { errorCode: 'TICKET_PLACEMENT_ERROR', error: 'Odds changed: 1.80 → 1.95' } })
    const error = await api.post('/api/tickets').catch((e: unknown) => e)
    expect((error as ApiError).code).toBe('TICKET_PLACEMENT_ERROR')
    expect((error as ApiError).message).toBe('Cota s-a modificat: 1.80 → 1.95. Verifica biletul si plaseaza-l din nou.')
  })

  it('reads the error code from a JSON body delivered as a Blob (file downloads)', async () => {
    handler = () => ({
      status: 404,
      data: new Blob([JSON.stringify({ errorCode: 'RESOURCE_NOT_FOUND', error: 'No id card' })], { type: 'application/json' }),
    })
    const error = await api.get('/api/users/me/id-card', { responseType: 'blob' }).catch((e: unknown) => e)
    expect((error as ApiError).code).toBe('RESOURCE_NOT_FOUND')
    expect((error as ApiError).message).toBe('Resursa solicitata nu a fost gasita.')
  })
})

describe('session helpers', () => {
  it('ensureFreshSession returns null when there is no session', async () => {
    await expect(ensureFreshSession()).resolves.toBeNull()
    expect(refreshCalls()).toHaveLength(0)
  })

  it('ensureFreshSession returns the current tokens while they are valid', async () => {
    const access = jwtExpiringIn(3600)
    const refresh = jwtExpiringIn(86400)
    storeAuthSession(access, refresh, user)
    await expect(ensureFreshSession()).resolves.toEqual({ token: access, refreshToken: refresh })
  })

  it('ensureFreshSession refreshes when only a valid refresh token is stored', async () => {
    sessionStorage.setItem('refreshToken', jwtExpiringIn(86400))
    const access = jwtExpiringIn(3600)
    handler = () => ({ status: 200, data: { token: access } })
    await expect(ensureFreshSession()).resolves.toMatchObject({ token: access })
  })

  it('refreshSession clears the session and redirects when the refresh token is expired', async () => {
    storeAuthSession(jwtExpiringIn(-10), jwtExpiringIn(-5), user)
    await expect(refreshSession()).rejects.toThrow('Sesiunea a expirat. Autentifica-te din nou.')
    expect(refreshCalls()).toHaveLength(0)
    expect(getStoredAccessToken()).toBeNull()
    expect(location.href).toBe(`/login?redirect=${encodeURIComponent('/tickets?status=PENDING')}`)
  })

  it('a request with an expired session is rejected with the session-expired message', async () => {
    storeAuthSession(jwtExpiringIn(-10), jwtExpiringIn(-5), user)
    await expect(api.get('/api/tickets')).rejects.toThrow('Sesiunea a expirat. Autentifica-te din nou.')
    expect(apiCalls('/api/tickets')).toHaveLength(0)
  })

  it('redirects to plain /login from the home page and not at all when already on /login', async () => {
    location.pathname = '/'
    location.search = ''
    storeAuthSession(jwtExpiringIn(-10), jwtExpiringIn(-5), user)
    await expect(refreshSession()).rejects.toThrow()
    expect(location.href).toBe('/login')

    location.pathname = '/login'
    location.href = 'unchanged'
    storeAuthSession(jwtExpiringIn(-10), jwtExpiringIn(-5), user)
    await expect(refreshSession()).rejects.toThrow()
    expect(location.href).toBe('unchanged')
  })
})

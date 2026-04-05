function decodeBase64Url(value: string) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  return atob(padded)
}

function getJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const [, payload] = token.split('.')
    if (!payload) return null
    return JSON.parse(decodeBase64Url(payload)) as Record<string, unknown>
  } catch {
    return null
  }
}

export function getJwtExpiryMs(token: string) {
  const payload = getJwtPayload(token)
  const expiration = payload?.exp

  if (typeof expiration !== 'number') {
    return null
  }

  return expiration * 1000
}

export function isJwtExpired(token: string | null | undefined, bufferMs = 0) {
  if (!token) {
    return true
  }

  const expiryMs = getJwtExpiryMs(token)
  if (!expiryMs) {
    return true
  }

  return Date.now() + bufferMs >= expiryMs
}

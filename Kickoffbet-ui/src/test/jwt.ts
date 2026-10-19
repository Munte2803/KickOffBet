function base64Url(value: string) {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Builds an unsigned JWT-shaped token whose payload carries the given claims. */
export function makeJwt(claims: Record<string, unknown>) {
  return `${base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))}.${base64Url(JSON.stringify(claims))}.signature`
}

let tokenSeq = 0

/** Unique token that expires `seconds` from now (negative for already expired). */
export function jwtExpiringIn(seconds: number, extraClaims: Record<string, unknown> = {}) {
  tokenSeq += 1
  return makeJwt({ sub: 'user@example.com', jti: `t${tokenSeq}`, exp: Math.floor(Date.now() / 1000) + seconds, ...extraClaims })
}

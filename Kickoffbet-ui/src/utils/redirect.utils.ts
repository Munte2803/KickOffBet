/**
 * Returns `value` when it is a path inside this app ("/tickets?status=WON"), otherwise null.
 * Rejects absolute URLs, protocol-relative ("//evil.com") and backslash ("/\\evil.com") forms, and
 * control characters (browsers drop tabs/newlines, turning "/\t/evil.com" into "//evil.com").
 */
export function safeInternalPath(value: unknown): string | null {
  if (typeof value !== 'string') return null
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(value)) return null
  if (!/^\/(?![/\\])/.test(value)) return null

  if (typeof window !== 'undefined' && window.location?.origin) {
    try {
      if (new URL(value, window.location.origin).origin !== window.location.origin) return null
    } catch {
      return null
    }
  }

  return value
}

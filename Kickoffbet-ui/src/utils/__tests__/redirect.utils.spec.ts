import { describe, expect, it } from 'vitest'
import { safeInternalPath } from '@/utils/redirect.utils'

describe('safeInternalPath', () => {
  it.each(['/', '/tickets', '/tickets?status=WON#top', '/matches/abc'])('accepts the internal path %s', (path) => {
    expect(safeInternalPath(path)).toBe(path)
  })

  it.each([
    '//evil.com',
    '/\\evil.com',
    '/\t/evil.com',
    '/\n/evil.com',
    'https://evil.com',
    'javascript:alert(1)',
    'tickets',
    '',
  ])('rejects %j', (path) => {
    expect(safeInternalPath(path)).toBeNull()
  })

  it('rejects anything that is not a string (repeated query params arrive as arrays)', () => {
    expect(safeInternalPath(['/tickets'])).toBeNull()
    expect(safeInternalPath(undefined)).toBeNull()
  })
})

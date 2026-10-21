import type { QueryClient } from '@tanstack/vue-query'
import type { MatchDetail, MatchList } from '@/types/match.types'
import { MATCH_OFFER_QUERY_KEYS } from '@/query/query-client'

type MatchData = MatchList | MatchDetail

function isMatch(value: unknown): value is MatchData {
  return (
    !!value &&
    typeof value === 'object' &&
    typeof (value as MatchData).id === 'string' &&
    ('availableOffers' in value || 'odd1' in value)
  )
}

/** Pulls the matches out of a match query's data: a single match, a page of matches or a plain list. */
export function extractMatches(data: unknown): MatchData[] {
  if (isMatch(data)) return [data]
  let items: unknown[] = []
  if (Array.isArray(data)) {
    items = data
  } else if (data && typeof data === 'object' && Array.isArray((data as { content?: unknown }).content)) {
    items = (data as { content: unknown[] }).content
  }
  return items.filter(isMatch)
}

/**
 * Whenever a match query succeeds, hands the loaded matches to `onMatches` (the bet slip refresh),
 * so a restored or long-open slip never keeps odds the server would reject.
 */
export function bindSlipOddsSync(client: QueryClient, onMatches: (matches: MatchData[]) => void) {
  const keys = new Set<string>(MATCH_OFFER_QUERY_KEYS)
  return client.getQueryCache().subscribe((event) => {
    if (event.type !== 'updated' || event.action.type !== 'success') return
    const [key] = event.query.queryKey
    if (typeof key !== 'string' || !keys.has(key)) return
    const matches = extractMatches(event.query.state.data)
    if (matches.length) onMatches(matches)
  })
}

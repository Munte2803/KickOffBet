import { QueryClient } from '@tanstack/vue-query'

/**
 * The app-wide query cache. It is a module singleton (rather than only provided through the Vue plugin)
 * so stores and composables can invalidate or clear it outside of a component's setup.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

/** Query keys (first segment) whose data belongs to the signed-in user's account. */
export const ACCOUNT_QUERY_KEYS = [
  'tickets',
  'ticket-detail',
  'wallet-transactions',
  'transaction-route-detail',
  'profile',
] as const

/** Query keys (first segment) holding matches with bettable offers, as shown next to the bet slip. */
export const MATCH_OFFER_QUERY_KEYS = [
  'matches-scheduled',
  'match-detail',
  'league-scheduled-matches',
  'team-scheduled-matches',
] as const

function invalidateKeys(keys: readonly string[]) {
  return Promise.all(keys.map((key) => queryClient.invalidateQueries({ queryKey: [key] })))
}

/** Refetches everything a ticket or wallet operation changes: tickets, wallet history and the profile balance. */
export function invalidateAccountQueries() {
  return invalidateKeys(ACCOUNT_QUERY_KEYS)
}

/** Refetches the match/offer queries so the bet slip picks up changed odds. */
export function invalidateMatchOfferQueries() {
  return invalidateKeys(MATCH_OFFER_QUERY_KEYS)
}

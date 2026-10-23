import { QueryClient } from '@tanstack/vue-query'
import { queryKeys } from '@/query/query-keys'

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
  queryKeys.tickets.root,
  queryKeys.ticketDetail.root,
  queryKeys.walletTransactions.root,
  queryKeys.transactionDetail.root,
  queryKeys.profile.root,
] as const

/** Query keys (first segment) holding matches with bettable offers, as shown next to the bet slip. */
export const MATCH_OFFER_QUERY_KEYS = [
  queryKeys.matchesScheduled.root,
  queryKeys.matchDetail.root,
  queryKeys.leagueScheduledMatches.root,
  queryKeys.teamScheduledMatches.root,
] as const

/** Everything an admin change to a match (status, score, time, odds, visibility) can alter. */
export const MATCH_QUERY_KEYS = [
  ...MATCH_OFFER_QUERY_KEYS,
  queryKeys.matchesFinished.root,
  queryKeys.leagueResultsMatches.root,
  queryKeys.teamResultsMatches.root,
  queryKeys.teamForm.root,
  queryKeys.adminMatchesDashboard.root,
  queryKeys.adminStuckMatches.root,
  queryKeys.adminMatchWorkbench.root,
] as const

/** Admin views listing users or showing a user's state (status, ID card). */
export const ADMIN_USER_QUERY_KEYS = [
  queryKeys.adminUsersList.root,
  queryKeys.adminUserSearchPicker.root,
  queryKeys.adminUserDetail.root,
] as const

/** Admin views listing transactions, plus the user detail that shows the balance. */
export const ADMIN_TRANSACTION_QUERY_KEYS = [
  queryKeys.adminTransactionPending.root,
  queryKeys.adminTransactionSearch.root,
  queryKeys.adminTransactionUserHistory.root,
  queryKeys.adminTransactionDetail.root,
  queryKeys.adminUserTransactions.root,
  queryKeys.adminUserDetail.root,
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

/** After an admin changed a match: the admin lists and every public view of matches. */
export function invalidateAdminMatchQueries() {
  return invalidateKeys(MATCH_QUERY_KEYS)
}

/** After an admin changed a user (status, ID card): the user lists and the user pickers. */
export function invalidateAdminUserQueries() {
  return invalidateKeys(ADMIN_USER_QUERY_KEYS)
}

/** After an admin approved or rejected a transaction. */
export function invalidateAdminTransactionQueries() {
  return invalidateKeys(ADMIN_TRANSACTION_QUERY_KEYS)
}

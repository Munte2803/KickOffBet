import { queryClient } from '@/query/query-client'
import { clearStoredSlip } from '@/stores/ticket.store'

/**
 * Drops everything cached for the signed-in account: the query cache (profile, tickets, wallet... are
 * not keyed by user) and the persisted bet slip. Safe to call outside Pinia/Vue (e.g. before a hard
 * redirect to the login page); the auth store additionally empties the in-memory slip.
 */
export function clearUserScopedState() {
  queryClient.clear()
  clearStoredSlip()
}

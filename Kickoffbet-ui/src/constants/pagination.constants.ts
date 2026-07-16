/**
 * Standardized page sizes per UI surface.
 *
 * Used to keep pagination consistent across the app and prevent
 * arbitrary `usePagination(7)` / `usePagination(12)` calls.
 */
export const PAGE_SIZES = {
  /** User tickets, user transactions, admin pending transactions, admin tickets-by-user. */
  COMPACT: 3,
  /** Admin tickets/transactions full search, match composer league/team pickers. */
  SMALL: 4,
  /** Admin users list + filters, admin pending verification (users + tickets), match league/team selection in admin matches dashboard. */
  NORMAL: 5,
  /** Nested league/team panels in admin entity detail/workspace pages. */
  MEDIUM: 6,
  /** Generic medium-sized lists (leagues/teams workspace). */
  LIST: 8,
  /** Teams grid on user-facing teams browse. */
  GRID: 12,
  /** User-facing matches grid grouped by day. */
  LARGE: 100,
} as const

export type PageSize = (typeof PAGE_SIZES)[keyof typeof PAGE_SIZES]

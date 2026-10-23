/**
 * Every TanStack Query key used by the app. A key is `[root, ...parts]`: the root names the data,
 * the parts (refs are fine, vue-query unwraps them) select the variant. Invalidating by `root`
 * refetches every variant, so pages and the invalidation helpers must use the same roots: hence
 * one place for them instead of strings copied across pages.
 */
function key<const R extends string>(root: R) {
  const make = <const P extends unknown[]>(...parts: P) => [root, ...parts] as const
  return Object.assign(make, { root })
}

export const queryKeys = {
  // Signed-in account
  profile: key('profile'),
  tickets: key('tickets'),
  ticketDetail: key('ticket-detail'),
  walletTransactions: key('wallet-transactions'),
  transactionDetail: key('transaction-route-detail'),

  // Public matches, leagues and teams
  matchesScheduled: key('matches-scheduled'),
  matchesFinished: key('matches-finished'),
  matchDetail: key('match-detail'),
  leaguePreviews: key('public-league-previews'),
  leagueDetail: key('league-detail'),
  leagueScheduledMatches: key('league-scheduled-matches'),
  leagueResultsHeader: key('league-results-header'),
  leagueResultsMatches: key('league-results-matches'),
  teams: key('public-teams'),
  teamDetail: key('team-detail'),
  teamForm: key('team-form'),
  teamScheduledMatches: key('team-scheduled-matches'),
  teamResultsHeader: key('team-results-header'),
  teamResultsMatches: key('team-results-matches'),

  // Admin: users
  adminUsersList: key('admin-users-list'),
  adminUserDetail: key('admin-user-detail'),
  adminUserTransactions: key('admin-user-transactions'),
  adminUserTickets: key('admin-user-tickets'),
  adminUserSearchPicker: key('admin-user-search-picker'),

  // Admin: transactions
  adminTransactionSearch: key('admin-transaction-search'),
  adminTransactionPending: key('admin-transaction-pending'),
  adminTransactionUserHistory: key('admin-transaction-user-history'),
  adminTransactionDetail: key('admin-transaction-route-detail'),

  // Admin: tickets
  adminTickets: key('admin-tickets'),
  adminTicketsUserHistory: key('admin-tickets-user-history'),
  adminTicketDetail: key('admin-ticket-route-detail'),

  // Admin: matches
  adminMatchesDashboard: key('admin-matches-dashboard'),
  adminMatchFilterLeagues: key('admin-match-filter-leagues'),
  adminMatchFilterTeams: key('admin-match-filter-teams'),
  adminStuckMatches: key('admin-stuck-matches'),
  adminMatchWorkbench: key('admin-match-workbench'),
  adminLeaguesForMatch: key('admin-leagues-for-match'),
  adminSelectedLeagueForMatch: key('admin-selected-league-for-match'),

  // Admin: leagues and teams
  adminLeaguesPaged: key('admin-leagues-paged'),
  adminLeagueCreateTeams: key('admin-league-create-teams'),
  adminLeagueDetail: key('admin-league-detail-route'),
  adminLeagueDetailTeams: key('admin-league-detail-teams'),
  adminTeamsPaged: key('admin-teams-paged'),
  adminTeamCreateLeagues: key('admin-team-create-leagues'),
  adminTeamDetail: key('admin-team-detail-route'),
  adminTeamDetailLeagues: key('admin-team-detail-leagues'),
} as const

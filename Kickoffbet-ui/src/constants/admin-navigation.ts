import type { RouteLocationRaw } from 'vue-router'

export type AdminResource = 'matches' | 'leagues' | 'teams' | 'users' | 'transactions' | 'tickets' | 'sync'

export const adminTopTabs: Array<{ resource: AdminResource; label: string; to: RouteLocationRaw }> = [
  { resource: 'matches', label: 'Meciuri', to: { name: 'admin-matches' } },
  { resource: 'leagues', label: 'Ligi', to: { name: 'admin-leagues' } },
  { resource: 'teams', label: 'Echipe', to: { name: 'admin-teams' } },
  { resource: 'users', label: 'Useri', to: { name: 'admin-users' } },
  { resource: 'transactions', label: 'Tranzactii', to: { name: 'admin-transactions' } },
  { resource: 'tickets', label: 'Bilete', to: { name: 'admin-tickets' } },
  { resource: 'sync', label: 'Sincronizare', to: { name: 'admin-sync' } },
]

export function getAdminResource(routeName?: string): AdminResource | null {
  if (!routeName || routeName === 'admin-dashboard') return null
  if (routeName.startsWith('admin-match')) return 'matches'
  if (routeName.startsWith('admin-league')) return 'leagues'
  if (routeName.startsWith('admin-team')) return 'teams'
  if (routeName.startsWith('admin-user')) return 'users'
  if (routeName.startsWith('admin-transaction')) return 'transactions'
  if (routeName.startsWith('admin-ticket')) return 'tickets'
  if (routeName.startsWith('admin-sync')) return 'sync'
  return null
}

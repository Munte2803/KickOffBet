import api from '@/api/axios'

export const triggerFullSync = (): Promise<void> =>
  api.post('/api/admin/sync/full').then(() => {})

export const syncAllMatches = (): Promise<void> =>
  api.post('/api/admin/sync/all-matches').then(() => {})

export const quickSyncMatches = (): Promise<void> =>
  api.post('/api/admin/sync/quick-matches').then(() => {})

export const recalculateAutomaticOdds = (): Promise<void> =>
  api.post('/api/admin/sync/recalculate-automatic-odds').then(() => {})

export interface SeedResult {
  usersCreated: number
  ticketsCreated: number
  transactionsCreated: number
  durationMs: number
}

export const triggerSeed = (): Promise<SeedResult> =>
  api.post('/api/admin/sync/seed').then((res) => res.data)

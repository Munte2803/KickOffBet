import api from '@/api/axios'

export const triggerFullSync = (): Promise<void> =>
  api.post('/api/admin/sync/full').then(() => {})

export const syncAllMatches = (): Promise<void> =>
  api.post('/api/admin/sync/all-matches').then(() => {})

export const quickSyncMatches = (): Promise<void> =>
  api.post('/api/admin/sync/quick-matches').then(() => {})

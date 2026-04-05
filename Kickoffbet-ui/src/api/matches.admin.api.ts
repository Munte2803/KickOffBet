import api from '@/api/axios'
import { inputToUtcApiDate } from '@/utils/date.utils'
import type {
  CreateMatchRequest,
  UpdateMatchRequest,
  UpdateMatchStatusRequest,
  UpdateMarketOfferRequest,
  MatchSearchRequest,
  MatchList,
  MatchDetail,
  MarketOffer
} from '../types/match.types'
import type { PageRequest, PageResponse } from '@/types/api.types'

export const createMatch = (request: CreateMatchRequest): Promise<MatchDetail> =>
  api.post('/api/admin/matches', {
    ...request,
    startTime: inputToUtcApiDate(request.startTime) ?? request.startTime,
  }).then(res => res.data)

export const searchMatches = (
  filters: MatchSearchRequest,
  pageable?: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get('/api/admin/matches/search', {
    params: {
      ...filters,
      startTimeFrom: inputToUtcApiDate(filters.startTimeFrom) ?? filters.startTimeFrom,
      startTimeTo: inputToUtcApiDate(filters.startTimeTo) ?? filters.startTimeTo,
      sort: pageable?.sort ?? `startTime,${filters.status === 'SCHEDULED' ? 'asc' : 'desc'}`,
      ...pageable,
    },
  }).then(res => res.data)

export const getStuckMatches = (): Promise<MatchList[]> =>
  api.get('/api/admin/matches/stuck').then(res => res.data)

export const getMatchById = (id: string): Promise<MatchDetail> =>
  api.get(`/api/admin/matches/${id}`).then(res => res.data)

export const updateMatch = (
  id: string,
  request: UpdateMatchRequest
): Promise<MatchDetail> =>
  api.put(`/api/admin/matches/${id}`, request).then(res => res.data)

export const updateMatchStatus = (
  id: string,
  request: UpdateMatchStatusRequest
): Promise<MatchDetail> =>
  api.patch(`/api/admin/matches/${id}/status`, request).then(res => res.data)

export const rescheduleMatch = (
  id: string,
  startTime: string
): Promise<MatchDetail> =>
  api.patch(`/api/admin/matches/${id}/time`, undefined, {
    params: {
      startTime: inputToUtcApiDate(startTime) ?? startTime,
    },
  }).then(res => res.data)

export const toggleMatchStatus = (id: string, active: boolean): Promise<void> =>
  api.patch(`/api/admin/matches/${id}/active`, undefined, { params: { active } }).then(() => {})

export const updateMarketOffer = (
  id: string,
  request: UpdateMarketOfferRequest
): Promise<MarketOffer> =>
  api.patch(`/api/admin/matches/${id}/offers`, request).then(res => res.data)

export const toggleOfferStatus = (
  id: string,
  offerId: string,
  active: boolean
): Promise<void> =>
  api.patch(`/api/admin/matches/${id}/offers/${offerId}`, undefined, { params: { active } }).then(() => {})


import api from "@/api/axios";

import type { MatchList, MatchDetail } from "@/types/match.types";
import type { MatchStatus } from "@/types/enums";
import type { PageRequest, PageResponse } from '@/types/api.types';

function withMatchSort(status: MatchStatus, pageRequest: PageRequest) {
  return {
    ...pageRequest,
    sort: pageRequest.sort ?? `startTime,${status === 'SCHEDULED' ? 'asc' : 'desc'}`,
  }
}

export const getMatchesByDay = (
  from: string,
  to: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/day/${encodeURIComponent(from)}/${encodeURIComponent(to)}/${status}`, {
    params: withMatchSort(status, pageRequest),
  }).then(res => res.data)

export const getMatchesByLeague = (
  leagueCode: string,
  from: string,
  to: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/league/${leagueCode}/${encodeURIComponent(from)}/${encodeURIComponent(to)}/${status}`, {
    params: withMatchSort(status, pageRequest),
  }).then(res => res.data)

export const getMatchesByTeam = (
  teamId: string,
  from: string,
  to: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/team/${teamId}/${encodeURIComponent(from)}/${encodeURIComponent(to)}/${status}`, {
    params: withMatchSort(status, pageRequest),
  }).then(res => res.data)

export const getMatchDetails = (id: string): Promise<MatchDetail> =>
  api.get(`/api/matches/${id}`).then(res => res.data)


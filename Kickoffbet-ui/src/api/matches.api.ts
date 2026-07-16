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
  status: MatchStatus,
  pageRequest: PageRequest,
  from?: string,
  to?: string,
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/day/${status}`, {
    params: { ...withMatchSort(status, pageRequest), from, to },
  }).then(res => res.data)

export const getMatchesByLeague = (
  leagueCode: string,
  status: MatchStatus,
  pageRequest: PageRequest,
  from?: string,
  to?: string,
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/league/${leagueCode}/${status}`, {
    params: { ...withMatchSort(status, pageRequest), from, to },
  }).then(res => res.data)

export const getMatchesByTeam = (
  teamId: string,
  status: MatchStatus,
  pageRequest: PageRequest,
  from?: string,
  to?: string,
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/team/${teamId}/${status}`, {
    params: { ...withMatchSort(status, pageRequest), from, to },
  }).then(res => res.data)

export const getMatchDetails = (id: string): Promise<MatchDetail> =>
  api.get(`/api/matches/${id}`).then(res => res.data)

import api from "@/api/axios";

import type { MatchList, MatchDetail } from "@/features/matches/types/match.types";
import type { MatchStatus } from "@/shared/types/enums";
import type { PageRequest, PageResponse } from '@/shared/types/api.types';

export const getMatchesByDay = (
  date: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/day/${date}/${status}`, { params: pageRequest }).then(res => res.data)

export const getMatchesByLeague = (
  leagueCode: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/league/${leagueCode}/${status}`, { params: pageRequest }).then(res => res.data)

export const getMatchesByTeam = (
  teamId: string,
  status: MatchStatus,
  pageRequest: PageRequest
): Promise<PageResponse<MatchList>> =>
  api.get(`/api/matches/team/${teamId}/${status}`, { params: pageRequest }).then(res => res.data)

export const getMatchDetails = (id: string): Promise<MatchDetail> =>
  api.get(`/api/matches/${id}`).then(res => res.data)
import { ref, computed } from 'vue'
import { getMatchesByDay, getMatchesByLeague, getMatchesByTeam } from '@/api/matches.api'
import type { MatchList } from '@/types/match.types'
import type { MatchStatus } from '@/types/enums'
import {
  addLocalDays,
  getDayLabel,
  getLocalDateParam,
  getStartOfLocalDay,
  getUtcApiRange,
  parseUtcDate,
  toDateParam,
} from '@/utils/date.utils'

export interface DayData {
  dateStr: string
  label: string
  matches: MatchList[]
  loading: boolean
}

interface UseLazyDaysOptions {
  direction: 'forward' | 'backward'
  filters: {
    status: MatchStatus
    leagueCode?: string
    teamId?: string
  }
}

export function useLazyDays(options: UseLazyDaysOptions) {
  const { direction, filters } = options

  const visibleDays = ref<DayData[]>([])
  const loading = ref(false)
  const cursor = ref(0)
  const lastBatchEmpty = ref(false)

  const allEmpty = computed(() =>
    !loading.value && visibleDays.value.every((d) => !d.loading && d.matches.length === 0)
  )

  const hasMore = ref(true)

  function buildLocalDateFromOffset(offsetDays: number): Date {
    const baseDate = getStartOfLocalDay(new Date())
    return addLocalDays(baseDate, direction === 'forward' ? offsetDays : -offsetDays)
  }

  async function fetchMatchesInRange(from: string, to: string): Promise<MatchList[]> {
    let page = 0
    const pageSize = 200
    const all: MatchList[] = []
    let totalPages = 1

    while (page < totalPages) {
      const pageRequest = { page, size: pageSize }
      let response

      if (filters.leagueCode) {
        response = await getMatchesByLeague(filters.leagueCode, from, to, filters.status, pageRequest)
      } else if (filters.teamId) {
        response = await getMatchesByTeam(filters.teamId, from, to, filters.status, pageRequest)
      } else {
        response = await getMatchesByDay(from, to, filters.status, pageRequest)
      }

      all.push(...response.content)
      totalPages = response.totalPages
      page += 1
    }

    const dedupedMatches = new Map<string, MatchList>()
    for (const match of all) {
      dedupedMatches.set(match.id, match)
    }

    return Array.from(dedupedMatches.values())
  }

  function sortMatches(matches: MatchList[]): MatchList[] {
    const multiplier = filters.status === 'SCHEDULED' ? 1 : -1

    return [...matches].sort(
      (left, right) => multiplier * (parseUtcDate(left.startTime).getTime() - parseUtcDate(right.startTime).getTime()),
    )
  }

  function groupByDay(matches: MatchList[], requestedDays: string[]): Map<string, MatchList[]> {
    const map = new Map<string, MatchList[]>()
    for (const day of requestedDays) {
      map.set(day, [])
    }

    for (const m of matches) {
      const key = getLocalDateParam(m.startTime)
      if (map.has(key)) {
        map.get(key)!.push(m)
      }
    }

    for (const day of requestedDays) {
      map.set(day, sortMatches(map.get(day) ?? []))
    }

    return map
  }

  async function loadNextDays(count = 3) {
    if (!hasMore.value || loading.value) return
    loading.value = true
    lastBatchEmpty.value = false

    const start = cursor.value
    const end = start + count
    const requestedDays = Array.from({ length: count }, (_, index) =>
      toDateParam(buildLocalDateFromOffset(start + index)),
    )
    const newDays: DayData[] = requestedDays.map((dateStr) => ({
      dateStr,
      label: getDayLabel(dateStr),
      matches: [],
      loading: true,
    }))

    visibleDays.value.push(...newDays)

    try {
      const newestRequested = buildLocalDateFromOffset(start)
      const oldestRequested = buildLocalDateFromOffset(end - 1)
      const localRange = direction === 'forward'
        ? {
            from: newestRequested,
            to: buildLocalDateFromOffset(end),
          }
        : {
            from: oldestRequested,
            to: addLocalDays(newestRequested, 1),
          }
      const { from, to } = getUtcApiRange(localRange.from, localRange.to)
      const grouped = groupByDay(await fetchMatchesInRange(from, to), requestedDays)
      const batchHasMatches = requestedDays.some((day) => (grouped.get(day)?.length ?? 0) > 0)

      for (const day of newDays) {
        day.matches = grouped.get(day.dateStr) ?? []
        day.loading = false
      }

      lastBatchEmpty.value = !batchHasMatches
      cursor.value = end
      hasMore.value = true
    } catch (error) {
      console.error('Failed to load matches for requested days:', error)
      for (const day of newDays) {
        day.loading = false
      }
      lastBatchEmpty.value = false
    } finally {
      loading.value = false
    }
  }

  async function loadInitial(initialCount?: number) {
    await loadNextDays(initialCount ?? (direction === 'forward' ? 3 : 5))
  }

  return {
    visibleDays,
    loading,
    allEmpty,
    lastBatchEmpty,
    hasMore,
    loadNextDays,
    loadInitial,
  }
}


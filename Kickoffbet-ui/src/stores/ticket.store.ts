import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { MatchList, MarketOffer } from '@/types/match.types'
import type { MarketType, BetOption } from '@/types/enums'
import { MAX_TICKET_SELECTIONS, MAX_WIN } from '@/constants/betting.constants'

type MatchSelectionContext = Pick<
  MatchList,
  'id' | 'homeTeamName' | 'awayTeamName' | 'homeTeamLogo' | 'awayTeamLogo' | 'startTime'
>

export interface TicketSelection {
  offerId: string
  matchId: string
  homeTeamName: string
  awayTeamName: string
  homeTeamLogo: string | null
  awayTeamLogo: string | null
  matchStartTime: string
  marketType: MarketType
  option: BetOption
  line: number | null
  odds: number
}

const STORAGE_KEY = 'kickoffbet.betslip'
const DEFAULT_STAKE = 10

interface StoredSlip {
  selections: TicketSelection[]
  stake: number
}

// The slip survives a page reload; selections whose match has already kicked off are dropped
function loadSlip(): StoredSlip {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { selections: [], stake: DEFAULT_STAKE }
    const parsed = JSON.parse(raw) as Partial<StoredSlip>
    const now = Date.now()
    const selections = Array.isArray(parsed.selections)
      ? parsed.selections.filter((selection) => new Date(selection.matchStartTime).getTime() > now)
      : []
    const stake = typeof parsed.stake === 'number' && Number.isFinite(parsed.stake) ? parsed.stake : DEFAULT_STAKE
    return { selections, stake }
  } catch {
    return { selections: [], stake: DEFAULT_STAKE }
  }
}

function saveSlip(slip: StoredSlip) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(slip))
  } catch {
    // storage unavailable (private mode, quota): the slip simply isn't kept across reloads
  }
}

export const useTicketStore = defineStore('ticket', () => {
  const stored = loadSlip()
  const selections = ref<TicketSelection[]>(stored.selections)
  const stake = ref<number>(stored.stake)

  watch([selections, stake], () => saveSlip({ selections: selections.value, stake: stake.value }), { deep: true })

  const totalOdds = computed(() => {
    if (!selections.value.length) return 1
    return selections.value.reduce((acc, s) => acc * s.odds, 1)
  })

  const selectionCount = computed(() => selections.value.length)
  const potentialWin = computed(() => +Math.min(stake.value * totalOdds.value, MAX_WIN).toFixed(2))

  function getSelectionsForMatch(matchId: string) {
    return selections.value.filter((selection) => selection.matchId === matchId)
  }

  function isMutuallyExclusiveResultMarket(first: MarketType, second: MarketType) {
    return (
      (first === 'H2H' && second === 'DOUBLE_CHANCE') ||
      (first === 'DOUBLE_CHANCE' && second === 'H2H')
    )
  }

  function toggleSelection(match: MatchSelectionContext, offer: MarketOffer): { ok: boolean; reason?: string } {
    if (!offer.active) {
      return { ok: false, reason: 'Cota nu mai este activa.' }
    }

    const existingOfferIndex = selections.value.findIndex((selection) => selection.offerId === offer.id)
    if (existingOfferIndex !== -1) {
      selections.value.splice(existingOfferIndex, 1)
      return { ok: true }
    }

    const replaceableSelectionIndexes = selections.value
      .map((selection, index) => ({ selection, index }))
      .filter(
        ({ selection }) =>
          selection.matchId === match.id &&
          (selection.marketType === offer.marketType ||
            isMutuallyExclusiveResultMarket(selection.marketType, offer.marketType)),
      )
      .map(({ index }) => index)

    if (selections.value.length >= MAX_TICKET_SELECTIONS && !replaceableSelectionIndexes.length) {
      return { ok: false, reason: `Poti avea cel mult ${MAX_TICKET_SELECTIONS} selectii pe un bilet.` }
    }

    for (const index of replaceableSelectionIndexes.sort((left, right) => right - left)) {
      selections.value.splice(index, 1)
    }

    selections.value.push({
      offerId: offer.id,
      matchId: match.id,
      homeTeamName: match.homeTeamName,
      awayTeamName: match.awayTeamName,
      homeTeamLogo: match.homeTeamLogo,
      awayTeamLogo: match.awayTeamLogo,
      matchStartTime: match.startTime,
      marketType: offer.marketType,
      option: offer.option,
      line: offer.line,
      odds: offer.odds,
    })

    return { ok: true }
  }

  function removeSelection(offerId: string) {
    const idx = selections.value.findIndex((s) => s.offerId === offerId)
    if (idx !== -1) selections.value.splice(idx, 1)
  }

  function clearSelections() {
    selections.value = []
  }

  function isSelected(offerId: string): boolean {
    return selections.value.some((s) => s.offerId === offerId)
  }

  return {
    selections,
    stake,
    totalOdds,
    selectionCount,
    potentialWin,
    getSelectionsForMatch,
    toggleSelection,
    removeSelection,
    clearSelections,
    isSelected,
  }
})


<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getMatchDetails } from '@/api/matches.api'
import { useTicketStore } from '@/stores/ticket.store'
import { useTicketActions } from '@/composables/useTicketActions'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import OddPill from '@/components/OddPill.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import { formatDateShort } from '@/utils/date.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { translateEnumLabel } from '@/utils/labels.utils'
import type { MarketOffer } from '@/types/match.types'
import type { BetOption, MarketType } from '@/types/enums'

const route = useRoute()
const ticketStore = useTicketStore()
const { toggleOffer } = useTicketActions()

const matchId = route.params.id as string

const matchQuery = useQuery({
  queryKey: ['match-detail', matchId],
  queryFn: () => getMatchDetails(matchId),
})

const marketOrder: Record<MarketType, number> = {
  H2H: 0,
  DOUBLE_CHANCE: 1,
  OVER_UNDER: 2,
  BTTS: 3,
}

const optionOrder: Partial<Record<MarketType, Partial<Record<BetOption, number>>>> = {
  H2H: {
    HOME: 0,
    DRAW: 1,
    AWAY: 2,
  },
  DOUBLE_CHANCE: {
    HOME_OR_DRAW: 0,
    AWAY_OR_DRAW: 1,
    HOME_OR_AWAY: 2,
  },
  BTTS: {
    YES: 0,
    NO: 1,
  },
}

const groupedOffers = computed(() => {
  const match = matchQuery.data.value
  if (!match) return []

  const groups = new Map<MarketType, MarketOffer[]>()
  for (const offer of match.availableOffers.filter((entry) => entry.active)) {
    const current = groups.get(offer.marketType) ?? []
    current.push(offer)
    groups.set(offer.marketType, current)
  }

  return Array.from(groups.entries())
    .sort(([left], [right]) => marketOrder[left] - marketOrder[right])
    .map(([marketType, offers]) => {
    if (marketType === 'OVER_UNDER') {
      const rows = new Map<number, { line: number; over: MarketOffer | null; under: MarketOffer | null }>()

      for (const offer of offers) {
        const line = offer.line ?? 0
        const currentRow = rows.get(line) ?? { line, over: null, under: null }

        if (offer.option === 'OVER') {
          currentRow.over = offer
        }

        if (offer.option === 'UNDER') {
          currentRow.under = offer
        }

        rows.set(line, currentRow)
      }

      return {
        marketType,
    label: translateEnumLabel(marketType),
        type: 'over-under' as const,
        offers: [] as MarketOffer[],
        rows: Array.from(rows.values()).sort((left, right) => left.line - right.line),
      }
    }

    return {
      marketType,
    label: translateEnumLabel(marketType),
      type: 'default' as const,
      offers: [...offers].sort((left, right) => {
        const order = optionOrder[marketType]
        return (order?.[left.option] ?? 99) - (order?.[right.option] ?? 99)
      }),
      rows: [] as { line: number; over: MarketOffer | null; under: MarketOffer | null }[],
    }
  })
})

const showOdds = computed(() => {
  const match = matchQuery.data.value
  if (!match) return false

  return match.status === 'SCHEDULED' || match.status === 'LIVE'
})
</script>

<template>
  <LoadingState v-if="matchQuery.isLoading.value" message="Se incarca meciul..." />
  <div v-else-if="matchQuery.data.value" class="space-y-6">
    <PageHeader
      scoreboard
      :back-route="{ name: 'home' }"
      :home-team-name="matchQuery.data.value.homeTeamName"
      :home-team-logo="matchQuery.data.value.homeTeamLogo"
      :away-team-name="matchQuery.data.value.awayTeamName"
      :away-team-logo="matchQuery.data.value.awayTeamLogo"
    />

    <Panel>
      <div class="flex flex-wrap items-center gap-2 text-[13px] text-gray-400 sm:gap-3 sm:text-sm">
        <span>{{ matchQuery.data.value.leagueName }}</span>
        <StatusBadge :status="matchQuery.data.value.status" />
        <span>{{ formatDateShort(matchQuery.data.value.startTime) }}</span>
        <span v-if="matchQuery.data.value.ftHome !== null && matchQuery.data.value.ftAway !== null" class="rounded-lg bg-white/10 px-2.5 py-1 font-mono text-[13px] text-white sm:px-3 sm:text-sm">
          {{ matchQuery.data.value.ftHome }} - {{ matchQuery.data.value.ftAway }}
        </span>
      </div>

      <div class="mt-4 flex flex-wrap gap-2 text-[13px] sm:gap-3 sm:text-sm">
        <RouterLink
          :to="{ name: 'team', params: { id: matchQuery.data.value.homeTeamId } }"
          class="rounded-lg border border-white/10 px-2.5 py-1.5 text-gray-300 transition-colors hover:bg-white/5 hover:text-white sm:px-3 sm:py-2"
        >
          {{ matchQuery.data.value.homeTeamName }}
        </RouterLink>
        <RouterLink
          :to="{ name: 'team', params: { id: matchQuery.data.value.awayTeamId } }"
          class="rounded-lg border border-white/10 px-2.5 py-1.5 text-gray-300 transition-colors hover:bg-white/5 hover:text-white sm:px-3 sm:py-2"
        >
          {{ matchQuery.data.value.awayTeamName }}
        </RouterLink>
      </div>
    </Panel>

    <Panel v-for="group in showOdds ? groupedOffers : []" :key="group.marketType">
      <h2 class="text-base font-semibold text-white sm:text-lg">{{ group.label }}</h2>

      <div v-if="group.type === 'over-under'" class="mt-4 space-y-3">
        <div
          v-for="row in group.rows"
          :key="row.line"
          class="rounded-xl border border-white/10 bg-black/30 p-3 sm:p-4"
        >
          <div class="mb-3 flex items-center justify-between gap-3">
            <h3 class="text-[13px] font-semibold uppercase tracking-[0.14em] text-gray-400 sm:text-sm sm:tracking-[0.18em]">Total goluri {{ row.line.toFixed(1) }}</h3>
          </div>

          <div class="grid gap-2 sm:grid-cols-2 sm:gap-3">
            <OddPill
              v-if="row.over"
              :label="formatBetLabel(row.over.option, row.over.marketType, row.over.line)"
              :odds="row.over.odds"
              :selected="ticketStore.isSelected(row.over.id)"
              :inactive="!row.over.active"
              @click="toggleOffer(matchQuery.data.value!, row.over)"
            />
            <div v-else class="rounded-xl border border-dashed border-white/10 bg-black/20 px-3 py-2 text-[13px] text-gray-500 sm:px-4 sm:py-3 sm:text-sm">
              Over indisponibil
            </div>

            <OddPill
              v-if="row.under"
              :label="formatBetLabel(row.under.option, row.under.marketType, row.under.line)"
              :odds="row.under.odds"
              :selected="ticketStore.isSelected(row.under.id)"
              :inactive="!row.under.active"
              @click="toggleOffer(matchQuery.data.value!, row.under)"
            />
            <div v-else class="rounded-xl border border-dashed border-white/10 bg-black/20 px-3 py-2 text-[13px] text-gray-500 sm:px-4 sm:py-3 sm:text-sm">
              Under indisponibil
            </div>
          </div>
        </div>
      </div>

      <div v-else class="mt-4 grid grid-cols-3 gap-2 sm:gap-3">
        <OddPill
          v-for="offer in group.offers"
          :key="offer.id"
          :label="formatBetLabel(offer.option, offer.marketType, offer.line)"
          :odds="offer.odds"
          :selected="ticketStore.isSelected(offer.id)"
          :inactive="!offer.active"
          @click="toggleOffer(matchQuery.data.value!, offer)"
        />
      </div>
    </Panel>
  </div>
</template>


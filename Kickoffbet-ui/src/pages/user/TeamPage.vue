<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTeamById } from '@/api/teams.api'
import { getMatchesByTeam } from '@/api/matches.api'
import { useLazyDays } from '@/composables/useLazyDays'
import MatchCard from '@/components/MatchCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'
import AppButton from '@/components/AppButton.vue'
import { addLocalDays, getStartOfLocalDay, getUtcApiRange } from '@/utils/date.utils'

const route = useRoute()
const teamId = route.params.id as string
const recentResultsRange = (() => {
  const end = addLocalDays(getStartOfLocalDay(new Date()), 1)
  const start = addLocalDays(end, -365)
  return getUtcApiRange(start, end)
})()

const teamQuery = useQuery({
  queryKey: ['team-detail', teamId],
  queryFn: () => getTeamById(teamId),
})

const recentResultsQuery = useQuery({
  queryKey: ['team-form', teamId],
  queryFn: () => getMatchesByTeam(teamId, recentResultsRange.from, recentResultsRange.to, 'FINISHED', { page: 0, size: 5 }),
})

const { visibleDays, allEmpty, lastBatchEmpty, loading, loadInitial, loadNextDays } = useLazyDays({
  direction: 'forward',
  filters: {
    status: 'SCHEDULED',
    teamId,
  },
})

onMounted(() => {
  loadInitial(14)
})

const formGuide = computed(() =>
  (recentResultsQuery.data.value?.content ?? []).map((match) => {
    const isHome = match.homeTeamId === teamId
    const teamGoals = isHome ? match.ftHome ?? 0 : match.ftAway ?? 0
    const opponentGoals = isHome ? match.ftAway ?? 0 : match.ftHome ?? 0

    if (teamGoals > opponentGoals) return { label: 'W', color: 'bg-green-500/20 text-green-300' }
    if (teamGoals < opponentGoals) return { label: 'L', color: 'bg-red-500/20 text-red-300' }
    return { label: 'D', color: 'bg-gray-500/20 text-gray-300' }
  }),
)
</script>

<template>
  <LoadingState v-if="teamQuery.isLoading.value" message="Se incarca echipa..." />
  <div v-else-if="teamQuery.data.value" class="space-y-6">
    <PageHeader
      :title="teamQuery.data.value.name"
      :logo-url="teamQuery.data.value.crestUrl"
      :back-route="{ name: 'leagues' }"
    >
      <template #actions>
        <AppLinkButton
          :to="{ name: 'team-results', params: { id: teamId } }"
          variant="outline"
        >
          Rezultate echipa
        </AppLinkButton>
      </template>
    </PageHeader>

    <Panel>
      <div class="flex flex-wrap items-center gap-4">
        <LogoFrame v-if="teamQuery.data.value.crestUrl" :src="teamQuery.data.value.crestUrl" size="xl" />
        <div>
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">{{ teamQuery.data.value.tla }}</p>
          <p class="text-sm text-gray-300">{{ teamQuery.data.value.shortName }}</p>
        </div>
      </div>

      <div class="mt-4 flex flex-wrap gap-1.5 sm:gap-2">
        <span
          v-for="league in teamQuery.data.value.leagues"
          :key="league.id"
          class="rounded-full border border-white/10 px-3 py-1 text-xs text-gray-300"
        >
          {{ league.name }}
        </span>
      </div>

      <div class="mt-4">
        <SectionHeader title="Forma" />
        <div v-if="formGuide.length" class="flex flex-wrap gap-1.5 sm:gap-2">
          <span
            v-for="(item, index) in formGuide"
            :key="index"
            class="flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold sm:h-9 sm:w-9 sm:text-sm"
            :class="item.color"
          >
            {{ item.label }}
          </span>
        </div>
        <EmptyState v-else message="Nu exista suficiente rezultate recente." />
      </div>
    </Panel>

    <template v-for="day in visibleDays" :key="day.dateStr">
      <section v-if="day.matches.length">
        <SectionHeader :title="day.label" />
        <div class="grid grid-cols-3 gap-1.5 sm:gap-3">
          <MatchCard v-for="match in day.matches" :key="match.id" :match="match" />
        </div>
      </section>
    </template>

    <div class="mt-8 flex justify-center">
      <AppButton variant="outline" :loading="loading" @click="loadNextDays()">
        Incarca mai multe meciuri
      </AppButton>
    </div>

    <div v-if="allEmpty || lastBatchEmpty" class="mt-6">
      <EmptyState message="Nu exista meciuri in zilele selectate pentru aceasta echipa." />
    </div>
  </div>
</template>


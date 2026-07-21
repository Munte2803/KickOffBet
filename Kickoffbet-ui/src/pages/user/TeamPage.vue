<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTeamById } from '@/api/teams.api'
import { getMatchesByTeam } from '@/api/matches.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { groupByLocalDay, parseUtcDate } from '@/utils/date.utils'
import MatchCard from '@/components/MatchCard.vue'
import MatchDayGrid from '@/components/MatchDayGrid.vue'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'
import AppPagination from '@/components/AppPagination.vue'

const route = useRoute()
const teamId = route.params.id as string

const teamQuery = useQuery({
  queryKey: ['team-detail', teamId],
  queryFn: () => getTeamById(teamId),
})

const recentResultsQuery = useQuery({
  queryKey: ['team-form', teamId],
  queryFn: () => getMatchesByTeam(teamId, 'FINISHED', { page: 0, size: 5 }),
})

const pagination = usePagination(PAGE_SIZES.LARGE)
const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'startTime,asc',
}))

const matchesQuery = useQuery({
  queryKey: ['team-scheduled-matches', teamId, pageRequest],
  queryFn: () => getMatchesByTeam(teamId, 'SCHEDULED', pageRequest.value),
})

const groupedByDay = computed(() => {
  const now = Date.now()
  const matches = (matchesQuery.data.value?.content ?? []).filter((m) => parseUtcDate(m.startTime).getTime() > now)
  return groupByLocalDay(matches, (m) => m.startTime)
})

const formGuide = computed(() =>
  (recentResultsQuery.data.value?.content ?? [])
    .map((match) => {
      const isHome = match.homeTeamId === teamId
      const teamGoals = isHome ? match.ftHome ?? 0 : match.ftAway ?? 0
      const opponentGoals = isHome ? match.ftAway ?? 0 : match.ftHome ?? 0

      if (teamGoals > opponentGoals) return { label: 'W', color: 'bg-green-500/20 text-green-500' }
      if (teamGoals < opponentGoals) return { label: 'L', color: 'bg-red-500/20 text-red-500' }
      return { label: 'D', color: 'bg-gray-500/20 text-muted' }
    })
    .reverse(),
)
</script>

<template>
  <LoadingState v-if="teamQuery.isLoading.value" message="Se incarca echipa..." />
  <div v-else-if="teamQuery.data.value" data-list-top class="space-y-6">
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
          <p class="text-xs uppercase tracking-[0.2em] text-subtle">{{ teamQuery.data.value.tla }}</p>
          <p class="text-sm text-muted">{{ teamQuery.data.value.shortName }}</p>
        </div>
      </div>

      <div class="mt-4 flex flex-wrap gap-1.5 sm:gap-2">
        <span
          v-for="league in teamQuery.data.value.leagues"
          :key="league.id"
          class="rounded-full border border-line px-3 py-1 text-xs text-muted"
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

    <LoadingState v-if="matchesQuery.isLoading.value" message="Se incarca meciurile..." />

    <template v-else>
      <Transition name="page-fade" mode="out-in" appear>
        <MatchDayGrid :key="matchesQuery.data.value?.number ?? 0" :days="groupedByDay">
          <template #default="{ match }">
            <MatchCard :match="match" />
          </template>
        </MatchDayGrid>
      </Transition>

      <EmptyState
        v-if="!groupedByDay.length"
        class="mt-6"
        message="Nu s-au gasit meciuri programate pentru aceasta echipa."
      />

      <AppPagination
        v-if="(matchesQuery.data.value?.totalElements ?? 0) > 0"
        :page="matchesQuery.data.value?.number ?? 0"
        :total-pages="matchesQuery.data.value?.totalPages ?? 0"
        :total-elements="matchesQuery.data.value?.totalElements"
        scroll-on-change
        @change="pagination.setPage"
      />
    </template>
  </div>
</template>

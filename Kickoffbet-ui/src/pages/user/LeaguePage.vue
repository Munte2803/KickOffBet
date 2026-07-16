<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getLeagueByCode } from '@/api/leagues.api'
import { getMatchesByLeague } from '@/api/matches.api'
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
const leagueCode = route.params.code as string

const leagueQuery = useQuery({
  queryKey: ['league-detail', leagueCode],
  queryFn: () => getLeagueByCode(leagueCode),
})

const pagination = usePagination(PAGE_SIZES.LARGE)
const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'startTime,asc',
}))

const matchesQuery = useQuery({
  queryKey: ['league-scheduled-matches', leagueCode, pageRequest],
  queryFn: () => getMatchesByLeague(leagueCode, 'SCHEDULED', pageRequest.value),
})

const groupedByDay = computed(() => {
  const now = Date.now()
  const matches = (matchesQuery.data.value?.content ?? []).filter((m) => parseUtcDate(m.startTime).getTime() > now)
  return groupByLocalDay(matches, (m) => m.startTime)
})
</script>

<template>
  <LoadingState v-if="leagueQuery.isLoading.value" message="Se incarca liga..." />
  <div v-else-if="leagueQuery.data.value" data-list-top class="space-y-6">
    <PageHeader
      :title="leagueQuery.data.value.name"
      :logo-url="leagueQuery.data.value.emblemUrl"
      subtitle="Echipe si meciuri programate"
      :back-route="{ name: 'leagues' }"
    >
      <template #actions>
        <AppLinkButton
          :to="{ name: 'league-results', params: { code: leagueCode } }"
          variant="outline"
        >
          Vezi rezultate
        </AppLinkButton>
      </template>
    </PageHeader>

    <Panel>
      <SectionHeader title="Echipe" />
      <div v-if="leagueQuery.data.value.teams.length" class="flex flex-wrap gap-1.5 sm:gap-2">
        <AppLinkButton
          v-for="team in leagueQuery.data.value.teams"
          :key="team.id"
          :to="{ name: 'team', params: { id: team.id } }"
          variant="outline"
          size="sm"
        >
          <LogoFrame v-if="team.crestUrl" :src="team.crestUrl" size="xs" />
          <span>{{ team.name }}</span>
        </AppLinkButton>
      </div>
      <EmptyState v-else message="Nu exista echipe disponibile in aceasta liga." />
    </Panel>

    <LoadingState v-if="matchesQuery.isLoading.value" message="Se incarca meciurile..." />

    <template v-else>
      <Transition name="page-fade" mode="out-in" appear>
        <MatchDayGrid :key="matchesQuery.data.value?.number ?? 0" :days="groupedByDay">
          <template #default="{ match }">
            <MatchCard :match="match" :link-league="false" />
          </template>
        </MatchDayGrid>
      </Transition>

      <EmptyState
        v-if="!groupedByDay.length"
        class="mt-6"
        message="Nu s-au gasit meciuri programate pentru aceasta liga."
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

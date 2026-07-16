<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTeamById } from '@/api/teams.api'
import { getMatchesByTeam } from '@/api/matches.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { groupByLocalDay } from '@/utils/date.utils'
import ResultCard from '@/components/ResultCard.vue'
import MatchDayGrid from '@/components/MatchDayGrid.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppPagination from '@/components/AppPagination.vue'

const route = useRoute()
const teamId = route.params.id as string

const teamQuery = useQuery({
  queryKey: ['team-results-header', teamId],
  queryFn: () => getTeamById(teamId),
})

const pagination = usePagination(PAGE_SIZES.LARGE)
const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'startTime,desc',
}))

const matchesQuery = useQuery({
  queryKey: ['team-results-matches', teamId, pageRequest],
  queryFn: () => getMatchesByTeam(teamId, 'FINISHED', pageRequest.value),
})

const groupedByDay = computed(() => {
  const matches = matchesQuery.data.value?.content ?? []
  return groupByLocalDay(matches, (m) => m.startTime)
})
</script>

<template>
  <LoadingState v-if="teamQuery.isLoading.value" message="Se incarca rezultatele..." />
  <div v-else data-list-top class="space-y-6">
    <PageHeader :title="`${teamQuery.data.value?.name ?? 'Echipa'} · rezultate`" :back-route="{ name: 'team', params: { id: teamId } }" />

    <LoadingState v-if="matchesQuery.isLoading.value" message="Se incarca meciurile..." />

    <template v-else>
      <Transition name="page-fade" mode="out-in" appear>
        <MatchDayGrid :key="matchesQuery.data.value?.number ?? 0" :days="groupedByDay">
          <template #default="{ match }">
            <ResultCard :match="match" />
          </template>
        </MatchDayGrid>
      </Transition>

      <EmptyState
        v-if="!groupedByDay.length"
        class="mt-6"
        message="Nu s-au gasit rezultate pentru aceasta echipa."
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

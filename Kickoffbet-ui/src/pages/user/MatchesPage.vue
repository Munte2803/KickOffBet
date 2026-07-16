<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getMatchesByDay } from '@/api/matches.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { groupByLocalDay, parseUtcDate } from '@/utils/date.utils'
import MatchCard from '@/components/MatchCard.vue'
import MatchDayGrid from '@/components/MatchDayGrid.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import AppPagination from '@/components/AppPagination.vue'

const pagination = usePagination(PAGE_SIZES.LARGE)
const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'startTime,asc',
}))

const matchesQuery = useQuery({
  queryKey: ['matches-scheduled', pageRequest],
  queryFn: () => getMatchesByDay('SCHEDULED', pageRequest.value),
})

const groupedByDay = computed(() => {
  const now = Date.now()
  const matches = (matchesQuery.data.value?.content ?? []).filter((m) => parseUtcDate(m.startTime).getTime() > now)
  return groupByLocalDay(matches, (m) => m.startTime)
})
</script>

<template>
  <div data-list-top>
    <PageHeader title="Meciuri disponibile" />

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
        message="Nu s-au gasit meciuri programate."
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

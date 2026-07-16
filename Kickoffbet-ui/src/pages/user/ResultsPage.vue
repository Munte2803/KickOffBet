<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getMatchesByDay } from '@/api/matches.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { groupByLocalDay } from '@/utils/date.utils'
import ResultCard from '@/components/ResultCard.vue'
import MatchDayGrid from '@/components/MatchDayGrid.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import AppPagination from '@/components/AppPagination.vue'

const pagination = usePagination(PAGE_SIZES.LARGE)
const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'startTime,desc',
}))

const resultsQuery = useQuery({
  queryKey: ['matches-finished', pageRequest],
  queryFn: () => getMatchesByDay('FINISHED', pageRequest.value),
})

const groupedByDay = computed(() => {
  const matches = resultsQuery.data.value?.content ?? []
  return groupByLocalDay(matches, (m) => m.startTime)
})
</script>

<template>
  <div data-list-top>
    <PageHeader title="Rezultate" />

    <LoadingState v-if="resultsQuery.isLoading.value" message="Se incarca rezultatele..." />

    <template v-else>
      <Transition name="page-fade" mode="out-in" appear>
        <MatchDayGrid :key="resultsQuery.data.value?.number ?? 0" :days="groupedByDay">
          <template #default="{ match }">
            <ResultCard :match="match" />
          </template>
        </MatchDayGrid>
      </Transition>

      <EmptyState
        v-if="!groupedByDay.length"
        class="mt-6"
        message="Nu s-au gasit rezultate."
      />

      <AppPagination
        v-if="(resultsQuery.data.value?.totalElements ?? 0) > 0"
        :page="resultsQuery.data.value?.number ?? 0"
        :total-pages="resultsQuery.data.value?.totalPages ?? 0"
        :total-elements="resultsQuery.data.value?.totalElements"
        scroll-on-change
        @change="pagination.setPage"
      />
    </template>
  </div>
</template>

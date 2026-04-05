<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getLeagueByCode } from '@/api/leagues.api'
import { useLazyDays } from '@/composables/useLazyDays'
import ResultCard from '@/components/ResultCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppButton from '@/components/AppButton.vue'

const route = useRoute()
const leagueCode = route.params.code as string

const leagueQuery = useQuery({
  queryKey: ['league-results-header', leagueCode],
  queryFn: () => getLeagueByCode(leagueCode),
})

const { visibleDays, allEmpty, lastBatchEmpty, loading, loadInitial, loadNextDays } = useLazyDays({
  direction: 'backward',
  filters: {
    status: 'FINISHED',
    leagueCode,
  },
})

onMounted(() => {
  loadInitial(15)
})
</script>

<template>
  <LoadingState v-if="leagueQuery.isLoading.value" message="Se incarca rezultatele..." />
  <div v-else class="space-y-6">
    <PageHeader :title="`${leagueQuery.data.value?.name ?? 'Liga'} · rezultate`" :back-route="{ name: 'league', params: { code: leagueCode } }" />

    <template v-for="day in visibleDays" :key="day.dateStr">
      <section v-if="day.matches.length">
        <SectionHeader :title="day.label" />
        <div class="grid grid-cols-3 gap-1.5 sm:gap-3">
          <ResultCard v-for="match in day.matches" :key="match.id" :match="match" />
        </div>
      </section>
    </template>

    <div class="mt-8 flex justify-center">
      <AppButton variant="outline" :loading="loading" @click="loadNextDays()">
        Incarca rezultate mai vechi
      </AppButton>
    </div>

    <div v-if="allEmpty || lastBatchEmpty" class="mt-10">
      <EmptyState message="Nu exista rezultate in zilele selectate pentru aceasta liga." />
    </div>
  </div>
</template>


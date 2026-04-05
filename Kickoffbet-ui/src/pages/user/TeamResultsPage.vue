<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTeamById } from '@/api/teams.api'
import { useLazyDays } from '@/composables/useLazyDays'
import ResultCard from '@/components/ResultCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppButton from '@/components/AppButton.vue'

const route = useRoute()
const teamId = route.params.id as string

const teamQuery = useQuery({
  queryKey: ['team-results-header', teamId],
  queryFn: () => getTeamById(teamId),
})

const { visibleDays, allEmpty, lastBatchEmpty, loading, loadInitial, loadNextDays } = useLazyDays({
  direction: 'backward',
  filters: {
    status: 'FINISHED',
    teamId,
  },
})

onMounted(() => {
  loadInitial(15)
})
</script>

<template>
  <LoadingState v-if="teamQuery.isLoading.value" message="Se incarca rezultatele..." />
  <div v-else class="space-y-6">
    <PageHeader :title="`${teamQuery.data.value?.name ?? 'Echipa'} · rezultate`" :back-route="{ name: 'team', params: { id: teamId } }" />

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
      <EmptyState message="Nu exista rezultate in zilele selectate pentru aceasta echipa." />
    </div>
  </div>
</template>


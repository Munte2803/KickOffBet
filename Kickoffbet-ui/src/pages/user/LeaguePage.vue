<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getLeagueByCode } from '@/api/leagues.api'
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

const route = useRoute()
const leagueCode = route.params.code as string

const leagueQuery = useQuery({
  queryKey: ['league-detail', leagueCode],
  queryFn: () => getLeagueByCode(leagueCode),
})

const { visibleDays, allEmpty, lastBatchEmpty, loading, loadInitial, loadNextDays } = useLazyDays({
  direction: 'forward',
  filters: {
    status: 'SCHEDULED',
    leagueCode,
  },
})

onMounted(() => {
  loadInitial(14)
})
</script>

<template>
  <LoadingState v-if="leagueQuery.isLoading.value" message="Se incarca liga..." />
  <div v-else-if="leagueQuery.data.value" class="space-y-6">
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

    <template v-for="day in visibleDays" :key="day.dateStr">
      <section v-if="day.matches.length">
        <SectionHeader :title="day.label" />
        <div class="grid grid-cols-3 gap-1.5 sm:gap-3">
          <MatchCard v-for="match in day.matches" :key="match.id" :match="match" :link-league="false" />
        </div>
      </section>
    </template>

    <div class="mt-8 flex justify-center">
      <AppButton variant="outline" :loading="loading" @click="loadNextDays()">
        Incarca mai multe meciuri
      </AppButton>
    </div>

    <div v-if="allEmpty || lastBatchEmpty" class="mt-6">
      <EmptyState message="Nu exista meciuri in zilele selectate pentru aceasta liga." />
    </div>
  </div>
</template>


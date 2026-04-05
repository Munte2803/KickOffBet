<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { getLeaguePreviews } from '@/api/leagues.api'
import MatchCard from '@/components/MatchCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'

const leaguePreviewsQuery = useQuery({
  queryKey: ['public-league-previews'],
  queryFn: () => getLeaguePreviews('SCHEDULED', 3),
})
</script>

<template>
  <div>
    <PageHeader title="Ligi si competitii"/>
    <LoadingState v-if="leaguePreviewsQuery.isLoading.value" message="Se incarca ligile..." />
    <div v-else class="space-y-6">
      <EmptyState v-if="!(leaguePreviewsQuery.data.value ?? []).length" message="Nu exista ligi disponibile." />

      <section v-for="league in leaguePreviewsQuery.data.value ?? []" :key="league.code">
        <SectionHeader :title="league.name" :logo="league.emblemUrl">
          <template #actions>
            <AppLinkButton
              :to="{ name: 'league', params: { code: league.code } }"
              variant="outline"
            >
              Deschide
            </AppLinkButton>
          </template>
        </SectionHeader>

        <div v-if="league.previewMatches.length" class="grid grid-cols-3 gap-1.5 sm:gap-3">
          <MatchCard v-for="match in league.previewMatches" :key="match.id" :match="match" />
        </div>
        <EmptyState v-else message="Nu exista meciuri programate in preview." />
      </section>
    </div>
  </div>
</template>


<script setup lang="ts">
import { onMounted } from 'vue'
import MatchCard from '@/components/MatchCard.vue'
import AppButton from '@/components/AppButton.vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import { useLazyDays } from '@/composables/useLazyDays'

const { visibleDays, allEmpty, lastBatchEmpty, loading, loadNextDays, loadInitial } = useLazyDays({
  direction: 'forward',
  filters: {
    status: 'SCHEDULED',
  },
})

onMounted(() => {
  loadInitial()
})
</script>

<template>
  <div>
    <PageHeader title="Meciuri disponibile" />

    <div class="space-y-6">
      <template v-for="day in visibleDays" :key="day.dateStr">
        <section v-if="day.loading || day.matches.length">
          <SectionHeader :title="day.label" />
          <div v-if="day.loading" class="grid grid-cols-3 gap-1.5 sm:gap-3">
            <div v-for="index in 3" :key="index" class="h-36 animate-pulse rounded-xl border border-white/10 bg-white/5" />
          </div>
          <div v-else class="grid grid-cols-3 gap-1.5 sm:gap-3">
            <MatchCard v-for="match in day.matches" :key="match.id" :match="match" />
          </div>
        </section>
      </template>
    </div>

    <div class="mt-8 flex justify-center">
      <AppButton variant="outline" :loading="loading" @click="loadNextDays()">
        Incarca zilele urmatoare
      </AppButton>
    </div>

    <div v-if="allEmpty || lastBatchEmpty" class="mt-6">
      <EmptyState message="Nu exista meciuri in zilele selectate." />
    </div>
  </div>
</template>


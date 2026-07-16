<script setup lang="ts">
import type { DayGroup } from '@/utils/date.utils'
import type { MatchList } from '@/types/match.types'
import SectionHeader from '@/components/SectionHeader.vue'

defineProps<{
  days: DayGroup<MatchList>[]
}>()
</script>

<template>
  <div class="space-y-6">
    <section v-for="day in days" :key="day.dateStr">
      <SectionHeader :title="day.label" />
      <div class="grid grid-cols-3 gap-1.5 sm:gap-3">
        <template v-for="match in day.items" :key="match.id">
          <slot :match="match" />
        </template>
      </div>
    </section>
  </div>
</template>

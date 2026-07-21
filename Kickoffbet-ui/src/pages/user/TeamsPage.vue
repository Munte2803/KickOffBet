<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTeams } from '@/api/teams.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppPagination from '@/components/AppPagination.vue'
import LogoFrame from '@/components/LogoFrame.vue'

const pagination = usePagination(PAGE_SIZES.GRID)

const teamsQuery = useQuery({
  queryKey: ['public-teams', pagination.request],
  queryFn: () => getTeams(pagination.request.value),
})

const teams = computed(() => teamsQuery.data.value?.content ?? [])
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Echipe" />

    <LoadingState v-if="teamsQuery.isLoading.value" message="Se incarca echipele..." />

    <section v-else data-list-top class="space-y-6">
      <EmptyState v-if="!teams.length" message="Nu exista echipe disponibile." />

      <Transition v-else name="page-fade" mode="out-in" appear>
        <div :key="teamsQuery.data.value?.number ?? 0" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <RouterLink
            v-for="team in teams"
            :key="team.id"
            :to="{ name: 'team', params: { id: team.id } }"
            class="block"
          >
            <Panel class="h-full bg-surface transition-transform hover:-translate-y-0.5">
              <div class="flex items-center gap-4">
                <LogoFrame v-if="team.crestUrl" :src="team.crestUrl" size="xl" />
                <div v-else class="flex h-14 w-14 items-center justify-center rounded-full border border-line bg-surface-2 text-xs font-semibold text-fg">
                  {{ team.tla }}
                </div>

                <div class="min-w-0">
                  <h2 class="truncate text-lg font-bold text-fg">{{ team.name }}</h2>
                  <p class="mt-1 text-sm text-muted">{{ team.shortName }}</p>
                  <p class="mt-1 text-xs uppercase tracking-[0.2em] text-subtle">{{ team.tla }}</p>
                </div>
              </div>
            </Panel>
          </RouterLink>
        </div>
      </Transition>

      <AppPagination
        v-if="(teamsQuery.data.value?.totalElements ?? 0) > 0"
        :page="teamsQuery.data.value?.number ?? 0"
        :total-pages="teamsQuery.data.value?.totalPages ?? 0"
        :total-elements="teamsQuery.data.value?.totalElements"
        @change="pagination.setPage"
      />
    </section>
  </div>
</template>


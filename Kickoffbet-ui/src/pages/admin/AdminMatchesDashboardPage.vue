<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import { getLeaguePage } from '@/api/leagues.admin.api'
import { getStuckMatches, searchMatches } from '@/api/matches.admin.api'
import type { MatchSearchRequest } from '@/types/match.types'
import { getAllTeams } from '@/api/teams.admin.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import type { MatchStatus } from '@/types/enums'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import AppPagination from '@/components/AppPagination.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AdminMatchCard from '@/components/AdminMatchCard.vue'
import AdminEntityPicker from '@/components/AdminEntityPicker.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import DatePresetButtons from '@/components/DatePresetButtons.vue'
import ExportButton from '@/components/ExportButton.vue'
import type { ExportColumn } from '@/utils/export.utils'
import type { MatchList } from '@/types/match.types'

const statusOptions: Array<{ label: string; value: MatchStatus | '' }> = [
  { label: 'Toate', value: '' },
  { label: 'Programat', value: 'SCHEDULED' },
  { label: 'In direct', value: 'LIVE' },
  { label: 'Terminat', value: 'FINISHED' },
  { label: 'Amanat', value: 'POSTPONED' },
  { label: 'Suspendat', value: 'SUSPENDED' },
  { label: 'Anulat', value: 'CANCELLED' },
]

const sortOptions = [
  { label: 'Sortare implicita', value: '' },
  { label: 'Ora de start crescator', value: 'startTime,asc' },
  { label: 'Ora de start descrescator', value: 'startTime,desc' },
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
  { label: 'Status A-Z', value: 'status,asc' },
  { label: 'Status Z-A', value: 'status,desc' },
]

const manualUpdate = ref<'ALL' | 'TRUE' | 'FALSE'>('ALL')
const status = ref<MatchStatus | ''>('')
const selectedLeagueCode = ref('')
const selectedTeamId = ref('')
const startTimeFrom = ref('')
const startTimeTo = ref('')
const activeOnly = ref(false)
const sortBy = ref('')
const hasSearched = ref(false)
const showStuckMatches = ref(true)

const pagination = usePagination(PAGE_SIZES.COMPACT)
const leaguePagination = usePagination(PAGE_SIZES.NORMAL)
const teamPagination = usePagination(PAGE_SIZES.NORMAL)
const appliedFilters = ref<MatchSearchRequest>({})
const matchesPageRequest = computed(() => ({
  ...pagination.request.value,
  sort: sortBy.value || undefined,
}))

const matchesQuery = useQuery({
  queryKey: ['admin-matches-dashboard', appliedFilters, matchesPageRequest],
  queryFn: () => searchMatches(appliedFilters.value, matchesPageRequest.value),
  enabled: computed(() => hasSearched.value),
})

const leaguesQuery = useQuery({
  queryKey: ['admin-match-filter-leagues', leaguePagination.request],
  queryFn: () => getLeaguePage(leaguePagination.request.value),
})

const teamsQuery = useQuery({
  queryKey: ['admin-match-filter-teams', teamPagination.request],
  queryFn: () => getAllTeams(teamPagination.request.value),
})

const stuckMatchesQuery = useQuery({
  queryKey: ['admin-stuck-matches', showStuckMatches],
  queryFn: getStuckMatches,
  enabled: computed(() => showStuckMatches.value),
})

const currentMatches = computed(() => matchesQuery.data.value?.content ?? [])
const stuckMatches = computed(() => stuckMatchesQuery.data.value ?? [])

const leagueItems = computed(() =>
  (leaguesQuery.data.value?.content ?? []).map((league) => ({
    id: league.code,
    label: league.name,
    subtitle: league.code,
    imageUrl: league.emblemUrl,
    active: league.active,
  })),
)

const teamItems = computed(() =>
  (teamsQuery.data.value?.content ?? []).map((team) => ({
    id: team.id,
    label: team.name,
    subtitle: `${team.shortName} - ${team.tla}`,
    imageUrl: team.crestUrl,
    active: team.active,
  })),
)

watch(() => pagination.page.value, () => {
  if (hasSearched.value) {
    matchesQuery.refetch()
  }
})

function buildFilters(): MatchSearchRequest {
  return {
    leagueCode: selectedLeagueCode.value || undefined,
    status: status.value || undefined,
    teamId: selectedTeamId.value || undefined,
    active: activeOnly.value ? true : undefined,
    startTimeFrom: startTimeFrom.value || undefined,
    startTimeTo: startTimeTo.value || undefined,
    manualUpdate: manualUpdate.value === 'ALL' ? undefined : manualUpdate.value === 'TRUE',
  }
}

async function applyFilters() {
  appliedFilters.value = buildFilters()
  hasSearched.value = true
  pagination.reset()
  await matchesQuery.refetch()
}

const exportColumns: ExportColumn<MatchList>[] = [
  { header: 'ID', accessor: (r) => r.id },
  { header: 'Liga', accessor: (r) => r.leagueName },
  { header: 'Echipa gazda', accessor: (r) => r.homeTeamName },
  { header: 'Echipa oaspete', accessor: (r) => r.awayTeamName },
  { header: 'Status', accessor: (r) => r.status },
  { header: 'Ora start', accessor: (r) => r.startTime },
]

async function fetchAllMatchesForExport(): Promise<MatchList[]> {
  const data = await searchMatches(appliedFilters.value, { page: 0, size: 10000, sort: sortBy.value || undefined })
  return data.content
}

function resetFilters() {
  status.value = ''
  manualUpdate.value = 'ALL'
  selectedLeagueCode.value = ''
  selectedTeamId.value = ''
  startTimeFrom.value = ''
  startTimeTo.value = ''
  activeOnly.value = false
  sortBy.value = ''
  appliedFilters.value = {}
  hasSearched.value = false
  pagination.reset()
}

</script>

<template>
  <div class="space-y-6">
    <PageHeader
      title="Administrare - meciuri"
      subtitle="Alegi filtrele pe care le vrei, apoi incarci rezultatele. Meciurile blocate se incarca automat pentru triere rapida."
    >
      <template #actions>
        <RouterLink
          :to="{ name: 'admin-matches-create' }"
          class="rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
        >
          Creeaza meci
        </RouterLink>
      </template>
    </PageHeader>

    <Panel id="stuck-matches" no-hover>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <SectionHeader title="Meciuri blocate" />
          <p class="mt-2 text-sm text-muted">
            Vezi aici meciurile care au ramas in urma cu actualizarea rezultatelor, pentru a le putea actualiza manual daca e necesar. Acestea sunt meciuri care ar fi trebuit sa fie actualizate deja, dar din cauza unor erori sau lipsa de date nu au fost.
          </p>
        </div>
      </div>

      <div v-if="showStuckMatches" class="mt-4">
        <LoadingState v-if="stuckMatchesQuery.isLoading.value" message="Se cauta meciurile blocate..." />

        <div v-else-if="stuckMatches.length" class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          <AdminMatchCard v-for="match in stuckMatches" :key="match.id" :match="match" />
        </div>

        <EmptyState
          v-else
          message="Nu exista meciuri blocate."
          description="Fluxul de sincronizare nu a raportat meciuri ramase in urma."
        />
      </div>
    </Panel>

    <Panel id="match-search" no-hover>
      <SectionHeader title="Filtre si cautare" />
      <p class="mt-2 text-sm text-muted">
        Selectezi liga si echipa direct din liste.
      </p>

      <div class="mt-4 grid gap-3 md:grid-cols-3">
        <label class="text-sm text-muted">
          <span class="mb-1 block">Status</span>
          <select v-model="status" class="app-select-field app-select">
            <option v-for="option in statusOptions" :key="option.label" :value="option.value">{{ option.label }}</option>
          </select>
        </label>

        <label class="text-sm text-muted">
          <span class="mb-1 block">Actualizare manuala</span>
          <select v-model="manualUpdate" class="app-select-field app-select">
            <option value="ALL">Toate</option>
            <option value="TRUE">Da</option>
            <option value="FALSE">Nu</option>
          </select>
        </label>

        <label class="text-sm text-muted">
          <span class="mb-1 block">Doar active</span>
          <select v-model="activeOnly" class="app-select-field app-select">
            <option :value="true">Da</option>
            <option :value="false">Nu</option>
          </select>
        </label>
      </div>

      <div class="mt-3 grid items-end gap-3 md:grid-cols-3">
        <FormInput v-model="startTimeFrom" label="Data inceput" type="datetime-local" />
        <FormInput v-model="startTimeTo" label="Data sfarsit" type="datetime-local" />
        <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
      </div>

      <div class="mt-6 flex justify-start">
        <DatePresetButtons @select="(s, e) => { startTimeFrom = s; startTimeTo = e }" />
      </div>

      <div class="mt-6 grid gap-6 xl:grid-cols-2">
        <AdminEntityPicker
          v-model="selectedLeagueCode"
          title="Selecteaza liga"
          :items="leagueItems"
          :page="leaguesQuery.data.value?.number ?? 0"
          :total-pages="leaguesQuery.data.value?.totalPages ?? 0"
          :loading="leaguesQuery.isLoading.value"
          empty-message="Nu exista ligi disponibile."
          @change-page="leaguePagination.setPage"
        />

        <AdminEntityPicker
          v-model="selectedTeamId"
          title="Selecteaza echipa"
          :items="teamItems"
          :page="teamsQuery.data.value?.number ?? 0"
          :total-pages="teamsQuery.data.value?.totalPages ?? 0"
          :loading="teamsQuery.isLoading.value"
          empty-message="Nu exista echipe disponibile."
          @change-page="teamPagination.setPage"
        />
      </div>

      <div class="mt-6 flex flex-wrap gap-3">
        <AppButton @click="applyFilters">Aplica filtrele</AppButton>
        <AppButton variant="outline" @click="resetFilters">Reseteaza tot</AppButton>
      </div>
    </Panel>

    <Panel id="match-search-results" no-hover>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <SectionHeader title="Rezultate cautare" />
        <ExportButton
          v-if="hasSearched"
          :fetch-all="fetchAllMatchesForExport"
          :columns="exportColumns"
          filename="meciuri"
          title="Meciuri"
          :disabled="!currentMatches.length"
        />
      </div>
      <p class="mt-2 text-sm text-muted">
        Aici vei vedea rezultatele cautarii.
      </p>

      <div class="mt-4">
        <EmptyState
          v-if="!hasSearched"
          message="Nu ai incarcat inca meciuri."
          description="Selecteaza filtrele pe care le vrei si apasa Aplica filtrele."
        />

        <LoadingState v-else-if="matchesQuery.isLoading.value" message="Se incarca meciurile filtrate..." />

        <Transition v-else name="page-fade" mode="out-in" appear>
          <div :key="matchesQuery.data.value?.number ?? 0">
            <div v-if="currentMatches.length" class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <AdminMatchCard v-for="match in currentMatches" :key="match.id" :match="match" />
            </div>
            <EmptyState
              v-else
              message="Nu exista meciuri pentru filtrele selectate."
              description="Poti lasa unele filtre goale sau poti schimba intervalul de timp."
            />
          </div>
        </Transition>
      </div>

      <AppPagination
        v-if="hasSearched && (matchesQuery.data.value?.totalElements ?? 0) > 0"
        :page="matchesQuery.data.value?.number ?? 0"
        :total-pages="matchesQuery.data.value?.totalPages ?? 0"
        :total-elements="matchesQuery.data.value?.totalElements"
        @change="pagination.setPage"
      />
    </Panel>
  </div>
</template>


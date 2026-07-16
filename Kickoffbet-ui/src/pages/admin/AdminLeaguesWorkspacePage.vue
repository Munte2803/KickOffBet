<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { RouterLink, useRouter } from 'vue-router'
import { createLeague, getLeaguePage } from '@/api/leagues.admin.api'
import { getAllTeams } from '@/api/teams.admin.api'
import { leagueSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AppPagination from '@/components/AppPagination.vue'
import AdminEntityPicker from '@/components/AdminEntityPicker.vue'
import LogoFrame from '@/components/LogoFrame.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import FileInputField from '@/components/FileInputField.vue'
import ExportButton from '@/components/ExportButton.vue'
import type { ExportColumn } from '@/utils/export.utils'
import type { LeagueList } from '@/types/league.types'

const toastStore = useToastStore()
const router = useRouter()
const sortBy = ref('name,asc')
const createEmblem = ref<File | null>(null)
const createFileVersion = ref(0)
const listPagination = usePagination(PAGE_SIZES.LIST)
const createTeamsPagination = usePagination(PAGE_SIZES.MEDIUM)
const leaguesPageRequest = computed(() => ({
  ...listPagination.request.value,
  sort: sortBy.value,
}))

const sortOptions = [
  { label: 'Nume A-Z', value: 'name,asc' },
  { label: 'Nume Z-A', value: 'name,desc' },
  { label: 'Cod A-Z', value: 'code,asc' },
  { label: 'Cod Z-A', value: 'code,desc' },
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
]

const leaguesQuery = useQuery({
  queryKey: ['admin-leagues-paged', leaguesPageRequest],
  queryFn: () => getLeaguePage(leaguesPageRequest.value),
})

const createTeamsQuery = useQuery({
  queryKey: ['admin-league-create-teams', createTeamsPagination.request],
  queryFn: () => getAllTeams(createTeamsPagination.request.value),
})

const createForm = useForm({
  validationSchema: toTypedSchema(leagueSchema),
  initialValues: {
    name: '',
    code: '',
    teamIds: [] as string[],
  },
})

const [createName] = createForm.defineField('name')
const [createCode] = createForm.defineField('code')
const [createTeamIds] = createForm.defineField('teamIds')

const createTeamItems = computed(() =>
  (createTeamsQuery.data.value?.content ?? []).map((team) => ({
    id: team.id,
    label: team.name,
    subtitle: `${team.shortName} - ${team.tla}`,
    imageUrl: team.crestUrl,
    active: team.active,
  })),
)

function onCreateFile(file: File | null) {
  createEmblem.value = file
}

const exportColumns: ExportColumn<LeagueList>[] = [
  { header: 'ID', accessor: (r) => r.id },
  { header: 'Cod', accessor: (r) => r.code },
  { header: 'Nume', accessor: (r) => r.name },
  { header: 'Activa', accessor: (r) => (r.active ? 'Da' : 'Nu') },
]

async function fetchAllLeaguesForExport(): Promise<LeagueList[]> {
  const data = await getLeaguePage({ page: 0, size: 10000, sort: sortBy.value })
  return data.content
}

const submitCreate = createForm.handleSubmit(async (values) => {
  try {
    const league = await createLeague(values, createEmblem.value ?? undefined)
    createForm.resetForm()
    createEmblem.value = null
    createFileVersion.value += 1
    await leaguesQuery.refetch()
    toastStore.showSuccess('Liga a fost creata.')
    await router.push({ name: 'admin-league-detail', params: { code: league.code } })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut crea liga.')
  }
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - ligi" subtitle="Creeaza o noua liga, sau selecteaza o liga existenta" />

    <Panel id="league-create" no-hover>
      <h2 class="text-lg font-semibold text-white">Creeaza liga</h2>

      <form class="mt-4 space-y-4" @submit.prevent="submitCreate">
        <FormInput v-model="createName" label="Nume" :error="createForm.errors.value.name" />
        <FormInput v-model="createCode" label="Cod" :error="createForm.errors.value.code" />

        <FileInputField
          label="Emblema"
          :file-name="createEmblem?.name ?? ''"
          helper="Optional: imagine pentru identitatea vizuala a ligii."
          :clear-version="createFileVersion"
          @select="onCreateFile"
        />

        <AdminEntityPicker
          v-model="createTeamIds"
          title="Echipe din liga"
          multiple
          :items="createTeamItems"
          :page="createTeamsQuery.data.value?.number ?? 0"
          :total-pages="createTeamsQuery.data.value?.totalPages ?? 0"
          :loading="createTeamsQuery.isLoading.value"
          empty-message="Nu exista echipe disponibile."
          @change-page="createTeamsPagination.setPage"
        />

        <AppButton type="submit" :loading="createForm.isSubmitting.value">Creeaza liga</AppButton>
      </form>
    </Panel>

    <Panel id="league-list" no-hover>
      <div class="flex items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-white">Toate ligile</h2>
          <p class="text-sm text-gray-400">Deschide pagina unei ligi pentru editare, asocierea echipelor sau schimbarea starii.</p>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm text-gray-400">{{ leaguesQuery.data.value?.totalElements ?? 0 }} ligi</span>
          <div class="w-48">
            <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
          </div>
          <ExportButton
            :fetch-all="fetchAllLeaguesForExport"
            :columns="exportColumns"
            filename="ligi"
            title="Ligi"
            :disabled="!(leaguesQuery.data.value?.content?.length ?? 0)"
          />
        </div>
      </div>

      <Transition name="page-fade" mode="out-in" appear>
        <div
          v-if="leaguesQuery.data.value?.content?.length"
          :key="leaguesQuery.data.value?.number ?? 0"
          class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3"
        >
          <RouterLink
            v-for="league in leaguesQuery.data.value?.content ?? []"
            :key="league.id"
            :to="{ name: 'admin-league-detail', params: { code: league.code } }"
            class="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-black/40 px-4 py-3 text-left transition-colors hover:border-blue-600"
          >
            <div class="flex min-w-0 items-center gap-3">
              <LogoFrame v-if="league.emblemUrl" :src="league.emblemUrl" size="md" />
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-white">{{ league.name }}</p>
                <p class="text-xs uppercase tracking-[0.2em] text-gray-500">{{ league.code }}</p>
              </div>
            </div>
            <StatusBadge :status="league.active ? 'ACTIVE' : 'DEACTIVATED'" />
          </RouterLink>
        </div>
      </Transition>

      <EmptyState
        v-if="!(leaguesQuery.data.value?.content?.length ?? 0)"
        class="mt-4"
        message="Nu exista ligi configurate."
      />

      <AppPagination
        v-if="(leaguesQuery.data.value?.totalElements ?? 0) > 0"
        :page="leaguesQuery.data.value?.number ?? 0"
        :total-pages="leaguesQuery.data.value?.totalPages ?? 0"
        :total-elements="leaguesQuery.data.value?.totalElements"
        @change="listPagination.setPage"
      />
    </Panel>
  </div>
</template>


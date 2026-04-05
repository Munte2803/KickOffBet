<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { RouterLink, useRouter } from 'vue-router'
import { getLeaguePage } from '@/api/leagues.admin.api'
import { createTeam, getAllTeams } from '@/api/teams.admin.api'
import { teamSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
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

const toastStore = useToastStore()
const router = useRouter()
const sortBy = ref('name,asc')
const createEmblem = ref<File | null>(null)
const createFileVersion = ref(0)
const listPagination = usePagination(8)
const createLeaguesPagination = usePagination(6)
const teamsPageRequest = computed(() => ({
  ...listPagination.request.value,
  sort: sortBy.value,
}))

const sortOptions = [
  { label: 'Nume A-Z', value: 'name,asc' },
  { label: 'Nume Z-A', value: 'name,desc' },
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
]

const teamsQuery = useQuery({
  queryKey: ['admin-teams-paged', teamsPageRequest],
  queryFn: () => getAllTeams(teamsPageRequest.value),
})

const createLeaguesQuery = useQuery({
  queryKey: ['admin-team-create-leagues', createLeaguesPagination.request],
  queryFn: () => getLeaguePage(createLeaguesPagination.request.value),
})

const createForm = useForm({
  validationSchema: toTypedSchema(teamSchema),
  initialValues: {
    name: '',
    shortName: '',
    tla: '',
    leagueIds: [] as string[],
  },
})

const [createName] = createForm.defineField('name')
const [createShortName] = createForm.defineField('shortName')
const [createTla] = createForm.defineField('tla')
const [createLeagueIds] = createForm.defineField('leagueIds')

const createLeagueItems = computed(() =>
  (createLeaguesQuery.data.value?.content ?? []).map((league) => ({
    id: league.id,
    label: league.name,
    subtitle: league.code,
    imageUrl: league.emblemUrl,
    active: league.active,
  })),
)

function onCreateFile(file: File | null) {
  createEmblem.value = file
}

const submitCreate = createForm.handleSubmit(async (values) => {
  try {
    const team = await createTeam(values, createEmblem.value ?? undefined)
    createForm.resetForm()
    createEmblem.value = null
    createFileVersion.value += 1
    await teamsQuery.refetch()
    toastStore.showSuccess('Echipa a fost creata.')
    await router.push({ name: 'admin-team-detail', params: { id: team.id } })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut crea echipa.')
  }
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - echipe" subtitle="Creeaza o noua echipa, sau selecteaza o echipa existenta" />

    <Panel id="team-create" no-hover>
      <h2 class="text-lg font-semibold text-white">Creeaza echipa</h2>

      <form class="mt-4 space-y-4" @submit.prevent="submitCreate">
        <FormInput v-model="createName" label="Nume" :error="createForm.errors.value.name" />
        <FormInput v-model="createShortName" label="Nume scurt" :error="createForm.errors.value.shortName" />
        <FormInput v-model="createTla" label="Abreviere" :error="createForm.errors.value.tla" />

        <FileInputField
          label="Stema"
          :file-name="createEmblem?.name ?? ''"
          helper="Optional: imagine pentru identificarea echipei."
          :clear-version="createFileVersion"
          @select="onCreateFile"
        />

        <AdminEntityPicker
          v-model="createLeagueIds"
          title="Ligi asociate"
          multiple
          :items="createLeagueItems"
          :page="createLeaguesQuery.data.value?.number ?? 0"
          :total-pages="createLeaguesQuery.data.value?.totalPages ?? 0"
          :loading="createLeaguesQuery.isLoading.value"
          empty-message="Nu exista ligi disponibile."
          @change-page="createLeaguesPagination.setPage"
        />

        <AppButton type="submit" :loading="createForm.isSubmitting.value">Creeaza echipa</AppButton>
      </form>
    </Panel>

    <Panel id="team-list" no-hover>
      <div class="flex items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-white">Toate echipele</h2>
          <p class="text-sm text-gray-400">Deschide pagina unei echipe pentru editare, asocierea ligilor sau schimbarea starii.</p>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm text-gray-400">{{ teamsQuery.data.value?.totalElements ?? 0 }} echipe</span>
          <div class="w-48">
            <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
          </div>
        </div>
      </div>

      <div class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <RouterLink
          v-for="team in teamsQuery.data.value?.content ?? []"
          :key="team.id"
          :to="{ name: 'admin-team-detail', params: { id: team.id } }"
          class="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-black/40 px-4 py-3 text-left transition-colors hover:border-blue-700"
        >
          <div class="flex min-w-0 items-center gap-3">
            <LogoFrame v-if="team.crestUrl" :src="team.crestUrl" size="md" />
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-white">{{ team.name }}</p>
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">{{ team.tla }}</p>
            </div>
          </div>
          <StatusBadge :status="team.active ? 'ACTIVE' : 'DEACTIVATED'" />
        </RouterLink>
      </div>

      <EmptyState
        v-if="!(teamsQuery.data.value?.content?.length ?? 0)"
        class="mt-4"
        message="Nu exista echipe configurate."
      />

      <AppPagination
        v-if="(teamsQuery.data.value?.totalPages ?? 0) > 1"
        :page="teamsQuery.data.value?.number ?? 0"
        :total-pages="teamsQuery.data.value?.totalPages ?? 0"
        @change="listPagination.setPage"
      />
    </Panel>
  </div>
</template>


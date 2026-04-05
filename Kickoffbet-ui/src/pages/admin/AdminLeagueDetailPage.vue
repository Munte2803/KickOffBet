<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { getLeagueByCode, toggleLeagueStatus, updateLeague } from '@/api/leagues.admin.api'
import { getAllTeams } from '@/api/teams.admin.api'
import { leagueSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import AdminEntityPicker from '@/components/AdminEntityPicker.vue'
import FileInputField from '@/components/FileInputField.vue'
import { formatDateShort } from '@/utils/date.utils'

const route = useRoute()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const leagueCode = route.params.code as string
const editEmblem = ref<File | null>(null)
const editFileVersion = ref(0)
const teamsPagination = usePagination(6)

const leagueQuery = useQuery({
  queryKey: ['admin-league-detail-route', leagueCode],
  queryFn: () => getLeagueByCode(leagueCode),
})

const teamsQuery = useQuery({
  queryKey: ['admin-league-detail-teams', teamsPagination.request],
  queryFn: () => getAllTeams(teamsPagination.request.value),
})

const editForm = useForm({
  validationSchema: toTypedSchema(leagueSchema.pick({ name: true, teamIds: true })),
  initialValues: {
    name: '',
    teamIds: [] as string[],
  },
})

const [name] = editForm.defineField('name')
const [teamIds] = editForm.defineField('teamIds')

const teamItems = computed(() =>
  (teamsQuery.data.value?.content ?? []).map((team) => ({
    id: team.id,
    label: team.name,
    subtitle: `${team.shortName} - ${team.tla}`,
    imageUrl: team.crestUrl,
    active: team.active,
  })),
)

watch(
  () => leagueQuery.data.value,
  (league) => {
    if (!league) return
    editForm.setValues({
      name: league.name,
      teamIds: league.teams.map((team) => team.id),
    })
  },
  { immediate: true },
)

function onFileChange(file: File | null) {
  editEmblem.value = file
}

const submitEdit = editForm.handleSubmit(async (values) => {
  const confirmed = await confirm({
    title: 'Salvare liga',
    message: 'Sunteti sigur ca vreti sa salvati modificarile ligii?',
    description: 'Vor fi actualizate numele, emblema si asocierea echipelor.',
    confirmLabel: 'Salveaza liga',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  try {
    await updateLeague(leagueCode, values, editEmblem.value ?? undefined)
    editEmblem.value = null
    editFileVersion.value += 1
    await leagueQuery.refetch()
    toastStore.showSuccess('Liga a fost actualizata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza liga.')
  }
})

async function switchLeague(active: boolean) {
  const confirmed = await confirm({
    title: active ? 'Activare liga' : 'Dezactivare liga',
    message: active
      ? 'Sunteti sigur ca vreti sa activati liga?'
      : 'Sunteti sigur ca vreti sa dezactivati liga?',
    description: 'Starea ligii se va schimba imediat pentru utilizatori si administratori.',
    confirmLabel: active ? 'Activeaza liga' : 'Dezactiveaza liga',
    cancelLabel: 'Renunta',
    variant: active ? 'primary' : 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await toggleLeagueStatus(leagueCode, active)
    await leagueQuery.refetch()
    toastStore.showSuccess('Starea ligii a fost schimbata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea ligii.')
  }
}
</script>

<template>
  <LoadingState v-if="leagueQuery.isLoading.value" message="Se incarca liga..." />

  <div v-else-if="leagueQuery.data.value" class="space-y-6">
    <PageHeader
      :title="`Administrare - ${leagueQuery.data.value.name}`"
      :subtitle="`${leagueQuery.data.value.code} - ${formatDateShort(leagueQuery.data.value.createdAt)}`"
      :logo-url="leagueQuery.data.value.emblemUrl"
      :back-route="{ name: 'admin-leagues' }"
    >
      <template #actions>
        <AppButton variant="outline" @click="switchLeague(!leagueQuery.data.value.active)">
          {{ leagueQuery.data.value.active ? 'Dezactiveaza liga' : 'Activeaza liga' }}
        </AppButton>
      </template>
    </PageHeader>

    <section id="league-overview" class="grid gap-4 md:grid-cols-3">
      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Stare</p>
        <div class="mt-3">
          <StatusBadge :status="leagueQuery.data.value.active ? 'ACTIVE' : 'DEACTIVATED'" />
        </div>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Cod</p>
        <p class="mt-2 text-2xl font-bold text-white">{{ leagueQuery.data.value.code }}</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Echipe</p>
        <p class="mt-2 text-2xl font-bold text-white">{{ leagueQuery.data.value.teams.length }}</p>
      </Panel>
    </section>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
      <Panel id="league-edit" no-hover>
        <h2 class="text-lg font-semibold text-white">Editare liga</h2>
        <p class="mt-1 text-sm text-gray-400">Editeaza detaliile ligii.</p>

        <form class="mt-4 space-y-4" @submit.prevent="submitEdit">
          <FormInput v-model="name" label="Nume" :error="editForm.errors.value.name" />

          <FileInputField
            label="Emblema"
            :file-name="editEmblem?.name ?? ''"
            helper="Optional: imagine noua pentru identitatea vizuala a ligii."
            :clear-version="editFileVersion"
            @select="onFileChange"
          />

          <AdminEntityPicker
            v-model="teamIds"
            title="Echipe asociate"
            multiple
            :items="teamItems"
            :page="teamsQuery.data.value?.number ?? 0"
            :total-pages="teamsQuery.data.value?.totalPages ?? 0"
            :loading="teamsQuery.isLoading.value"
            empty-message="Nu exista echipe disponibile."
            @change-page="teamsPagination.setPage"
          />

          <AppButton type="submit" :loading="editForm.isSubmitting.value">Salveaza modificarile</AppButton>
        </form>
      </Panel>

      <Panel id="league-teams" no-hover>
        <h2 class="text-lg font-semibold text-white">Echipe din liga</h2>

        <div class="mt-4 space-y-3">
          <RouterLink
            v-for="team in leagueQuery.data.value.teams"
            :key="team.id"
            :to="{ name: 'admin-team-detail', params: { id: team.id } }"
            class="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-black/40 px-4 py-3 transition-colors hover:border-blue-700"
          >
            <div>
              <p class="text-sm font-semibold text-white">{{ team.name }}</p>
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">{{ team.tla }}</p>
            </div>
            <StatusBadge :status="team.active ? 'ACTIVE' : 'DEACTIVATED'" />
          </RouterLink>
        </div>

        <EmptyState
          v-if="!leagueQuery.data.value.teams.length"
          class="mt-4"
          message="Liga nu are echipe asociate."
        />
      </Panel>
    </div>
  </div>

  <EmptyState v-else message="Nu am gasit liga ceruta" />
</template>



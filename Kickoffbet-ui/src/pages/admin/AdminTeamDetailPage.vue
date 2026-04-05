<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { getLeaguePage } from '@/api/leagues.admin.api'
import { getTeamById, toggleTeamStatus, updateTeam } from '@/api/teams.admin.api'
import { teamSchema } from '@/validation/forms'
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
const teamId = route.params.id as string
const editEmblem = ref<File | null>(null)
const editFileVersion = ref(0)
const leaguesPagination = usePagination(6)

const teamQuery = useQuery({
  queryKey: ['admin-team-detail-route', teamId],
  queryFn: () => getTeamById(teamId),
})

const leaguesQuery = useQuery({
  queryKey: ['admin-team-detail-leagues', leaguesPagination.request],
  queryFn: () => getLeaguePage(leaguesPagination.request.value),
})

const editForm = useForm({
  validationSchema: toTypedSchema(teamSchema),
  initialValues: {
    name: '',
    shortName: '',
    tla: '',
    leagueIds: [] as string[],
  },
})

const [name] = editForm.defineField('name')
const [shortName] = editForm.defineField('shortName')
const [tla] = editForm.defineField('tla')
const [leagueIds] = editForm.defineField('leagueIds')

const leagueItems = computed(() =>
  (leaguesQuery.data.value?.content ?? []).map((league) => ({
    id: league.id,
    label: league.name,
    subtitle: league.code,
    imageUrl: league.emblemUrl,
    active: league.active,
  })),
)

watch(
  () => teamQuery.data.value,
  (team) => {
    if (!team) return
    editForm.setValues({
      name: team.name,
      shortName: team.shortName,
      tla: team.tla,
      leagueIds: team.leagues.map((league) => league.id),
    })
  },
  { immediate: true },
)

function onFileChange(file: File | null) {
  editEmblem.value = file
}

const submitEdit = editForm.handleSubmit(async (values) => {
  const confirmed = await confirm({
    title: 'Salvare echipa',
    message: 'Sunteti sigur ca vreti sa salvati modificarile echipei?',
    description: 'Vor fi actualizate numele, abrevierea, stema si ligile asociate.',
    confirmLabel: 'Salveaza echipa',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  try {
    await updateTeam(teamId, values, editEmblem.value ?? undefined)
    editEmblem.value = null
    editFileVersion.value += 1
    await teamQuery.refetch()
    toastStore.showSuccess('Echipa a fost actualizata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza echipa.')
  }
})

async function switchTeam(active: boolean) {
  const confirmed = await confirm({
    title: active ? 'Activare echipa' : 'Dezactivare echipa',
    message: active
      ? 'Sunteti sigur ca vreti sa activati echipa?'
      : 'Sunteti sigur ca vreti sa dezactivati echipa?',
    description: 'Starea echipei se va schimba imediat in toate zonele aplicatiei.',
    confirmLabel: active ? 'Activeaza echipa' : 'Dezactiveaza echipa',
    cancelLabel: 'Renunta',
    variant: active ? 'primary' : 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await toggleTeamStatus(teamId, active)
    await teamQuery.refetch()
    toastStore.showSuccess('Starea echipei a fost schimbata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea echipei.')
  }
}
</script>

<template>
  <LoadingState v-if="teamQuery.isLoading.value" message="Se incarca echipa..." />

  <div v-else-if="teamQuery.data.value" class="space-y-6">
    <PageHeader
      :title="`Administrare - ${teamQuery.data.value.name}`"
      :subtitle="`${teamQuery.data.value.tla} - ${formatDateShort(teamQuery.data.value.createdAt)}`"
      :logo-url="teamQuery.data.value.crestUrl"
      :back-route="{ name: 'admin-teams' }"
    >
      <template #actions>
        <AppButton variant="outline" @click="switchTeam(!teamQuery.data.value.active)">
          {{ teamQuery.data.value.active ? 'Dezactiveaza echipa' : 'Activeaza echipa' }}
        </AppButton>
      </template>
    </PageHeader>

    <section id="team-overview" class="grid gap-4 md:grid-cols-4">
      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Stare</p>
        <div class="mt-3">
          <StatusBadge :status="teamQuery.data.value.active ? 'ACTIVE' : 'DEACTIVATED'" />
        </div>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">TLA</p>
        <p class="mt-2 text-2xl font-bold text-white">{{ teamQuery.data.value.tla }}</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Nume scurt</p>
        <p class="mt-2 text-lg font-bold text-white">{{ teamQuery.data.value.shortName }}</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Ligi</p>
        <p class="mt-2 text-2xl font-bold text-white">{{ teamQuery.data.value.leagues.length }}</p>
      </Panel>
    </section>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
      <Panel id="team-edit" no-hover>
        <h2 class="text-lg font-semibold text-white">Editare echipa</h2>
        <p class="mt-1 text-sm text-gray-400">Editeaza detaliile echipei.</p>

        <form class="mt-4 space-y-4" @submit.prevent="submitEdit">
          <FormInput v-model="name" label="Nume" :error="editForm.errors.value.name" />
          <FormInput v-model="shortName" label="Nume scurt" :error="editForm.errors.value.shortName" />
          <FormInput v-model="tla" label="Abreviere" :error="editForm.errors.value.tla" />

          <FileInputField
            label="Stema"
            :file-name="editEmblem?.name ?? ''"
            helper="Optional: imagine noua pentru identificarea echipei."
            :clear-version="editFileVersion"
            @select="onFileChange"
          />

          <AdminEntityPicker
            v-model="leagueIds"
            title="Ligi asociate"
            multiple
            :items="leagueItems"
            :page="leaguesQuery.data.value?.number ?? 0"
            :total-pages="leaguesQuery.data.value?.totalPages ?? 0"
            :loading="leaguesQuery.isLoading.value"
            empty-message="Nu exista ligi disponibile."
            @change-page="leaguesPagination.setPage"
          />

          <AppButton type="submit" :loading="editForm.isSubmitting.value">Salveaza modificarile</AppButton>
        </form>
      </Panel>

      <Panel id="team-leagues" no-hover>
        <h2 class="text-lg font-semibold text-white">Ligi asociate</h2>

        <div class="mt-4 space-y-3">
          <RouterLink
            v-for="league in teamQuery.data.value.leagues"
            :key="league.id"
            :to="{ name: 'admin-league-detail', params: { code: league.code } }"
            class="flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-black/40 px-4 py-3 transition-colors hover:border-blue-700"
          >
            <div>
              <p class="text-sm font-semibold text-white">{{ league.name }}</p>
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">{{ league.code }}</p>
            </div>
            <StatusBadge :status="league.active ? 'ACTIVE' : 'DEACTIVATED'" />
          </RouterLink>
        </div>

        <EmptyState
          v-if="!teamQuery.data.value.leagues.length"
          class="mt-4"
          message="Echipa nu are ligi asociate."
        />
      </Panel>
    </div>
  </div>

  <EmptyState v-else message="Nu am gasit echipa ceruta in admin." />
</template>



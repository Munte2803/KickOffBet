<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { createMatch } from '@/api/matches.admin.api'
import { getLeagueByCode, getLeaguePage } from '@/api/leagues.admin.api'
import type { EditableMarketOffer } from '@/types/match.types'
import { createMatchSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import DateTimeInput from '@/components/DateTimeInput.vue'
import AdminEntityPicker from '@/components/AdminEntityPicker.vue'
import AdminOfferBuilder from '@/components/AdminOfferBuilder.vue'

const router = useRouter()
const toastStore = useToastStore()
const offers = ref<EditableMarketOffer[]>([])
const leaguePagination = usePagination(PAGE_SIZES.SMALL)
const homeTeamsPagination = usePagination(PAGE_SIZES.SMALL)
const awayTeamsPagination = usePagination(PAGE_SIZES.SMALL)
const selectedLeagueCode = ref('')

const leaguesQuery = useQuery({
  queryKey: ['admin-leagues-for-match', leaguePagination.request],
  queryFn: () => getLeaguePage(leaguePagination.request.value),
})

const selectedLeagueQuery = useQuery({
  queryKey: ['admin-selected-league-for-match', selectedLeagueCode],
  queryFn: () => getLeagueByCode(selectedLeagueCode.value),
  enabled: computed(() => Boolean(selectedLeagueCode.value)),
})

const leagueItems = computed(() =>
  (leaguesQuery.data.value?.content ?? []).map((league) => ({
    id: league.id,
    label: league.name,
    subtitle: league.code,
    imageUrl: league.emblemUrl,
    active: league.active,
  })),
)

const teamItems = computed(() =>
  [...(selectedLeagueQuery.data.value?.teams ?? [])]
    .sort((left, right) => left.name.localeCompare(right.name))
    .map((team) => ({
    id: team.id,
    label: team.name,
    subtitle: `${team.shortName} - ${team.tla}`,
    imageUrl: team.crestUrl,
    active: team.active,
  })),
)

const teamsTotalPages = computed(() =>
  Math.max(1, Math.ceil(teamItems.value.length / homeTeamsPagination.size.value)),
)

const homeTeamsPage = computed(() => {
  const start = homeTeamsPagination.page.value * homeTeamsPagination.size.value
  return teamItems.value.slice(start, start + homeTeamsPagination.size.value)
})

const awayTeamsPage = computed(() => {
  const start = awayTeamsPagination.page.value * awayTeamsPagination.size.value
  return teamItems.value.slice(start, start + awayTeamsPagination.size.value)
})

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(createMatchSchema),
  initialValues: {
    startTime: '',
    leagueId: '',
    homeTeamId: '',
    awayTeamId: '',
  },
})

const [startTime] = defineField('startTime')
const [leagueId] = defineField('leagueId')
const [homeTeamId] = defineField('homeTeamId')
const [awayTeamId] = defineField('awayTeamId')

const canChooseTeams = computed(() => Boolean(selectedLeagueCode.value))
const teamEmptyMessage = computed(() =>
  canChooseTeams.value
    ? 'Nu exista echipe disponibile in liga selectata.'
    : 'Alege liga pentru a putea alege echipele.',
)

const onSubmit = handleSubmit(async (values) => {
  try {
    const match = await createMatch({
      startTime: values.startTime,
      leagueId: values.leagueId,
      homeTeamId: values.homeTeamId,
      awayTeamId: values.awayTeamId,
      availableOffers: offers.value.map((offer) => ({
        marketType: offer.marketType,
        option: offer.option,
        line: offer.line,
        odds: offer.odds,
      })),
    })

    toastStore.showSuccess('Meciul a fost creat.')
    await router.push({ name: 'admin-match-detail', params: { id: match.id } })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut crea meciul.')
  }
})

watch(leagueId, (nextLeagueId) => {
  const league = leaguesQuery.data.value?.content.find((item) => item.id === nextLeagueId)
  selectedLeagueCode.value = league?.code ?? ''
  homeTeamId.value = ''
  awayTeamId.value = ''
  homeTeamsPagination.setPage(0)
  awayTeamsPagination.setPage(0)
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - creeaza meci" :back-route="{ name: 'admin-matches' }" />

    <Panel no-hover>
      <form class="space-y-6" @submit.prevent="onSubmit">
          <DateTimeInput v-model="startTime" label="Ora de start" required :error="errors.startTime" />

        <div class="grid gap-6 xl:grid-cols-3">
          <div class="space-y-2">
            <AdminEntityPicker
              v-model="leagueId"
              title="Liga"
              :items="leagueItems"
              :page="leaguesQuery.data.value?.number ?? 0"
              :total-pages="leaguesQuery.data.value?.totalPages ?? 0"
              :loading="leaguesQuery.isLoading.value"
              empty-message="Nu exista ligi disponibile."
              @change-page="leaguePagination.setPage"
            />
            <span v-if="errors.leagueId" class="block text-xs text-red-400">{{ errors.leagueId }}</span>
          </div>

          <div class="space-y-2">
            <AdminEntityPicker
              v-model="homeTeamId"
              title="Echipa gazda"
              :items="homeTeamsPage"
              :page="homeTeamsPagination.page.value"
              :total-pages="teamsTotalPages"
              :loading="canChooseTeams ? selectedLeagueQuery.isLoading.value : false"
              :empty-message="teamEmptyMessage"
              @change-page="homeTeamsPagination.setPage"
            />
            <span v-if="errors.homeTeamId" class="block text-xs text-red-400">{{ errors.homeTeamId }}</span>
          </div>

          <div class="space-y-2">
            <AdminEntityPicker
              v-model="awayTeamId"
              title="Echipa oaspete"
              :items="awayTeamsPage"
              :page="awayTeamsPagination.page.value"
              :total-pages="teamsTotalPages"
              :loading="canChooseTeams ? selectedLeagueQuery.isLoading.value : false"
              :empty-message="teamEmptyMessage"
              @change-page="awayTeamsPagination.setPage"
            />
            <span v-if="errors.awayTeamId" class="block text-xs text-red-400">{{ errors.awayTeamId }}</span>
          </div>
        </div>

        <AdminOfferBuilder v-model="offers" />

        <div class="flex justify-end">
          <AppButton type="submit" size="lg" :loading="isSubmitting">Creeaza meci</AppButton>
        </div>
      </form>
    </Panel>
  </div>
</template>


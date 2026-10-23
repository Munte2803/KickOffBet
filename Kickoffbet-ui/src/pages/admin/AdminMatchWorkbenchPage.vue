<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  getMatchById,
  rescheduleMatch,
  toggleMatchStatus,
  toggleOfferStatus,
  updateMatch,
  updateMatchStatus,
} from '@/api/matches.admin.api'
import type { EditableMarketOffer, MatchDetail } from '@/types/match.types'
import type { MatchStatus } from '@/types/enums'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'
import DateTimeInput from '@/components/DateTimeInput.vue'
import MarketOffersGrid from '@/components/MarketOffersGrid.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatDateShort, formatDateTime, formatForInput } from '@/utils/date.utils'
import { translateEnumLabel } from '@/utils/labels.utils'
import { queryKeys } from '@/query/query-keys'
import { invalidateAdminMatchQueries } from '@/query/query-client'
import { ApiError } from '@/api/api-error'
import AppErrorState from '@/components/AppErrorState.vue'

const route = useRoute()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const matchId = route.params.id as string

const statusOptions: MatchStatus[] = ['SCHEDULED', 'LIVE', 'FINISHED', 'POSTPONED', 'SUSPENDED', 'CANCELLED']

const startTime = ref('')
const status = ref<MatchStatus>('SCHEDULED')
// v-model.number keeps an emptied field as ''
const ftHome = ref<number | string | null>(null)
const ftAway = ref<number | string | null>(null)
const offers = ref<EditableMarketOffer[]>([])

const matchQuery = useQuery({
  queryKey: queryKeys.adminMatchWorkbench(matchId),
  queryFn: () => getMatchById(matchId),
})

const match = computed(() => matchQuery.data.value)
// A missing match is reported as "not found"; any other failure (network, 5xx) offers a retry
const isNotFound = computed(
  () => matchQuery.error.value instanceof ApiError && matchQuery.error.value.code === 'RESOURCE_NOT_FOUND',
)

/** The save in flight, if any: its button shows a spinner and every other save waits. */
const saving = ref<'time' | 'status' | 'odds' | 'active' | 'offer' | null>(null)

async function runSave(section: NonNullable<typeof saving.value>, task: () => Promise<void>) {
  // The confirm dialog is async: a second click may have started a save meanwhile
  if (saving.value) return
  saving.value = section
  try {
    await task()
  } finally {
    saving.value = null
  }
}

/** A typed score: a whole number >= 0, or null when the field is empty. NaN for anything else. */
function readScore(value: number | string | null): number | null {
  if (value === null || value === '') return null
  const score = Number(value)
  return Number.isInteger(score) && score >= 0 ? score : Number.NaN
}

function resetTimeForm(value: MatchDetail) {
  startTime.value = formatForInput(value.startTime)
}

function resetStatusForm(value: MatchDetail) {
  status.value = value.status
  ftHome.value = value.ftHome
  ftAway.value = value.ftAway
}

function resetOffersForm(value: MatchDetail) {
  offers.value = value.availableOffers.map((offer) => ({
    id: offer.id,
    marketType: offer.marketType,
    option: offer.option,
    line: offer.line,
    odds: offer.odds,
    active: offer.active,
  }))
}

// The form is filled from the first load only: background refetches (window focus, saving another
// section) must not overwrite values the admin is still editing. Each save resets just its own section.
const formInitialized = ref(false)
watch(
  () => match.value,
  (value) => {
    if (!value || formInitialized.value) return
    resetTimeForm(value)
    resetStatusForm(value)
    resetOffersForm(value)
    formInitialized.value = true
  },
  { immediate: true },
)

/** After a save: this match (returned fresh), the admin lists and every public view of matches. */
async function refreshAfterSave() {
  await invalidateAdminMatchQueries()
  return matchQuery.data.value
}

async function saveTime() {
  if (saving.value) return
  const confirmed = await confirm({
    title: 'Salvare programare',
    message: 'Sunteti sigur ca vreti sa salvati noua ora a meciului?',
    description: 'Programarea publica a meciului va fi actualizata imediat.',
    confirmLabel: 'Salveaza ora',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  await runSave('time', async () => {
    try {
      await rescheduleMatch(matchId, startTime.value)
      const fresh = await refreshAfterSave()
      if (fresh) resetTimeForm(fresh)
      toastStore.showSuccess('Ora meciului a fost actualizata.')
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza ora meciului.')
    }
  })
}

async function saveStatus() {
  if (saving.value) return
  // v-model.number leaves an emptied field as '' (and a decimal as a fraction): validate what was typed
  const home = readScore(ftHome.value)
  const away = readScore(ftAway.value)
  if (Number.isNaN(home) || Number.isNaN(away)) {
    toastStore.showError('Scorul trebuie sa fie un numar intreg, cel putin 0.')
    return
  }

  const finishing = status.value === 'FINISHED'
  if (finishing && (home === null || away === null)) {
    toastStore.showError('Completeaza scorul final al ambelor echipe.')
    return
  }

  // Finishing settles every ticket on the match and cannot be undone: show exactly what is saved
  const confirmed = await confirm({
    title: 'Salvare status meci',
    message: finishing
      ? `Sunteti sigur ca vreti sa finalizati meciul cu scorul ${home} - ${away}?`
      : 'Sunteti sigur ca vreti sa salvati statusul si scorul meciului?',
    description: finishing
      ? 'Biletele vor fi decontate pe baza acestui scor. Un meci finalizat nu mai poate fi modificat.'
      : 'Modificarile vor afecta imediat afisarea meciului pentru utilizatori.',
    confirmLabel: finishing ? 'Finalizeaza meciul' : 'Salveaza statusul',
    cancelLabel: 'Renunta',
    variant: finishing ? 'danger' : 'primary',
  })

  if (!confirmed) {
    return
  }

  await runSave('status', async () => {
    try {
      await updateMatchStatus(matchId, {
        status: status.value,
        ftHome: home,
        ftAway: away,
      })
      const fresh = await refreshAfterSave()
      if (fresh) resetStatusForm(fresh)
      toastStore.showSuccess('Statusul meciului a fost actualizat.')
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza statusul meciului.')
    }
  })
}

async function saveOdds() {
  if (saving.value) return
  if (offers.value.some((offer) => !Number.isFinite(offer.odds) || offer.odds < 1.01)) {
    toastStore.showError('Cotele trebuie sa fie cel putin 1.01.')
    return
  }

  const confirmed = await confirm({
    title: 'Salvare cote',
    message: 'Sunteti sigur ca vreti sa salvati modificarile cotelor?',
    description: 'Cotele vor fi actualizate imediat pentru toate ofertele editate.',
    confirmLabel: 'Salveaza cotele',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  await runSave('odds', async () => {
    try {
      await updateMatch(matchId, {
        availableOffers: offers.value
          .filter((offer): offer is EditableMarketOffer & { id: string } => Boolean(offer.id))
          .map((offer) => ({
            id: offer.id,
            odds: offer.odds,
          })),
      })
      const fresh = await refreshAfterSave()
      if (fresh) resetOffersForm(fresh)
      toastStore.showSuccess('Cotele au fost actualizate.')
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut salva cotele.')
    }
  })
}

async function switchMatchActive() {
  if (!match.value || saving.value) return
  const nextActive = !match.value.active

  const confirmed = await confirm({
    title: match.value.active ? 'Dezactivare meci' : 'Activare meci',
    message: match.value.active
      ? 'Sunteti sigur ca vreti sa dezactivati meciul?'
      : 'Sunteti sigur ca vreti sa activati meciul?',
    description: 'Vizibilitatea si disponibilitatea pentru pariere se vor actualiza imediat.',
    confirmLabel: match.value.active ? 'Dezactiveaza meciul' : 'Activeaza meciul',
    cancelLabel: 'Renunta',
    variant: match.value.active ? 'danger' : 'primary',
  })

  if (!confirmed) {
    return
  }

  await runSave('active', async () => {
    try {
      await toggleMatchStatus(matchId, nextActive)
      await refreshAfterSave()
      toastStore.showSuccess(nextActive ? 'Meciul a fost activat.' : 'Meciul a fost dezactivat.')
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea meciului.')
    }
  })
}

async function switchOfferActive(offerId: string, active: boolean) {
  if (saving.value) return
  const confirmed = await confirm({
    title: active ? 'Activare oferta' : 'Dezactivare oferta',
    message: active
      ? 'Sunteti sigur ca vreti sa activati oferta selectata?'
      : 'Sunteti sigur ca vreti sa dezactivati oferta selectata?',
    description: 'Oferta isi va schimba imediat disponibilitatea in interfata publica.',
    confirmLabel: active ? 'Activeaza oferta' : 'Dezactiveaza oferta',
    cancelLabel: 'Renunta',
    variant: active ? 'primary' : 'danger',
  })

  if (!confirmed) {
    return
  }

  await runSave('offer', async () => {
    try {
      await toggleOfferStatus(matchId, offerId, active)
      // Only the toggled offer's flag changes in the form; odds still being edited are kept
      const editedOffer = offers.value.find((offer) => offer.id === offerId)
      if (editedOffer) editedOffer.active = active
      await refreshAfterSave()
      toastStore.showSuccess('Starea ofertei a fost schimbata.')
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea ofertei.')
    }
  })
}
</script>

<template>
  <LoadingState v-if="matchQuery.isLoading.value" message="Se incarca meciul din admin..." />

  <div v-else-if="match" class="space-y-6">
    <PageHeader
      :back-route="{ name: 'admin-matches' }"
      :scoreboard="true"
      :home-team-name="match.homeTeamName"
      :home-team-logo="match.homeTeamLogo"
      :away-team-name="match.awayTeamName"
      :away-team-logo="match.awayTeamLogo"
      :subtitle="`${match.leagueName} - ${formatDateTime(match.startTime)}`"
    >
      <template #actions>
        <AppButton variant="outline" :loading="saving === 'active'" :disabled="saving !== null" @click="switchMatchActive">
          {{ match.active ? 'Dezactiveaza meciul' : 'Activeaza meciul' }}
        </AppButton>
      </template>
    </PageHeader>

    <section id="match-overview" class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-subtle">Status curent</p>
        <div class="mt-3">
          <StatusBadge :status="match.status" />
        </div>
        <p class="mt-3 text-sm text-muted">Start programat: {{ formatDateShort(match.startTime) }}</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-subtle">Vizibilitate</p>
        <p class="mt-2 text-2xl font-bold text-fg">{{ match.active ? 'Activ' : 'Inactiv' }}</p>
        <p class="mt-1 text-sm text-muted">Controleaza rapid daca meciul poate fi vazut si jucat.</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-subtle">Scor final</p>
        <p class="mt-2 text-2xl font-bold text-fg">{{ match.ftHome ?? '-' }} - {{ match.ftAway ?? '-' }}</p>
        <p class="mt-1 text-sm text-muted">Actualizeaza scorul doar din zona de status a meciului.</p>
      </Panel>
    </section>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <Panel id="match-schedule" no-hover>
        <SectionHeader title="Programare" />
        <div class="flex flex-col gap-3 md:flex-row md:items-end">
          <DateTimeInput v-model="startTime" label="Ora de start" />
          <AppButton :loading="saving === 'time'" :disabled="saving !== null" @click="saveTime">Salveaza ora</AppButton>
        </div>

        <div class="mt-4 flex flex-wrap gap-2">
          <AppLinkButton
            :to="{ name: 'league', params: { code: match.leagueCode } }"
            variant="outline"
          >
            Vezi liga
          </AppLinkButton>
          <AppLinkButton
            :to="{ name: 'team', params: { id: match.homeTeamId } }"
            variant="outline"
          >
            Vezi {{ match.homeTeamName }}
          </AppLinkButton>
          <AppLinkButton
            :to="{ name: 'team', params: { id: match.awayTeamId } }"
            variant="outline"
          >
            Vezi {{ match.awayTeamName }}
          </AppLinkButton>
        </div>
      </Panel>

      <Panel id="match-status" no-hover>
        <SectionHeader title="Stare meci" />
        <div class="space-y-3">
          <label class="text-sm text-muted">
            <span class="mb-1 block">Status</span>
            <select v-model="status" class="app-select-field app-select">
              <option v-for="option in statusOptions" :key="option" :value="option">{{ translateEnumLabel(option) }}</option>
            </select>
          </label>

          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label class="text-sm text-muted">
                <span class="mb-1 block">Scor gazde</span>
              <input v-model.number="ftHome" type="number" min="0" step="1" class="w-full rounded-lg border border-line bg-surface-2 px-3 py-2 text-fg" />
            </label>

            <label class="text-sm text-muted">
                <span class="mb-1 block">Scor oaspeti</span>
              <input v-model.number="ftAway" type="number" min="0" step="1" class="w-full rounded-lg border border-line bg-surface-2 px-3 py-2 text-fg" />
            </label>
          </div>

          <AppButton class="w-full" :loading="saving === 'status'" :disabled="saving !== null" @click="saveStatus">Salveaza status</AppButton>
        </div>
      </Panel>
    </div>

    <Panel id="match-offers" no-hover>
      <SectionHeader title="Cote si piete" />
      <MarketOffersGrid v-model="offers" />
      <div class="mt-4 flex flex-wrap gap-3">
        <AppButton :loading="saving === 'odds'" :disabled="saving !== null" @click="saveOdds">Salveaza cote</AppButton>
      </div>
    </Panel>

    <Panel id="match-offer-status" no-hover>
      <SectionHeader title="Activare oferte" />
      <div class="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-3">
        <button
          v-for="offer in offers"
          :key="offer.id ?? `${offer.marketType}-${offer.option}-${offer.line ?? 'null'}`"
          class="rounded-xl border px-4 py-3 text-left text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="saving !== null"
          :class="offer.active === false ? 'border-line bg-surface text-muted hover:bg-surface-2' : 'border-blue-500/20 bg-blue-500/10 text-blue-500 hover:bg-blue-500/15'"
          @click="offer.id && switchOfferActive(offer.id, !(offer.active ?? true))"
        >
          <p class="font-semibold">{{ translateEnumLabel(offer.marketType) }} - {{ translateEnumLabel(offer.option) }}</p>
          <p class="mt-1 text-xs opacity-75">
            {{ offer.active === false ? 'Oferta inactiva - apasa pentru activare' : 'Oferta activa - apasa pentru dezactivare' }}
          </p>
        </button>
      </div>
    </Panel>
  </div>

  <AppErrorState
    v-else-if="matchQuery.isError.value && !isNotFound"
    message="Nu am putut incarca meciul."
    :error="matchQuery.error.value"
    :retrying="matchQuery.isFetching.value"
    @retry="matchQuery.refetch()"
  />

  <EmptyState v-else message="Nu am gasit meciul cerut in admin." />
</template>




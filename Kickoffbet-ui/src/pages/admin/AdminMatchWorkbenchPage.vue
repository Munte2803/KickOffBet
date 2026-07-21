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
import type { EditableMarketOffer } from '@/types/match.types'
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

const route = useRoute()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const matchId = route.params.id as string

const statusOptions: MatchStatus[] = ['SCHEDULED', 'LIVE', 'FINISHED', 'POSTPONED', 'SUSPENDED', 'CANCELLED']

const startTime = ref('')
const status = ref<MatchStatus>('SCHEDULED')
const ftHome = ref<number | null>(null)
const ftAway = ref<number | null>(null)
const offers = ref<EditableMarketOffer[]>([])

const matchQuery = useQuery({
  queryKey: ['admin-match-workbench', matchId],
  queryFn: () => getMatchById(matchId),
})

const match = computed(() => matchQuery.data.value)

watch(
  () => match.value,
  (value) => {
    if (!value) return
    startTime.value = formatForInput(value.startTime)
    status.value = value.status
    ftHome.value = value.ftHome
    ftAway.value = value.ftAway
    offers.value = value.availableOffers.map((offer) => ({
      id: offer.id,
      marketType: offer.marketType,
      option: offer.option,
      line: offer.line,
      odds: offer.odds,
      active: offer.active,
    }))
  },
  { immediate: true },
)

async function saveTime() {
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

  try {
    await rescheduleMatch(matchId, startTime.value)
    await matchQuery.refetch()
    toastStore.showSuccess('Ora meciului a fost actualizata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza ora meciului.')
  }
}

async function saveStatus() {
  if (status.value === 'FINISHED' && ((ftHome.value ?? 0) < 0 || (ftAway.value ?? 0) < 0)) {
    toastStore.showError('Scorul nu poate fi negativ.')
    return
  }

  const confirmed = await confirm({
    title: 'Salvare status meci',
    message: 'Sunteti sigur ca vreti sa salvati statusul si scorul meciului?',
    description: 'Modificarile vor afecta imediat afisarea meciului pentru utilizatori.',
    confirmLabel: 'Salveaza statusul',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  try {
    await updateMatchStatus(matchId, {
      status: status.value,
      ftHome: ftHome.value,
      ftAway: ftAway.value,
    })
    await matchQuery.refetch()
    toastStore.showSuccess('Statusul meciului a fost actualizat.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut actualiza statusul meciului.')
  }
}

async function saveOdds() {
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

  try {
    await updateMatch(matchId, {
      availableOffers: offers.value
        .filter((offer): offer is EditableMarketOffer & { id: string } => Boolean(offer.id))
        .map((offer) => ({
          id: offer.id,
          odds: offer.odds,
        })),
    })
    await matchQuery.refetch()
    toastStore.showSuccess('Cotele au fost actualizate.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut salva cotele.')
  }
}

async function switchMatchActive() {
  if (!match.value) return
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

  try {
    await toggleMatchStatus(matchId, nextActive)
    await matchQuery.refetch()
    toastStore.showSuccess(nextActive ? 'Meciul a fost activat.' : 'Meciul a fost dezactivat.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea meciului.')
  }
}

async function switchOfferActive(offerId: string, active: boolean) {
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

  try {
    await toggleOfferStatus(matchId, offerId, active)
    await matchQuery.refetch()
    toastStore.showSuccess('Starea ofertei a fost schimbata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut schimba starea ofertei.')
  }
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
        <AppButton variant="outline" @click="switchMatchActive">
          {{ match.active ? 'Dezactiveaza meciul' : 'Activeaza meciul' }}
        </AppButton>
      </template>
    </PageHeader>

    <section id="match-overview" class="grid gap-4 md:grid-cols-3">
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

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <Panel id="match-schedule" no-hover>
        <SectionHeader title="Programare" />
        <div class="flex flex-col gap-3 md:flex-row md:items-end">
          <DateTimeInput v-model="startTime" label="Ora de start" />
          <AppButton @click="saveTime">Salveaza ora</AppButton>
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

          <div class="grid gap-3 sm:grid-cols-2">
            <label class="text-sm text-muted">
                <span class="mb-1 block">Scor gazde</span>
              <input v-model.number="ftHome" type="number" min="0" step="1" class="w-full rounded-lg border border-line bg-surface-2 px-3 py-2 text-fg" />
            </label>

            <label class="text-sm text-muted">
                <span class="mb-1 block">Scor oaspeti</span>
              <input v-model.number="ftAway" type="number" min="0" step="1" class="w-full rounded-lg border border-line bg-surface-2 px-3 py-2 text-fg" />
            </label>
          </div>

          <AppButton class="w-full" @click="saveStatus">Salveaza status</AppButton>
        </div>
      </Panel>
    </div>

    <Panel id="match-offers" no-hover>
      <SectionHeader title="Cote si piete" />
      <MarketOffersGrid v-model="offers" />
      <div class="mt-4 flex flex-wrap gap-3">
        <AppButton @click="saveOdds">Salveaza cote</AppButton>
      </div>
    </Panel>

    <Panel id="match-offer-status" no-hover>
      <SectionHeader title="Activare oferte" />
      <div class="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        <button
          v-for="offer in offers"
          :key="offer.id ?? `${offer.marketType}-${offer.option}-${offer.line ?? 'null'}`"
          class="rounded-xl border px-4 py-3 text-left text-sm transition-colors"
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

  <EmptyState v-else message="Nu am gasit meciul cerut in admin." />
</template>




<script setup lang="ts">
import { watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { cancelTicket, getTicket } from '@/api/tickets.api'
import { getProfile } from '@/api/profile.api'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import { useAuthStore } from '@/stores/auth.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { translateEnumLabel } from '@/utils/labels.utils'

const route = useRoute()
const toastStore = useToastStore()
const authStore = useAuthStore()
const { confirm } = useConfirmDialog()
const ticketId = route.params.id as string

const ticketQuery = useQuery({
  queryKey: ['ticket-detail', ticketId],
  queryFn: () => getTicket(ticketId),
})

watch(() => ticketQuery.data.value, (ticket) => {
  if (ticket && ticket.status === 'WON') {
    getProfile().then(profile => authStore.hydrateProfile(profile)).catch(() => {})
  }
}, { immediate: true })

async function handleCancel() {
  const confirmed = await confirm({
    title: 'Anulare bilet',
    message: 'Sunteti sigur ca vreti sa anulati biletul?',
    description: 'Actiunea este disponibila doar pentru biletele care sunt inca in asteptare.',
    confirmLabel: 'Anuleaza biletul',
    cancelLabel: 'Pastreaza biletul',
    variant: 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await cancelTicket(ticketId)
    await ticketQuery.refetch()
    try {
      const profile = await getProfile()
      authStore.hydrateProfile(profile)
    } catch {

    }
    toastStore.showSuccess('Biletul a fost anulat.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut anula biletul.')
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Detaliu bilet" :back-route="{ name: 'tickets' }" />

    <Panel v-if="ticketQuery.data.value">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="ticketQuery.data.value.status" />
            <span class="text-sm text-gray-400">{{ formatDateShort(ticketQuery.data.value.createdAt) }}</span>
          </div>
          <p class="mt-2 font-mono text-xs text-gray-500">ID bilet: {{ ticketQuery.data.value.id }}</p>
        </div>

        <div class="text-right">
          <p class="text-sm text-gray-400">Castig potential</p>
          <p class="text-xl font-black text-white">{{ formatMoney(ticketQuery.data.value.potentialPayout) }}</p>
        </div>
      </div>

      <div class="mt-6 space-y-3">
        <div
          v-for="selection in ticketQuery.data.value.selections"
          :key="selection.id"
          class="rounded-xl border border-white/10 bg-black/40 p-2.5 sm:p-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <RouterLink
                :to="{ name: 'match-detail', params: { id: selection.matchId } }"
                class="text-sm font-semibold text-white transition hover:text-amber-300"
              >
                {{ selection.homeTeamName }} vs {{ selection.awayTeamName }}
              </RouterLink>
              <p class="mt-1 text-xs text-gray-400">
              {{ translateEnumLabel(selection.marketType) }} ·
                {{ formatBetLabel(selection.selectedOption, selection.marketType, selection.line) }}
              </p>
            </div>
            <StatusBadge :status="selection.status" />
          </div>
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <AppButton
          v-if="ticketQuery.data.value.status === 'PENDING'"
          variant="danger"
          @click="handleCancel"
        >
          Anuleaza biletul
        </AppButton>
      </div>
    </Panel>
  </div>
</template>

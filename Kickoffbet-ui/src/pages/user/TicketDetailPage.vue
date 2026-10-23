<script setup lang="ts">
import { watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { cancelTicket, getTicket } from '@/api/tickets.api'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import { useAuthStore } from '@/stores/auth.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import AppErrorState from '@/components/AppErrorState.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { translateEnumLabel } from '@/utils/labels.utils'
import { queryKeys } from '@/query/query-keys'

const route = useRoute()
const toastStore = useToastStore()
const authStore = useAuthStore()
const { confirm } = useConfirmDialog()
const ticketId = route.params.id as string

const ticketQuery = useQuery({
  queryKey: queryKeys.ticketDetail(ticketId),
  queryFn: () => getTicket(ticketId),
})

watch(() => ticketQuery.data.value, (ticket) => {
  if (ticket && ticket.status === 'WON') {
    // A won ticket was paid out: show the new balance (rate-limited and shared with other refreshes)
    authStore.refreshProfile().catch(() => {})
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
    toastStore.showSuccess('Biletul a fost anulat.')
    // The refund changes the ticket lists, the wallet history and the balance (on failure the
    // balance is refreshed again by the profile query / layout)
    await authStore.refreshAccountAfterMutation()
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut anula biletul.')
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Detaliu bilet" :back-route="{ name: 'tickets' }" />

    <LoadingState v-if="ticketQuery.isLoading.value" message="Se incarca biletul..." />

    <AppErrorState
      v-else-if="ticketQuery.isError.value && !ticketQuery.data.value"
      message="Nu am putut incarca biletul."
      :error="ticketQuery.error.value"
      :retrying="ticketQuery.isFetching.value"
      @retry="ticketQuery.refetch()"
    />

    <Panel v-else-if="ticketQuery.data.value">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="ticketQuery.data.value.status" />
            <span class="text-sm text-muted">{{ formatDateShort(ticketQuery.data.value.createdAt) }}</span>
          </div>
          <p class="mt-2 font-mono text-xs text-subtle">ID bilet: {{ ticketQuery.data.value.id }}</p>
        </div>

        <div class="text-right">
          <p class="text-sm text-muted">Castig potential</p>
          <p class="text-xl font-black text-fg">{{ formatMoney(ticketQuery.data.value.potentialPayout) }}</p>
        </div>
      </div>

      <div class="mt-6 space-y-3">
        <div
          v-for="selection in ticketQuery.data.value.selections"
          :key="selection.id"
          class="rounded-xl border border-line bg-surface p-2.5 sm:p-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <RouterLink
                :to="{ name: 'match-detail', params: { id: selection.matchId } }"
                class="text-sm font-semibold text-fg transition hover:text-amber-500"
              >
                {{ selection.homeTeamName }} vs {{ selection.awayTeamName }}
              </RouterLink>
              <p class="mt-1 text-xs text-muted">
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

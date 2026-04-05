<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTicketById } from '@/api/tickets.admin.api'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { translateEnumLabel } from '@/utils/labels.utils'

const route = useRoute()
const ticketId = route.params.id as string

const ticketQuery = useQuery({
  queryKey: ['admin-ticket-route-detail', ticketId],
  queryFn: () => getTicketById(ticketId),
})
</script>

<template>
  <LoadingState v-if="ticketQuery.isLoading.value" message="Se incarca biletul..." />

  <div v-else-if="ticketQuery.data.value" class="space-y-6">
    <PageHeader
      title="Administrare - detaliu bilet"
      :subtitle="formatDateShort(ticketQuery.data.value.createdAt)"
      :back-route="{ name: 'admin-tickets' }"
    />

    <Panel no-hover>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="ticketQuery.data.value.status" />
            <span class="text-sm text-gray-400">{{ formatDateShort(ticketQuery.data.value.createdAt) }}</span>
          </div>
          <RouterLink
            :to="{ name: 'admin-user-detail', params: { id: ticketQuery.data.value.userId } }"
            class="mt-2 inline-flex max-w-full flex-col text-left text-sm font-medium text-blue-300 transition-colors hover:text-blue-200"
          >
            <span class="truncate">{{ ticketQuery.data.value.userEmail }}</span>
            <span class="mt-2 font-mono text-xs text-gray-500">{{ ticketQuery.data.value.userId }}</span>
          </RouterLink>
            <p class="mt-2 font-mono text-xs text-gray-500">ID bilet: {{ ticketQuery.data.value.id }}</p>
        </div>

        <div class="text-right">
          <p class="text-sm text-gray-400">Miza / castig potential</p>
          <p class="text-xl font-black text-white">
            {{ formatMoney(ticketQuery.data.value.stake) }} / {{ formatMoney(ticketQuery.data.value.potentialPayout) }}
          </p>
        </div>
      </div>

      <div class="mt-6 space-y-3">
        <div
          v-for="selection in ticketQuery.data.value.selections"
          :key="selection.id"
          class="rounded-xl border border-white/10 bg-black/40 p-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-white">{{ selection.homeTeamName }} vs {{ selection.awayTeamName }}</p>
              <p class="mt-1 text-xs text-gray-400">
              {{ translateEnumLabel(selection.marketType) }} ·
                {{ formatBetLabel(selection.selectedOption, selection.marketType, selection.line) }}
              </p>
            </div>
            <StatusBadge :status="selection.status" />
          </div>
        </div>
      </div>
    </Panel>
  </div>

  <EmptyState v-else message="Nu am gasit biletul cerut in admin." />
</template>


<script setup lang="ts">
import { ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import { getUserTickets } from '@/api/tickets.api'
import { usePagination } from '@/composables/usePagination'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import TabNav from '@/components/TabNav.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppPagination from '@/components/AppPagination.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import type { TicketStatus } from '@/types/enums'

const status = ref<TicketStatus>('PENDING')
const pagination = usePagination(10)

const ticketsQuery = useQuery({
  queryKey: ['tickets', status, pagination.request],
  queryFn: () => getUserTickets(status.value, pagination.request.value),
})

watch([status, () => pagination.page.value], () => {
  ticketsQuery.refetch()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Biletele mele" />

    <Panel>
      <TabNav
        :tabs="[
          { key: 'PENDING', label: 'In asteptare' },
          { key: 'WON', label: 'Castigate' },
          { key: 'LOST', label: 'Pierdute' },
          { key: 'CANCELLED', label: 'Anulate' },
        ]"
        :active="status"
        @change="status = $event as TicketStatus; pagination.reset()"
      />

      <div class="mt-4 space-y-3">
        <EmptyState v-if="!(ticketsQuery.data.value?.content?.length ?? 0)" message="Nu exista bilete in aceasta categorie." />
        <RouterLink
          v-for="ticket in ticketsQuery.data.value?.content ?? []"
          :key="ticket.id"
          :to="{ name: 'ticket-detail', params: { id: ticket.id } }"
          class="block rounded-xl border border-white/10 bg-black/40 p-3 transition-colors hover:border-blue-700 sm:p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <StatusBadge :status="ticket.status" />
              <span class="text-xs text-gray-400">{{ formatDateShort(ticket.createdAt) }}</span>
            </div>
            <div class="text-right">
              <p class="text-sm font-semibold text-white">{{ formatMoney(ticket.potentialPayout) }}</p>
              <p class="text-xs text-gray-400">cota {{ ticket.totalOdd.toFixed(2) }}</p>
            </div>
          </div>
          <p class="mt-3 text-sm text-gray-300">{{ ticket.selections.length }} selectii</p>
        </RouterLink>
      </div>

      <AppPagination
        v-if="(ticketsQuery.data.value?.totalPages ?? 0) > 1"
        :page="ticketsQuery.data.value?.number ?? 0"
        :total-pages="ticketsQuery.data.value?.totalPages ?? 0"
        @change="pagination.setPage"
      />
    </Panel>
  </div>
</template>


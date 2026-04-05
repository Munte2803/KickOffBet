<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTicketsByStatus, getUserTickets } from '@/api/tickets.admin.api'
import { usePagination } from '@/composables/usePagination'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppPagination from '@/components/AppPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AdminUserSearchPicker from '@/components/AdminUserSearchPicker.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import type { TicketStatus } from '@/types/enums'
import { useToastStore } from '@/stores/toast.store'
import { translateEnumLabel } from '@/utils/labels.utils'

const toastStore = useToastStore()
const statusPagination = usePagination(12)
const userTicketsPagination = usePagination(10)
const status = ref<TicketStatus>('PENDING')
const selectedHistoryUserId = ref('')
const sortBy = ref('createdAt,desc')
const statusPageRequest = computed(() => ({
  ...statusPagination.request.value,
  sort: sortBy.value,
}))
const userHistoryPageRequest = computed(() => ({
  ...userTicketsPagination.request.value,
  sort: sortBy.value,
}))

const sortOptions = [
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
  { label: 'Miza descrescator', value: 'stake,desc' },
  { label: 'Miza crescator', value: 'stake,asc' },
  { label: 'Cota totala descrescator', value: 'totalOdd,desc' },
  { label: 'Cota totala crescator', value: 'totalOdd,asc' },
  { label: 'Castig potential descrescator', value: 'potentialPayout,desc' },
  { label: 'Castig potential crescator', value: 'potentialPayout,asc' },
]

const ticketsQuery = useQuery({
  queryKey: ['admin-tickets', status, statusPageRequest],
  queryFn: () => getTicketsByStatus(status.value, statusPageRequest.value),
})

const userTicketsQuery = useQuery({
  queryKey: ['admin-tickets-user-history', selectedHistoryUserId, userHistoryPageRequest],
  queryFn: () => getUserTickets(selectedHistoryUserId.value, userHistoryPageRequest.value),
  enabled: false,
})

watch([status, sortBy], () => {
  statusPagination.reset()
  ticketsQuery.refetch()
})

watch(() => statusPagination.page.value, () => {
  ticketsQuery.refetch()
})

watch(() => userTicketsPagination.page.value, () => {
  if (selectedHistoryUserId.value) {
    userTicketsQuery.refetch()
  }
})

async function loadUserHistory() {
  if (!selectedHistoryUserId.value) {
    toastStore.showError('Selecteaza utilizatorul pentru biletele lui.')
    return
  }

  if (userTicketsPagination.page.value !== 0) {
    userTicketsPagination.reset()
    return
  }

  await userTicketsQuery.refetch()
}

async function handleHistoryUserSelect() {
  if (!selectedHistoryUserId.value) {
    return
  }

  await loadUserHistory()
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - bilete" subtitle="Lista dupa status si istoric pe utilizator ales dupa email." />

    <Panel id="ticket-status" no-hover>
      <h2 class="text-lg font-semibold text-white">Lista dupa status</h2>

      <div class="mt-4 max-w-xs">
        <div class="grid gap-3">
          <label class="text-sm text-gray-300">
          <span class="mb-1 block">Status</span>
          <select v-model="status" class="app-select-field app-select">
              <option :value="'PENDING'">{{ translateEnumLabel('PENDING') }}</option>
              <option :value="'WON'">{{ translateEnumLabel('WON') }}</option>
              <option :value="'LOST'">{{ translateEnumLabel('LOST') }}</option>
              <option :value="'CANCELLED'">{{ translateEnumLabel('CANCELLED') }}</option>
            </select>
          </label>
          <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
        </div>
      </div>

      <div class="mt-4 space-y-3">
        <div
          v-for="ticket in ticketsQuery.data.value?.content ?? []"
          :key="ticket.id"
          class="rounded-xl border border-white/10 bg-black/40 p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <StatusBadge :status="ticket.status" />
              <span class="text-xs text-gray-400">{{ formatDateShort(ticket.createdAt) }}</span>
            </div>
            <div class="text-right">
              <p class="text-sm font-semibold text-white">{{ formatMoney(ticket.potentialPayout) }}</p>
              <p class="text-xs text-gray-400">miza {{ formatMoney(ticket.stake) }} - {{ ticket.selections.length }} selectie(i)</p>
            </div>
          </div>

          <div class="mt-3 flex justify-start">
            <RouterLink
              :to="{ name: 'admin-ticket-detail', params: { id: ticket.id } }"
              class="text-xs font-semibold text-blue-300 transition-colors hover:text-blue-200"
              @click.stop
            >
              Deschide detaliul complet
            </RouterLink>
          </div>
        </div>
      </div>

      <EmptyState v-if="!(ticketsQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Nu exista bilete pentru statusul selectat." />

      <AppPagination
        v-if="(ticketsQuery.data.value?.totalPages ?? 0) > 1"
        :page="ticketsQuery.data.value?.number ?? 0"
        :total-pages="ticketsQuery.data.value?.totalPages ?? 0"
        @change="statusPagination.setPage"
      />
    </Panel>

    <Panel id="ticket-user-history" no-hover>
        <h2 class="text-lg font-semibold text-white">Bilete dupa utilizator</h2>

        <div class="mt-4 space-y-3">
          <AdminUserSearchPicker
            v-model="selectedHistoryUserId"
            title="Utilizator pentru istoricul biletelor"
            placeholder="Cauta utilizatorul dupa email"
            results-title="Rezultate utilizatori"
            @select="handleHistoryUserSelect"
          />
        </div>

        <div class="mt-4 space-y-3">
          <div
            v-for="ticket in userTicketsQuery.data.value?.content ?? []"
            :key="ticket.id"
            class="rounded-xl border border-white/10 bg-black/40 p-4"
          >
            <div class="flex items-center justify-between gap-3">
              <StatusBadge :status="ticket.status" />
              <span class="text-sm font-semibold text-white">{{ formatMoney(ticket.potentialPayout) }}</span>
            </div>
            <p class="mt-2 text-xs text-gray-400">{{ formatDateShort(ticket.createdAt) }}</p>
            <div class="mt-3 flex justify-start">
              <RouterLink
                :to="{ name: 'admin-ticket-detail', params: { id: ticket.id } }"
                class="text-xs font-semibold text-blue-300 transition-colors hover:text-blue-200"
                @click.stop
              >
                Deschide detaliul complet
              </RouterLink>
            </div>
          </div>
        </div>

        <EmptyState
          v-if="selectedHistoryUserId && !(userTicketsQuery.data.value?.content?.length ?? 0)"
          class="mt-4"
          message="Utilizatorul nu are bilete in istoricul solicitat."
        />

        <AppPagination
          v-if="(userTicketsQuery.data.value?.totalPages ?? 0) > 1"
          :page="userTicketsQuery.data.value?.number ?? 0"
          :total-pages="userTicketsQuery.data.value?.totalPages ?? 0"
          @change="userTicketsPagination.setPage"
        />
    </Panel>
  </div>
</template>


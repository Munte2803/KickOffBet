<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTicketsByStatus, getUserTickets } from '@/api/tickets.admin.api'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppPagination from '@/components/AppPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AdminUserSearchPicker from '@/components/AdminUserSearchPicker.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import ExportButton from '@/components/ExportButton.vue'
import TimeSeriesChart from '@/components/TimeSeriesChart.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import DatePresetButtons from '@/components/DatePresetButtons.vue'
import type { ExportColumn } from '@/utils/export.utils'
import type { Ticket } from '@/types/ticket.types'
import { getTicketsTimeSeries } from '@/api/admin.timeseries.api'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import { useTimeSeriesChart } from '@/composables/useTimeSeriesChart'
import type { TicketStatus } from '@/types/enums'
import { useToastStore } from '@/stores/toast.store'
import { translateEnumLabel } from '@/utils/labels.utils'

const toastStore = useToastStore()
const statusPagination = usePagination(PAGE_SIZES.SMALL)
const userTicketsPagination = usePagination(PAGE_SIZES.COMPACT)
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

const exportColumns: ExportColumn<Ticket>[] = [
  { header: 'ID', accessor: (r) => r.id },
  { header: 'Email', accessor: (r) => r.userEmail },
  { header: 'Status', accessor: (r) => translateEnumLabel(r.status) },
  { header: 'Miza', accessor: (r) => r.stake },
  { header: 'Cota totala', accessor: (r) => r.totalOdd },
  { header: 'Castig potential', accessor: (r) => r.potentialPayout },
  { header: 'Selectii', accessor: (r) => r.selections.length },
  { header: 'Creat la', accessor: (r) => r.createdAt },
]

async function fetchAllTicketsForExport(): Promise<Ticket[]> {
  const data = await getTicketsByStatus(status.value, { page: 0, size: 10000, sort: sortBy.value })
  return data.content
}

const trendChartRef = ref<InstanceType<typeof TimeSeriesChart> | null>(null)
const chartStatus = ref<TicketStatus | ''>('')
const chartMetric = ref<'count' | 'totalAmount'>('count')
const { chartStart, chartEnd, chartData, chartLoading, loadChart } = useTimeSeriesChart(
  (start, end) => getTicketsTimeSeries(start, end, chartStatus.value || undefined),
)
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - bilete" subtitle="Lista dupa status si istoric pe utilizator ales dupa email." />

    <Panel id="ticket-trend" no-hover>
      <h2 class="text-lg font-semibold text-fg">Evolutie in timp</h2>

      <div class="mt-4 grid items-end gap-3 md:grid-cols-4">
        <FormInput v-model="chartStart" label="Data inceput" type="datetime-local" />
        <FormInput v-model="chartEnd" label="Data sfarsit" type="datetime-local" />
        <label class="text-sm text-muted">
          <span class="mb-1 block">Status</span>
          <select v-model="chartStatus" class="app-select-field app-select">
            <option value="">Toate</option>
            <option value="PENDING">{{ translateEnumLabel('PENDING') }}</option>
            <option value="WON">{{ translateEnumLabel('WON') }}</option>
            <option value="LOST">{{ translateEnumLabel('LOST') }}</option>
            <option value="CANCELLED">{{ translateEnumLabel('CANCELLED') }}</option>
          </select>
        </label>
        <label class="text-sm text-muted">
          <span class="mb-1 block">Metrica</span>
          <select v-model="chartMetric" class="app-select-field app-select">
            <option value="count">Numar bilete</option>
            <option value="totalAmount">Total miza</option>
          </select>
        </label>
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <DatePresetButtons @select="(s, e) => { chartStart = s; chartEnd = e }" />
        <div class="flex flex-wrap gap-2">
          <AppButton :loading="chartLoading" @click="loadChart">Incarca graficul</AppButton>
          <AppButton variant="outline" :disabled="!chartData.length" @click="trendChartRef?.exportPng(`bilete-${(chartStatus || 'toate').toLowerCase()}-grafic`)">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v12m0 0l-4-4m4 4l4-4M4 20h16" />
            </svg>
            <span>Descarca PNG</span>
          </AppButton>
        </div>
      </div>

      <div class="mt-6">
        <TimeSeriesChart
          ref="trendChartRef"
          :data="chartData"
          :metric="chartMetric"
          :label="chartMetric === 'totalAmount' ? 'Total miza' : 'Numar bilete'"
          color="#10b981"
        />
      </div>
    </Panel>

    <Panel id="ticket-status" no-hover>
      <div class="flex items-center justify-between gap-3">
        <h2 class="text-lg font-semibold text-fg">Lista dupa status</h2>
        <ExportButton
          :fetch-all="fetchAllTicketsForExport"
          :columns="exportColumns"
          filename="bilete"
          title="Bilete"
          :disabled="!(ticketsQuery.data.value?.content?.length ?? 0)"
        />
      </div>

      <div class="mt-4 max-w-xs">
        <div class="grid gap-3">
          <label class="text-sm text-muted">
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

      <Transition name="page-fade" mode="out-in" appear>
      <div :key="ticketsQuery.data.value?.number ?? 0" class="mt-4 space-y-3">
        <div
          v-for="ticket in ticketsQuery.data.value?.content ?? []"
          :key="ticket.id"
          class="rounded-xl border border-line bg-surface p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <StatusBadge :status="ticket.status" />
              <span class="text-xs text-muted">{{ formatDateShort(ticket.createdAt) }}</span>
            </div>
            <div class="text-right">
              <p class="text-sm font-semibold text-fg">{{ formatMoney(ticket.potentialPayout) }}</p>
              <p class="text-xs text-muted">miza {{ formatMoney(ticket.stake) }} - {{ ticket.selections.length }} selectie(i)</p>
            </div>
          </div>

          <div class="mt-3 flex justify-start">
            <RouterLink
              :to="{ name: 'admin-ticket-detail', params: { id: ticket.id } }"
              class="text-xs font-semibold text-blue-500 transition-colors hover:text-blue-500"
              @click.stop
            >
              Deschide detaliul complet
            </RouterLink>
          </div>
        </div>

        <EmptyState v-if="!(ticketsQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Nu exista bilete pentru statusul selectat." />
      </div>
      </Transition>

      <AppPagination
        v-if="(ticketsQuery.data.value?.totalElements ?? 0) > 0"
        :page="ticketsQuery.data.value?.number ?? 0"
        :total-pages="ticketsQuery.data.value?.totalPages ?? 0"
        :total-elements="ticketsQuery.data.value?.totalElements"
        @change="statusPagination.setPage"
      />
    </Panel>

    <Panel id="ticket-user-history" no-hover>
        <h2 class="text-lg font-semibold text-fg">Bilete dupa utilizator</h2>

        <div class="mt-4 space-y-3">
          <AdminUserSearchPicker
            v-model="selectedHistoryUserId"
            title="Utilizator pentru istoricul biletelor"
            placeholder="Cauta utilizatorul dupa email"
            results-title="Rezultate utilizatori"
            @select="handleHistoryUserSelect"
          />
        </div>

        <Transition name="page-fade" mode="out-in" appear>
          <div
            v-if="userTicketsQuery.data.value?.content?.length"
            :key="userTicketsQuery.data.value?.number ?? 0"
            class="mt-4 space-y-3"
          >
            <div
              v-for="ticket in userTicketsQuery.data.value?.content ?? []"
              :key="ticket.id"
              class="rounded-xl border border-line bg-surface p-4"
            >
              <div class="flex items-center justify-between gap-3">
                <StatusBadge :status="ticket.status" />
                <span class="text-sm font-semibold text-fg">{{ formatMoney(ticket.potentialPayout) }}</span>
              </div>
              <p class="mt-2 text-xs text-muted">{{ formatDateShort(ticket.createdAt) }}</p>
              <div class="mt-3 flex justify-start">
                <RouterLink
                  :to="{ name: 'admin-ticket-detail', params: { id: ticket.id } }"
                  class="text-xs font-semibold text-blue-500 transition-colors hover:text-blue-500"
                  @click.stop
                >
                  Deschide detaliul complet
                </RouterLink>
              </div>
            </div>
          </div>
        </Transition>

        <EmptyState
          v-if="selectedHistoryUserId && !(userTicketsQuery.data.value?.content?.length ?? 0)"
          class="mt-4"
          message="Utilizatorul nu are bilete in istoricul solicitat."
        />

        <AppPagination
          v-if="selectedHistoryUserId && (userTicketsQuery.data.value?.totalElements ?? 0) > 0"
          :page="userTicketsQuery.data.value?.number ?? 0"
          :total-pages="userTicketsQuery.data.value?.totalPages ?? 0"
          :total-elements="userTicketsQuery.data.value?.totalElements"
          @change="userTicketsPagination.setPage"
        />
    </Panel>
  </div>
</template>


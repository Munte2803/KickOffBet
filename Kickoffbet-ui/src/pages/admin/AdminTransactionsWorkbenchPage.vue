<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  getPendingTransactions,
  getTopDepositors,
  getTransactionReport,
  getUserTransactionSummary,
  getUserTransactions,
  searchTransactions,
} from '@/api/transactions.admin.api'
import type { TransactionReport, UserDepositSummary, UserTransactionSummary } from '@/types/transaction.types'
import type { TransactionStatus, TransactionType } from '@/types/enums'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import FormInput from '@/components/FormInput.vue'
import AppPagination from '@/components/AppPagination.vue'
import TransactionRow from '@/components/TransactionRow.vue'
import StatCard from '@/components/StatCard.vue'
import DatePresetButtons from '@/components/DatePresetButtons.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AdminUserSearchPicker from '@/components/AdminUserSearchPicker.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import ExportButton from '@/components/ExportButton.vue'
import TimeSeriesChart from '@/components/TimeSeriesChart.vue'
import type { ExportColumn } from '@/utils/export.utils'
import type { Transaction } from '@/types/transaction.types'
import { getTransactionsTimeSeries } from '@/api/admin.timeseries.api'
import { formatMoney } from '@/utils/money.utils'
import { inputToUtcApiDate } from '@/utils/date.utils'
import { useTimeSeriesChart } from '@/composables/useTimeSeriesChart'
import { translateEnumLabel } from '@/utils/labels.utils'

const toastStore = useToastStore()
const searchPagination = usePagination(PAGE_SIZES.SMALL)
const userTransactionsPagination = usePagination(PAGE_SIZES.COMPACT)
const pendingPagination = usePagination(PAGE_SIZES.COMPACT)

const transactionType = ref<TransactionType | ''>('')
const transactionStatus = ref<TransactionStatus | ''>('')
const selectedSearchUserId = ref('')
const startDate = ref('')
const endDate = ref('')
const minAmount = ref('')
const maxAmount = ref('')
const sortBy = ref('createdAt,desc')
const hasSearched = ref(false)
const appliedSearchFilters = ref({
  userId: undefined as string | undefined,
  transactionType: undefined as TransactionType | undefined,
  transactionStatus: undefined as TransactionStatus | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  minAmount: undefined as number | undefined,
  maxAmount: undefined as number | undefined,
})

const reportStart = ref('')
const reportEnd = ref('')
const report = ref<TransactionReport | null>(null)
const topDepositors = ref<UserDepositSummary[]>([])
const loadingReport = ref(false)

const selectedSummaryUserId = ref('')
const summaryStart = ref('')
const summaryEnd = ref('')
const userSummary = ref<UserTransactionSummary | null>(null)
const summaryLoading = ref(false)

const selectedHistoryUserId = ref('')
const searchPageRequest = computed(() => ({
  ...searchPagination.request.value,
  sort: sortBy.value,
}))
const userHistoryPageRequest = computed(() => ({
  ...userTransactionsPagination.request.value,
  sort: sortBy.value,
}))

const sortOptions = [
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
  { label: 'Suma descrescator', value: 'amount,desc' },
  { label: 'Suma crescator', value: 'amount,asc' },
  { label: 'Tip A-Z', value: 'transactionType,asc' },
  { label: 'Tip Z-A', value: 'transactionType,desc' },
  { label: 'Status A-Z', value: 'status,asc' },
  { label: 'Status Z-A', value: 'status,desc' },
]

function parseAmountFilter(value: string) {
  if (!value.trim()) return undefined

  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return null

  return parsed
}

function validateAmountFilters() {
  const parsedMin = parseAmountFilter(minAmount.value)
  const parsedMax = parseAmountFilter(maxAmount.value)

  if (parsedMin === null || parsedMax === null) {
    toastStore.showError('Sumele filtrate trebuie sa fie valori pozitive sau zero.')
    return null
  }

  if (parsedMin !== undefined && parsedMax !== undefined && parsedMin > parsedMax) {
    toastStore.showError('Suma minima nu poate fi mai mare decat suma maxima.')
    return null
  }

  return { parsedMin, parsedMax }
}

const searchQuery = useQuery({
  queryKey: ['admin-transaction-search', appliedSearchFilters, searchPageRequest],
  queryFn: async () => {
    return searchTransactions(appliedSearchFilters.value, searchPageRequest.value)
  },
  enabled: computed(() => hasSearched.value),
})

const pendingPageRequest = computed(() => ({
  ...pendingPagination.request.value,
  sort: sortBy.value,
}))

const pendingQuery = useQuery({
  queryKey: ['admin-transaction-pending', sortBy, pendingPageRequest],
  queryFn: () => getPendingTransactions(pendingPageRequest.value),
})

const userTransactionsQuery = useQuery({
  queryKey: ['admin-transaction-user-history', selectedHistoryUserId, userHistoryPageRequest],
  queryFn: () => getUserTransactions(selectedHistoryUserId.value, userHistoryPageRequest.value),
  enabled: false,
})

watch(sortBy, () => {
  if (hasSearched.value) {
    searchPagination.reset()
    searchQuery.refetch()
  }
  pendingPagination.reset()
  pendingQuery.refetch()
})

watch(() => searchPagination.page.value, () => {
  if (hasSearched.value) {
    searchQuery.refetch()
  }
})

watch(() => userTransactionsPagination.page.value, () => {
  if (selectedHistoryUserId.value) {
    userTransactionsQuery.refetch()
  }
})

async function loadReport() {
  if (!reportStart.value || !reportEnd.value) {
    toastStore.showError('Selecteaza intervalul pentru raport.')
    return
  }

  loadingReport.value = true
  try {
    const utcStart = inputToUtcApiDate(reportStart.value) ?? reportStart.value
    const utcEnd = inputToUtcApiDate(reportEnd.value) ?? reportEnd.value
    const [reportData, topDepositorsData] = await Promise.all([
      getTransactionReport(utcStart, utcEnd),
      getTopDepositors(utcStart, utcEnd, { page: 0, size: 5 }),
    ])
    report.value = reportData
    topDepositors.value = topDepositorsData.content
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca raportul.')
  } finally {
    loadingReport.value = false
  }
}

async function loadUserSummary() {
  if (!selectedSummaryUserId.value || !summaryStart.value || !summaryEnd.value) {
    toastStore.showError('Selecteaza utilizatorul si intervalul pentru sumar.')
    return
  }

  summaryLoading.value = true
  try {
    userSummary.value = await getUserTransactionSummary(
      selectedSummaryUserId.value,
      inputToUtcApiDate(summaryStart.value) ?? summaryStart.value,
      inputToUtcApiDate(summaryEnd.value) ?? summaryEnd.value,
    )
  } catch (error) {
    userSummary.value = null
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca sumarul utilizatorului.')
  } finally {
    summaryLoading.value = false
  }
}

async function loadUserHistory() {
  if (!selectedHistoryUserId.value) {
    toastStore.showError('Selecteaza utilizatorul pentru istoric.')
    return
  }

  if (userTransactionsPagination.page.value !== 0) {
    userTransactionsPagination.reset()
    return
  }

  await userTransactionsQuery.refetch()
}

async function handleHistoryUserSelect() {
  if (!selectedHistoryUserId.value) {
    return
  }

  await loadUserHistory()
}

async function applySearchFilters() {
  const amountFilters = validateAmountFilters()

  if (!amountFilters) {
    return
  }

  appliedSearchFilters.value = {
    userId: selectedSearchUserId.value || undefined,
    transactionType: transactionType.value || undefined,
    transactionStatus: transactionStatus.value || undefined,
    startDate: inputToUtcApiDate(startDate.value) || undefined,
    endDate: inputToUtcApiDate(endDate.value) || undefined,
    minAmount: amountFilters.parsedMin,
    maxAmount: amountFilters.parsedMax,
  }
  hasSearched.value = true

  if (searchPagination.page.value !== 0) {
    searchPagination.reset()
    return
  }

  await searchQuery.refetch()
}

const exportColumns: ExportColumn<Transaction>[] = [
  { header: 'ID', accessor: (r) => r.id },
  { header: 'Email', accessor: (r) => r.userEmail },
  { header: 'Nume', accessor: (r) => `${r.userFirstName} ${r.userLastName}` },
  { header: 'Tip', accessor: (r) => translateEnumLabel(r.transactionType) },
  { header: 'Status', accessor: (r) => translateEnumLabel(r.status) },
  { header: 'Suma', accessor: (r) => r.amount },
  { header: 'Creat la', accessor: (r) => r.createdAt },
]

async function fetchAllTransactionsForExport(): Promise<Transaction[]> {
  const data = await searchTransactions(appliedSearchFilters.value, { page: 0, size: 10000, sort: sortBy.value })
  return data.content
}

const trendChartRef = ref<InstanceType<typeof TimeSeriesChart> | null>(null)
const chartType = ref<'DEPOSIT' | 'WITHDRAWAL' | 'BET' | 'PAYOUT' | 'REFUND'>('DEPOSIT')
const chartMetric = ref<'count' | 'totalAmount'>('totalAmount')
const { chartStart, chartEnd, chartData, chartLoading, loadChart } = useTimeSeriesChart(
  (start, end) => getTransactionsTimeSeries(start, end, chartType.value),
)

function resetSearchFilters() {
  transactionType.value = ''
  transactionStatus.value = ''
  selectedSearchUserId.value = ''
  startDate.value = ''
  endDate.value = ''
  minAmount.value = ''
  maxAmount.value = ''
  sortBy.value = 'createdAt,desc'
  hasSearched.value = false
  appliedSearchFilters.value = {
    userId: undefined,
    transactionType: undefined,
    transactionStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    minAmount: undefined,
    maxAmount: undefined,
  }
  searchPagination.reset()
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - tranzactii" subtitle="Gestionarea tranzactiilor din sistem." />

    <Panel id="transaction-pending" no-hover>
      <h2 class="text-lg font-semibold text-fg">Aprobari in asteptare</h2>

      <Transition name="page-fade" mode="out-in" appear>
        <div
          v-if="pendingQuery.data.value?.content?.length"
          :key="pendingQuery.data.value?.number ?? 0"
          class="mt-4 space-y-3"
        >
          <TransactionRow
            v-for="transaction in pendingQuery.data.value?.content ?? []"
            :key="transaction.id"
            :transaction="transaction"
            show-email
            route-name="admin-transaction-detail"
            link-label="Deschide pagina dedicata"
          />
        </div>
      </Transition>

      <EmptyState v-if="!(pendingQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Nu exista tranzactii care asteapta aprobare." />

      <AppPagination
        v-if="(pendingQuery.data.value?.totalElements ?? 0) > 0"
        :page="pendingQuery.data.value?.number ?? 0"
        :total-pages="pendingQuery.data.value?.totalPages ?? 0"
        :total-elements="pendingQuery.data.value?.totalElements"
        @change="pendingPagination.setPage"
      />
    </Panel>

    <Panel id="transaction-trend" no-hover>
      <h2 class="text-lg font-semibold text-fg">Evolutie in timp</h2>

      <div class="mt-4 grid items-end gap-3 md:grid-cols-4">
        <FormInput v-model="chartStart" label="Data inceput" type="datetime-local" />
        <FormInput v-model="chartEnd" label="Data sfarsit" type="datetime-local" />
        <label class="text-sm text-muted">
          <span class="mb-1 block">Tip</span>
          <select v-model="chartType" class="app-select-field app-select">
            <option value="DEPOSIT">{{ translateEnumLabel('DEPOSIT') }}</option>
            <option value="WITHDRAWAL">{{ translateEnumLabel('WITHDRAWAL') }}</option>
            <option value="BET">{{ translateEnumLabel('BET') }}</option>
            <option value="PAYOUT">{{ translateEnumLabel('PAYOUT') }}</option>
            <option value="REFUND">{{ translateEnumLabel('REFUND') }}</option>
          </select>
        </label>
        <label class="text-sm text-muted">
          <span class="mb-1 block">Metrica</span>
          <select v-model="chartMetric" class="app-select-field app-select">
            <option value="totalAmount">Suma totala</option>
            <option value="count">Numar tranzactii</option>
          </select>
        </label>
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <DatePresetButtons @select="(s, e) => { chartStart = s; chartEnd = e }" />
        <div class="flex flex-wrap gap-2">
          <AppButton :loading="chartLoading" @click="loadChart">Incarca graficul</AppButton>
          <AppButton variant="outline" :disabled="!chartData.length" @click="trendChartRef?.exportPng(`tranzactii-${chartType.toLowerCase()}-grafic`)">
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
          :label="chartMetric === 'totalAmount' ? 'Suma' : 'Numar'"
          color="#3b82f6"
        />
      </div>
    </Panel>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div class="space-y-6">
        <Panel id="transaction-report" no-hover>
          <h2 class="text-lg font-semibold text-fg">Raport</h2>

          <div class="mt-4 grid gap-3 md:grid-cols-2">
            <FormInput v-model="reportStart" label="Data inceput" type="datetime-local" />
            <FormInput v-model="reportEnd" label="Data sfarsit" type="datetime-local" />
          </div>
          <div class="mt-6 flex flex-wrap items-center justify-between gap-3">
            <DatePresetButtons @select="(s, e) => { reportStart = s; reportEnd = e }" />
            <AppButton :loading="loadingReport" @click="loadReport">Incarca raportul</AppButton>
          </div>
          

          <div v-if="report" class="mt-6 grid gap-3 grid-cols-2 md:grid-cols-3 2xl:grid-cols-5">
            <StatCard label="Depuneri" :value="formatMoney(report.totalDeposited)" />
            <StatCard label="Retras" :value="formatMoney(report.totalWithdrawn)" />
            <StatCard label="Pariat" :value="formatMoney(report.totalStaked)" />
            <StatCard label="Castigat" :value="formatMoney(report.totalWon)" />
            <StatCard label="Rambursat" :value="formatMoney(report.totalRefunded)" />
          </div>

          <div v-if="topDepositors.length" class="mt-6 space-y-2">
            <div
              v-for="user in topDepositors"
              :key="user.userId"
              class="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface px-3 sm:px-4 py-3 text-xs sm:text-sm"
            >
              <span class="min-w-0 truncate text-muted">{{ user.firstName }} {{ user.lastName }}</span>
              <span class="shrink-0 font-semibold text-fg">{{ formatMoney(user.totalDeposited) }}</span>
            </div>
          </div>
        </Panel>

        <Panel id="transaction-user-summary" no-hover>
          <h2 class="text-lg font-semibold text-fg">Sumar utilizator</h2>

          <div class="mt-4 grid gap-3 md:grid-cols-2">
            <AdminUserSearchPicker
              v-model="selectedSummaryUserId"
              title="Utilizator pentru sumar"
              placeholder="Cauta utilizatorul dupa email"
              class="md:col-span-2"
            />
            <FormInput v-model="summaryStart" label="Data inceput" type="datetime-local" />
            <FormInput v-model="summaryEnd" label="Data sfarsit" type="datetime-local" />
          </div>
          <div class="mt-6 flex flex-wrap items-center justify-between gap-3">
            <DatePresetButtons @select="(s, e) => { summaryStart = s; summaryEnd = e }" />
            <AppButton :loading="summaryLoading" @click="loadUserSummary">Incarca sumarul</AppButton>
          </div>

          <div v-if="userSummary" class="mt-4 grid gap-3 grid-cols-2 md:grid-cols-3">
            <StatCard label="Utilizator" :value="`${userSummary.firstName} ${userSummary.lastName}`" />
            <StatCard label="Depuneri" :value="formatMoney(userSummary.totalDeposited)" />
            <StatCard label="Retras" :value="formatMoney(userSummary.totalWithdrawn)" />
            <StatCard label="Pariat" :value="formatMoney(userSummary.totalStaked)" />
            <StatCard label="Castigat" :value="formatMoney(userSummary.totalWon)" />
            <StatCard label="Rambursat" :value="formatMoney(userSummary.totalRefunded)" />
          </div>
        </Panel>
      </div>

      <Panel id="transaction-user-history" no-hover>
        <h2 class="text-lg font-semibold text-fg">Tranzactiile utilizatorului</h2>

        <div class="mt-4 space-y-3">
          <AdminUserSearchPicker
            v-model="selectedHistoryUserId"
            title="Utilizator pentru istoric"
            placeholder="Cauta utilizatorul dupa email"
            results-title="Rezultate utilizatori"
            @select="handleHistoryUserSelect"
          />
        </div>

        <Transition name="page-fade" mode="out-in" appear>
          <div
            v-if="userTransactionsQuery.data.value?.content?.length"
            :key="userTransactionsQuery.data.value?.number ?? 0"
            class="mt-4 space-y-3"
          >
            <TransactionRow
              v-for="transaction in userTransactionsQuery.data.value?.content ?? []"
              :key="transaction.id"
              :transaction="transaction"
              route-name="admin-transaction-detail"
              link-label="Deschide pagina dedicata"
            />
          </div>
        </Transition>

        <EmptyState
          v-if="selectedHistoryUserId && !(userTransactionsQuery.data.value?.content?.length ?? 0)"
          class="mt-4"
          message="Utilizatorul nu are tranzactii in istoricul solicitat."
        />

        <AppPagination
          v-if="selectedHistoryUserId && (userTransactionsQuery.data.value?.totalElements ?? 0) > 0"
          :page="userTransactionsQuery.data.value?.number ?? 0"
          :total-pages="userTransactionsQuery.data.value?.totalPages ?? 0"
          :total-elements="userTransactionsQuery.data.value?.totalElements"
          @change="userTransactionsPagination.setPage"
        />
      </Panel>
    </div>

    <Panel id="transaction-search" no-hover>
      <div class="flex items-center justify-between gap-3">
        <h2 class="text-lg font-semibold text-fg">Cautare completa</h2>
        <ExportButton
          v-if="hasSearched"
          :fetch-all="fetchAllTransactionsForExport"
          :columns="exportColumns"
          filename="tranzactii"
          title="Tranzactii"
          :disabled="!(searchQuery.data.value?.content?.length ?? 0)"
        />
      </div>

      <div class="mt-4 grid gap-3 md:grid-cols-3">
        <AdminUserSearchPicker
          v-model="selectedSearchUserId"
          title="Utilizator pentru filtrare"
          placeholder="Cauta utilizatorul dupa email"
          results-title="Rezultate utilizatori"
        />

        <FormInput v-model="startDate" label="Data inceput" type="datetime-local" />
        <FormInput v-model="endDate" label="Data sfarsit" type="datetime-local" />
      </div>

      <div class="mt-3 grid items-end gap-3 md:grid-cols-3">
        <FormInput v-model="minAmount" label="Suma minima" type="number" min="0" step="0.01" />

        <label class="text-sm text-muted">
          <span class="mb-1 block">Tip</span>
          <select v-model="transactionType" class="app-select-field app-select">
            <option value="">Toate</option>
            <option :value="'DEPOSIT'">{{ translateEnumLabel('DEPOSIT') }}</option>
            <option :value="'WITHDRAWAL'">{{ translateEnumLabel('WITHDRAWAL') }}</option>
            <option :value="'BET'">{{ translateEnumLabel('BET') }}</option>
            <option :value="'PAYOUT'">{{ translateEnumLabel('PAYOUT') }}</option>
            <option :value="'REFUND'">{{ translateEnumLabel('REFUND') }}</option>
          </select>
        </label>

        <label class="text-sm text-muted">
          <span class="mb-1 block">Stare</span>
          <select v-model="transactionStatus" class="app-select-field app-select">
            <option value="">Toate</option>
            <option :value="'PENDING'">{{ translateEnumLabel('PENDING') }}</option>
            <option :value="'COMPLETED'">{{ translateEnumLabel('COMPLETED') }}</option>
            <option :value="'REJECTED'">{{ translateEnumLabel('REJECTED') }}</option>
          </select>
        </label>
      </div>

      <div class="mt-3 grid items-end gap-3 md:grid-cols-3">
        <FormInput v-model="maxAmount" label="Suma maxima" type="number" min="0" step="0.01" />
        <div class="hidden md:block"></div>
        <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
      </div>

      <div class="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap gap-3">
          <AppButton @click="applySearchFilters">Aplica filtrele</AppButton>
          <AppButton variant="outline" @click="resetSearchFilters">Reseteaza tot</AppButton>
        </div>
        <DatePresetButtons @select="(s, e) => { startDate = s; endDate = e }" />
      </div>

      <div class="mt-4 space-y-3">
        <EmptyState
          v-if="!hasSearched"
          message="Nu ai incarcat inca tranzactii filtrate."
          description="Alege filtrele pe care le vrei si apasa Aplica filtrele."
        />

        <Transition v-else name="page-fade" mode="out-in" appear>
          <div :key="searchQuery.data.value?.number ?? 0" class="space-y-3">
            <template v-if="searchQuery.data.value?.content?.length">
              <TransactionRow
                v-for="transaction in searchQuery.data.value?.content ?? []"
                :key="transaction.id"
                :transaction="transaction"
                show-email
                route-name="admin-transaction-detail"
                link-label="Deschide pagina dedicata"
              />
            </template>
            <EmptyState
              v-else
              class="mt-4"
              message="Nu exista tranzactii pentru filtrele selectate."
            />
          </div>
        </Transition>
      </div>

      <AppPagination
        v-if="hasSearched && (searchQuery.data.value?.totalElements ?? 0) > 0"
        :page="searchQuery.data.value?.number ?? 0"
        :total-pages="searchQuery.data.value?.totalPages ?? 0"
        :total-elements="searchQuery.data.value?.totalElements"
        @change="searchPagination.setPage"
      />
    </Panel>
  </div>
</template>


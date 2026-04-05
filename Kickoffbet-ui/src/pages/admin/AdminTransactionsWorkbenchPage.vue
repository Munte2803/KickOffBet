<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  approveTransaction,
  getPendingTransactions,
  getTopDepositors,
  getTransactionReport,
  getUserTransactionSummary,
  getUserTransactions,
  rejectTransaction,
  searchTransactions,
} from '@/api/transactions.admin.api'
import type { TransactionReport, UserDepositSummary, UserTransactionSummary } from '@/types/transaction.types'
import type { TransactionStatus, TransactionType } from '@/types/enums'
import { usePagination } from '@/composables/usePagination'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import FormInput from '@/components/FormInput.vue'
import AppPagination from '@/components/AppPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AdminUserSearchPicker from '@/components/AdminUserSearchPicker.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort, inputToUtcApiDate } from '@/utils/date.utils'
import { translateEnumLabel } from '@/utils/labels.utils'

const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const searchPagination = usePagination(12)
const userTransactionsPagination = usePagination(5)

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

const pendingQuery = useQuery({
  queryKey: ['admin-transaction-pending', sortBy],
  queryFn: () => getPendingTransactions({ page: 0, size: 10, sort: sortBy.value }),
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

async function updateTransaction(action: (id: string) => Promise<unknown>, id: string, message: string) {
  const isReject = action === rejectTransaction
  const confirmed = await confirm({
    title: isReject ? 'Respingere tranzactie' : 'Aprobare tranzactie',
    message: isReject
      ? 'Sunteti sigur ca vreti sa respingeti tranzactia selectata?'
      : 'Sunteti sigur ca vreti sa aprobati tranzactia selectata?',
    description: 'Actiunea va actualiza imediat starea tranzactiei.',
    confirmLabel: isReject ? 'Respinge tranzactia' : 'Aproba tranzactia',
    cancelLabel: 'Renunta',
    variant: isReject ? 'danger' : 'primary',
  })

  if (!confirmed) {
    return
  }

  try {
    await action(id)
    await Promise.all([pendingQuery.refetch(), searchQuery.refetch()])
    toastStore.showSuccess(message)
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Actiunea a esuat.')
  }
}

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
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca sumarul userului.')
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
    hasSearched.value = true
    appliedSearchFilters.value = {
      userId: undefined,
      transactionType: undefined,
      transactionStatus: undefined,
      startDate: undefined,
      endDate: undefined,
      minAmount: undefined,
      maxAmount: undefined,
    }
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
      <h2 class="text-lg font-semibold text-white">Aprobari in asteptare</h2>

      <div class="mt-4 space-y-3">
        <div
          v-for="transaction in pendingQuery.data.value?.content ?? []"
          :key="transaction.id"
          class="rounded-xl border border-white/10 bg-black/40 p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div class="flex items-center gap-2">
                <StatusBadge :status="transaction.transactionType" />
                <StatusBadge :status="transaction.status" />
              </div>
              <p class="mt-2 text-sm text-gray-400">{{ transaction.userEmail }} - {{ formatDateShort(transaction.createdAt) }}</p>
            </div>
            <div class="text-right">
              <p class="text-sm font-semibold text-white">{{ formatMoney(transaction.amount) }}</p>
              <div class="mt-2 flex flex-wrap gap-2">
                <AppButton size="sm" @click="updateTransaction(approveTransaction, transaction.id, 'Tranzactia a fost aprobata.')">Aproba</AppButton>
                <AppButton size="sm" variant="danger" @click="updateTransaction(rejectTransaction, transaction.id, 'Tranzactia a fost respinsa.')">Respinge</AppButton>
              </div>
            </div>
          </div>

          <div class="mt-3 flex gap-3">
            <RouterLink
              :to="{ name: 'admin-transaction-detail', params: { id: transaction.id } }"
              class="inline-flex items-center text-xs font-semibold text-blue-300 transition-colors hover:text-blue-200"
              @click.stop
            >
              Deschide pagina dedicata
            </RouterLink>
          </div>
        </div>
      </div>

      <EmptyState v-if="!(pendingQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Nu exista tranzactii care asteapta aprobare." />
    </Panel>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div class="space-y-6">
        <Panel id="transaction-report" no-hover>
          <h2 class="text-lg font-semibold text-white">Raport</h2>

          <div class="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_auto]">
            <FormInput v-model="reportStart" label="Data inceput" type="datetime-local" />
            <FormInput v-model="reportEnd" label="Data sfarsit" type="datetime-local" />
            <div class="flex items-end">
              <AppButton :loading="loadingReport" @click="loadReport">Incarca raportul</AppButton>
            </div>
          </div>

          <div v-if="report" class="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Depuneri</p>
              <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(report.totalDeposited) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Retras</p>
              <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(report.totalWithdrawn) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Pariat</p>
              <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(report.totalStaked) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Castigat</p>
              <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(report.totalWon) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Rambursat</p>
              <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(report.totalRefunded) }}</p>
            </div>
          </div>

          <div v-if="topDepositors.length" class="mt-6 space-y-2">
            <div
              v-for="user in topDepositors"
              :key="user.userId"
              class="flex items-center justify-between rounded-xl border border-white/10 bg-black/40 px-4 py-3 text-sm"
            >
              <span class="text-gray-300">{{ user.firstName }} {{ user.lastName }}</span>
              <span class="font-semibold text-white">{{ formatMoney(user.totalDeposited) }}</span>
            </div>
          </div>
        </Panel>

        <Panel id="transaction-user-summary" no-hover>
          <h2 class="text-lg font-semibold text-white">Sumar utilizator</h2>

          <div class="mt-4 grid gap-3 md:grid-cols-2">
            <AdminUserSearchPicker
              v-model="selectedSummaryUserId"
              title="Utilizator pentru sumar"
              placeholder="Cauta utilizatorul dupa email"
              class="md:col-span-2"
            />
            <FormInput v-model="summaryStart" label="Data inceput" type="datetime-local" />
            <FormInput v-model="summaryEnd" label="Data sfarsit" type="datetime-local" />
            <div class="flex items-end">
              <AppButton :loading="summaryLoading" @click="loadUserSummary">Incarca sumarul</AppButton>
            </div>
          </div>

          <div v-if="userSummary" class="mt-4 grid gap-3 md:grid-cols-2">
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Utilizator</p>
              <p class="mt-2 text-sm font-semibold text-white">{{ userSummary.firstName }} {{ userSummary.lastName }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Depuneri</p>
              <p class="mt-2 text-sm font-semibold text-white">{{ formatMoney(userSummary.totalDeposited) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Retras</p>
              <p class="mt-2 text-sm font-semibold text-white">{{ formatMoney(userSummary.totalWithdrawn) }}</p>
            </div>
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Pariat / Castigat / Rambursat</p>
              <p class="mt-2 text-sm font-semibold text-white">
                {{ formatMoney(userSummary.totalStaked) }} / {{ formatMoney(userSummary.totalWon) }} / {{ formatMoney(userSummary.totalRefunded) }}
              </p>
            </div>
          </div>
        </Panel>
      </div>

      <Panel id="transaction-user-history" no-hover>
        <h2 class="text-lg font-semibold text-white">Tranzactiile utilizatorului</h2>

        <div class="mt-4 space-y-3">
          <AdminUserSearchPicker
            v-model="selectedHistoryUserId"
            title="Utilizator pentru istoric"
            placeholder="Cauta utilizatorul dupa email"
            results-title="Rezultate utilizatori"
            @select="handleHistoryUserSelect"
          />
        </div>

        <div class="mt-4 space-y-3">
          <div
            v-for="transaction in userTransactionsQuery.data.value?.content ?? []"
            :key="transaction.id"
            class="rounded-xl border border-white/10 bg-black/40 p-4"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <StatusBadge :status="transaction.transactionType" />
                <StatusBadge :status="transaction.status" />
              </div>
              <span class="text-sm font-semibold text-white">{{ formatMoney(transaction.amount) }}</span>
            </div>
            <p class="mt-2 text-xs text-gray-400">{{ formatDateShort(transaction.createdAt) }}</p>
          </div>
        </div>

        <EmptyState
          v-if="selectedHistoryUserId && !(userTransactionsQuery.data.value?.content?.length ?? 0)"
          class="mt-4"
          message="Utilizatorul nu are tranzactii in istoricul solicitat."
        />

        <AppPagination
          v-if="(userTransactionsQuery.data.value?.totalPages ?? 0) > 1"
          :page="userTransactionsQuery.data.value?.number ?? 0"
          :total-pages="userTransactionsQuery.data.value?.totalPages ?? 0"
          @change="userTransactionsPagination.setPage"
        />
      </Panel>
    </div>

    <Panel id="transaction-search" no-hover>
      <h2 class="text-lg font-semibold text-white">Cautare completa</h2>

      <div class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <AdminUserSearchPicker
          v-model="selectedSearchUserId"
          title="Utilizator pentru filtrare"
          placeholder="Cauta utilizatorul dupa email"
          results-title="Rezultate utilizatori"
          class="md:col-span-2 xl:col-span-2"
        />

        <label class="text-sm text-gray-300">
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

        <label class="text-sm text-gray-300">
          <span class="mb-1 block">Stare</span>
          <select v-model="transactionStatus" class="app-select-field app-select">
            <option value="">Toate</option>
            <option :value="'PENDING'">{{ translateEnumLabel('PENDING') }}</option>
            <option :value="'COMPLETED'">{{ translateEnumLabel('COMPLETED') }}</option>
            <option :value="'REJECTED'">{{ translateEnumLabel('REJECTED') }}</option>
          </select>
        </label>

        <FormInput v-model="minAmount" label="Suma minima" type="number" min="0" step="0.01" />
        <FormInput v-model="maxAmount" label="Suma maxima" type="number" min="0" step="0.01" />
        <FormInput v-model="startDate" label="Data inceput" type="datetime-local" />
        <FormInput v-model="endDate" label="Data sfarsit" type="datetime-local" />
        <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
      </div>

      <div class="mt-6 flex flex-wrap gap-3">
        <AppButton @click="applySearchFilters">Aplica filtrele</AppButton>
        <AppButton variant="outline" @click="resetSearchFilters">Reseteaza tot</AppButton>
      </div>

      <div class="mt-4 space-y-3">
        <EmptyState
          v-if="!hasSearched"
          message="Nu ai incarcat inca tranzactii filtrate."
          description="Alege filtrele pe care le vrei si apasa Aplica filtrele."
        />

        <template v-else-if="searchQuery.data.value?.content?.length">
          <div
            v-for="transaction in searchQuery.data.value?.content ?? []"
            :key="transaction.id"
            class="rounded-xl border border-white/10 bg-black/40 p-4"
          >
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <StatusBadge :status="transaction.transactionType" />
                  <StatusBadge :status="transaction.status" />
                </div>
                <p class="mt-2 text-sm text-gray-400">{{ transaction.userEmail }} - {{ formatDateShort(transaction.createdAt) }}</p>
              </div>
              <span class="text-sm font-semibold text-white">{{ formatMoney(transaction.amount) }}</span>
            </div>

            <div class="mt-3 flex justify-start">
              <RouterLink
                :to="{ name: 'admin-transaction-detail', params: { id: transaction.id } }"
                class="text-xs font-semibold text-blue-300 transition-colors hover:text-blue-200"
                @click.stop
              >
                Deschide pagina dedicata
              </RouterLink>
            </div>
          </div>
        </template>

        <EmptyState
          v-else
          class="mt-4"
          message="Nu exista tranzactii pentru filtrele selectate."
        />
      </div>

      <AppPagination
        v-if="hasSearched && (searchQuery.data.value?.totalPages ?? 0) > 1"
        :page="searchQuery.data.value?.number ?? 0"
        :total-pages="searchQuery.data.value?.totalPages ?? 0"
        @change="searchPagination.setPage"
      />
    </Panel>
  </div>
</template>


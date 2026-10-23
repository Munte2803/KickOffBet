<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { deposit, getTransactions, withdraw } from '@/api/wallet.api'
import type { TransactionTypeFilter } from '@/types/transaction.types'
import { TRANSACTION_TYPES } from '@/types/enums'
import { TRANSACTION_TYPE_TAB_LABELS } from '@/utils/labels.utils'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth.store'
import { useToastStore } from '@/stores/toast.store'
import { depositSchema, withdrawSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AppPagination from '@/components/AppPagination.vue'
import TransactionRow from '@/components/TransactionRow.vue'
import TabNav from '@/components/TabNav.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import AppErrorState from '@/components/AppErrorState.vue'
import { formatMoney } from '@/utils/money.utils'
import { queryKeys } from '@/query/query-keys'

const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const mode = ref<'deposit' | 'withdraw'>('deposit')
const transactionType = ref<TransactionTypeFilter>('ALL')
const transactionTabs = [
  { key: 'ALL', label: 'Toate' },
  ...TRANSACTION_TYPES.map((type) => ({ key: type, label: TRANSACTION_TYPE_TAB_LABELS[type] })),
]
const pagination = usePagination(PAGE_SIZES.COMPACT)
const transactionPageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'createdAt,desc',
}))

const transactionsQuery = useQuery({
  queryKey: queryKeys.walletTransactions(transactionType, transactionPageRequest),
  queryFn: () => getTransactions(transactionType.value, transactionPageRequest.value),
})

// The key holds the type and the page: changing them fetches, so only the page is reset here
watch(transactionType, () => {
  pagination.reset()
})

const depositForm = useForm({
  validationSchema: toTypedSchema(depositSchema),
  initialValues: { amount: 20 },
})

const withdrawForm = useForm({
  validationSchema: toTypedSchema(withdrawSchema),
  initialValues: { amount: 20 },
})

const [depositAmount] = depositForm.defineField('amount')
const [withdrawAmount] = withdrawForm.defineField('amount')

/**
 * Runs after a deposit/withdrawal succeeded: a failure here only means the shown balance may lag,
 * so it is never reported as a failed money operation.
 */
async function refreshAfterWalletChange() {
  // Every filter tab of the history (and the profile) is cached separately; on failure the layout
  // refreshes the profile again on the next navigation
  await authStore.refreshAccountAfterMutation()
}

const submitDeposit = depositForm.handleSubmit(async (values) => {
  const confirmed = await confirm({
    title: 'Confirmare depunere',
    message: `Sunteti sigur ca vreti sa inregistrati o depunere de ${formatMoney(values.amount)}?`,
    description: 'Suma va fi adaugata imediat in sold daca operatiunea este acceptata de server.',
    confirmLabel: 'Confirma depunerea',
    cancelLabel: 'Renunta',
  })

  if (!confirmed) {
    return
  }

  try {
    await deposit({ amount: values.amount })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut procesa depunerea.')
    return
  }

  toastStore.showSuccess('Depunerea a fost procesata.')
  await refreshAfterWalletChange()
})

const submitWithdraw = withdrawForm.handleSubmit(async (values) => {
  if (values.amount > authStore.balance) {
    toastStore.showError(`Fonduri insuficiente. Balanta curenta: ${formatMoney(authStore.balance)}.`)
    return
  }

  const confirmed = await confirm({
    title: 'Confirmare retragere',
    message: `Sunteti sigur ca vreti sa inregistrati o retragere de ${formatMoney(values.amount)}?`,
    description: 'Suma va fi rezervata din sold conform regulilor platformei.',
    confirmLabel: 'Confirma retragerea',
    cancelLabel: 'Renunta',
    variant: 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await withdraw({ amount: values.amount })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut procesa retragerea.')
    return
  }

  toastStore.showSuccess('Retragerea a fost inregistrata.')
  await refreshAfterWalletChange()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Portofel" subtitle="Depuneri, retrageri si istoric tranzactii" />

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[380px_minmax(0,1fr)] items-start">
      <Panel>
        <div class="flex gap-2">
          <button
            class="flex-1 rounded-lg px-3 py-2 text-[13px] font-semibold transition-colors sm:text-sm"
            :class="mode === 'deposit' ? 'bg-blue-600 text-white' : 'bg-surface text-muted'"
            @click="mode = 'deposit'"
          >
            Depunere
          </button>
          <button
            class="flex-1 rounded-lg px-3 py-2 text-[13px] font-semibold transition-colors sm:text-sm"
            :class="mode === 'withdraw' ? 'bg-blue-600 text-white' : 'bg-surface text-muted'"
            @click="mode = 'withdraw'"
          >
            Retragere
          </button>
        </div>

        <div class="mt-4 rounded-xl border border-line bg-surface p-3 sm:p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-subtle">Balanta curenta</p>
          <p class="mt-2 text-2xl font-black text-fg sm:text-3xl">{{ formatMoney(authStore.balance) }}</p>
        </div>

        <form v-if="mode === 'deposit'" class="mt-4 space-y-4" @submit.prevent="submitDeposit">
          <FormInput
            v-model="depositAmount"
            label="Suma"
            type="number"
            :error="depositForm.errors.value.amount"
            min="20"
            max="200000"
            step="0.01"
          />
          <AppButton class="w-full" type="submit" :loading="depositForm.isSubmitting.value">Confirma depunerea</AppButton>
        </form>

        <form v-else class="mt-4 space-y-4" @submit.prevent="submitWithdraw">
          <FormInput
            v-model="withdrawAmount"
            label="Suma"
            type="number"
            :error="withdrawForm.errors.value.amount"
            min="20"
            max="200000"
            step="0.01"
          />
          <AppButton class="w-full" type="submit" :loading="withdrawForm.isSubmitting.value">Retrage bani</AppButton>
        </form>
      </Panel>

      <Panel>
        <h2 class="text-lg font-semibold text-fg">Istoric tranzactii</h2>

        <div class="mt-4">
          <TabNav
            :tabs="transactionTabs"
            :active="transactionType"
            @change="transactionType = $event as TransactionTypeFilter"
          />
        </div>

        <LoadingState v-if="transactionsQuery.isLoading.value" class="mt-4" message="Se incarca tranzactiile..." />

        <AppErrorState
          v-else-if="transactionsQuery.isError.value"
          class="mt-4"
          message="Nu am putut incarca tranzactiile."
          :error="transactionsQuery.error.value"
          :retrying="transactionsQuery.isFetching.value"
          @retry="transactionsQuery.refetch()"
        />

        <Transition v-else name="page-fade" mode="out-in" appear>
          <div
            v-if="transactionsQuery.data.value?.content?.length"
            :key="transactionsQuery.data.value?.number ?? 0"
            class="mt-4 space-y-3"
          >
            <TransactionRow
              v-for="transaction in transactionsQuery.data.value?.content ?? []"
              :key="transaction.id"
              :transaction="transaction"
              route-name="transaction-detail"
              compact
            />
          </div>
        </Transition>

        <EmptyState
          v-if="!transactionsQuery.data.value?.content?.length && !transactionsQuery.isLoading.value && !transactionsQuery.isError.value"
          class="mt-4"
          message="Nu ai inca tranzactii pentru filtrul selectat."
          description="Efectueaza o depunere, o retragere ori un pariu nou."
        />

        <AppPagination
          v-if="!transactionsQuery.isError.value && (transactionsQuery.data.value?.totalElements ?? 0) > 0"
          :page="transactionsQuery.data.value?.number ?? 0"
          :total-pages="transactionsQuery.data.value?.totalPages ?? 0"
          :total-elements="transactionsQuery.data.value?.totalElements"
          @change="pagination.setPage"
        />
      </Panel>
    </div>
  </div>
</template>

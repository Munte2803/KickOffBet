<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { deposit, getTransactions, withdraw } from '@/api/wallet.api'
import { getProfile } from '@/api/profile.api'
import type { TransactionTypeFilter } from '@/types/transaction.types'
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
import { formatMoney } from '@/utils/money.utils'

const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const mode = ref<'deposit' | 'withdraw'>('deposit')
const transactionType = ref<TransactionTypeFilter>('ALL')
const pagination = usePagination(PAGE_SIZES.COMPACT)
const transactionPageRequest = computed(() => ({
  ...pagination.request.value,
  sort: 'createdAt,desc',
}))

const transactionsQuery = useQuery({
  queryKey: ['wallet-transactions', transactionType, transactionPageRequest],
  queryFn: () => getTransactions(transactionType.value, transactionPageRequest.value),
})

watch(transactionType, () => {
  pagination.reset()
  transactionsQuery.refetch()
})

watch([() => pagination.page.value, () => pagination.size.value], () => {
  transactionsQuery.refetch()
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

async function refreshProfileBalance() {
  const profile = await getProfile()
  authStore.hydrateProfile(profile)
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
    await refreshProfileBalance()
    await transactionsQuery.refetch()
    toastStore.showSuccess('Depunerea a fost procesata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut procesa depunerea.')
  }
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
    await refreshProfileBalance()
    await transactionsQuery.refetch()
    toastStore.showSuccess('Retragerea a fost inregistrata.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut procesa retragerea.')
  }
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Portofel" subtitle="Depuneri, retrageri si istoric tranzactii" />

    <div class="grid gap-6 lg:grid-cols-[380px_minmax(0,1fr)] items-start">
      <Panel>
        <div class="flex gap-2">
          <button
            class="flex-1 rounded-lg px-3 py-2 text-[13px] font-semibold transition-colors sm:text-sm"
            :class="mode === 'deposit' ? 'bg-blue-600 text-white' : 'bg-white/5 text-gray-300'"
            @click="mode = 'deposit'"
          >
            Depunere
          </button>
          <button
            class="flex-1 rounded-lg px-3 py-2 text-[13px] font-semibold transition-colors sm:text-sm"
            :class="mode === 'withdraw' ? 'bg-blue-600 text-white' : 'bg-white/5 text-gray-300'"
            @click="mode = 'withdraw'"
          >
            Retragere
          </button>
        </div>

        <div class="mt-4 rounded-xl border border-white/10 bg-black/40 p-3 sm:p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Balanta curenta</p>
          <p class="mt-2 text-2xl font-black text-white sm:text-3xl">{{ formatMoney(authStore.balance) }}</p>
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
        <h2 class="text-lg font-semibold text-white">Istoric tranzactii</h2>

        <div class="mt-4">
          <TabNav
            :tabs="[
              { key: 'ALL', label: 'Toate' },
              { key: 'DEPOSIT', label: 'Depuneri' },
              { key: 'WITHDRAWAL', label: 'Retrageri' },
              { key: 'BET', label: 'Pariuri' },
              { key: 'PAYOUT', label: 'Castiguri platite' },
              { key: 'REFUND', label: 'Rambursari' },
            ]"
            :active="transactionType"
            @change="transactionType = $event as TransactionTypeFilter"
          />
        </div>

        <Transition name="page-fade" mode="out-in" appear>
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
          v-if="!transactionsQuery.data.value?.content?.length && !transactionsQuery.isLoading.value"
          class="mt-4"
          message="Nu ai inca tranzactii pentru filtrul selectat."
          description="Efectueaza o depunere, o retragere ori un pariu nou."
        />

        <AppPagination
          v-if="(transactionsQuery.data.value?.totalElements ?? 0) > 0"
          :page="transactionsQuery.data.value?.number ?? 0"
          :total-pages="transactionsQuery.data.value?.totalPages ?? 0"
          :total-elements="transactionsQuery.data.value?.totalElements"
          @change="pagination.setPage"
        />
      </Panel>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { approveTransaction, getTransactionById, rejectTransaction } from '@/api/transactions.admin.api'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import { formatDateShort } from '@/utils/date.utils'
import { formatMoney } from '@/utils/money.utils'

const route = useRoute()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const transactionId = route.params.id as string

const transactionQuery = useQuery({
  queryKey: ['admin-transaction-route-detail', transactionId],
  queryFn: () => getTransactionById(transactionId),
})

async function runAction(action: (id: string) => Promise<unknown>, message: string) {
  const isReject = action === rejectTransaction
  const confirmed = await confirm({
    title: isReject ? 'Respingere tranzactie' : 'Aprobare tranzactie',
    message: isReject
      ? 'Sunteti sigur ca vreti sa respingeti tranzactia?'
      : 'Sunteti sigur ca vreti sa aprobati tranzactia?',
    description: 'Actiunea se aplica imediat tranzactiei selectate.',
    confirmLabel: isReject ? 'Respinge tranzactia' : 'Aproba tranzactia',
    cancelLabel: 'Renunta',
    variant: isReject ? 'danger' : 'primary',
  })

  if (!confirmed) {
    return
  }

  try {
    await action(transactionId)
    await transactionQuery.refetch()
    toastStore.showSuccess(message)
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Actiunea a esuat.')
  }
}
</script>

<template>
  <LoadingState v-if="transactionQuery.isLoading.value" message="Se incarca tranzactia..." />

  <div v-else-if="transactionQuery.data.value" class="space-y-6">
    <PageHeader
      title="Administrare - detaliu tranzactie"
      :subtitle="`${transactionQuery.data.value.userEmail} · ${formatDateShort(transactionQuery.data.value.createdAt)}`"
      :back-route="{ name: 'admin-transactions' }"
    />

    <section id="transaction-overview" class="grid gap-4 md:grid-cols-4">
      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Tip</p>
        <div class="mt-3">
          <StatusBadge :status="transactionQuery.data.value.transactionType" />
        </div>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Status</p>
        <div class="mt-3">
          <StatusBadge :status="transactionQuery.data.value.status" />
        </div>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Suma</p>
        <p class="mt-2 text-2xl font-bold text-white">{{ formatMoney(transactionQuery.data.value.amount) }}</p>
      </Panel>

      <Panel no-hover>
        <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Referinta</p>
        <RouterLink
          v-if="transactionQuery.data.value.referenceId && transactionQuery.data.value.referenceType === 'TICKET'"
          :to="{ name: 'admin-ticket-detail', params: { id: transactionQuery.data.value.referenceId } }"
          class="mt-2 inline-flex text-sm font-semibold text-blue-300 transition-colors hover:text-blue-300"
        >
          {{ transactionQuery.data.value.referenceId }}
        </RouterLink>
        <RouterLink
          v-else-if="transactionQuery.data.value.referenceId && transactionQuery.data.value.referenceType === 'TRANSACTION'"
          :to="{ name: 'admin-transaction-detail', params: { id: transactionQuery.data.value.referenceId } }"
          class="mt-2 inline-flex text-sm font-semibold text-blue-300 transition-colors hover:text-blue-300"
        >
          {{ transactionQuery.data.value.referenceId }}
        </RouterLink>
        <p v-else class="mt-2 text-sm font-semibold text-white">Fara referinta</p>
      </Panel>
    </section>

    <Panel id="transaction-actions" no-hover>
      <h2 class="text-lg font-semibold text-white">Actiuni</h2>
      <p class="mt-1 text-sm text-gray-400">Pentru tranzactiile pending exista optiuni de aprobare sau respingere.</p>

      <div class="mt-4 flex flex-wrap gap-3">
        <AppButton
          v-if="transactionQuery.data.value.status === 'PENDING'"
          @click="runAction(approveTransaction, 'Tranzactia a fost aprobata.')"
        >
          Aproba
        </AppButton>
        <AppButton
          v-if="transactionQuery.data.value.status === 'PENDING'"
          variant="danger"
          @click="runAction(rejectTransaction, 'Tranzactia a fost respinsa.')"
        >
          Respinge
        </AppButton>
      </div>
    </Panel>
  </div>

  <EmptyState v-else message="Nu am gasit tranzactia ceruta in admin." />
</template>


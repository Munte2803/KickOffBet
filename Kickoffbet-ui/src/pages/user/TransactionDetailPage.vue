<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { getTransactionDetails } from '@/api/transactions.api'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import { formatDateShort } from '@/utils/date.utils'
import { formatMoney } from '@/utils/money.utils'

const route = useRoute()
const transactionId = route.params.id as string

const transactionQuery = useQuery({
  queryKey: ['transaction-route-detail', transactionId],
  queryFn: () => getTransactionDetails(transactionId),
})
</script>

<template>
  <LoadingState v-if="transactionQuery.isLoading.value" message="Se incarca tranzactia..." />

  <div v-else-if="transactionQuery.data.value" class="space-y-6">
    <PageHeader
      title="Detaliu tranzactie"
      :subtitle="`${formatDateShort(transactionQuery.data.value.createdAt)}`"
      :back-route="{ name: 'wallet' }"
    />

    <section class="grid gap-4 md:grid-cols-4">
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
          :to="{ name: 'ticket-detail', params: { id: transactionQuery.data.value.referenceId } }"
          class="mt-2 inline-flex text-sm font-semibold text-blue-300 transition-colors hover:text-blue-300"
        >
          {{ transactionQuery.data.value.referenceId }}
        </RouterLink>
        <RouterLink
          v-else-if="transactionQuery.data.value.referenceId && transactionQuery.data.value.referenceType === 'TRANSACTION'"
          :to="{ name: 'transaction-detail', params: { id: transactionQuery.data.value.referenceId } }"
          class="mt-2 inline-flex text-sm font-semibold text-blue-300 transition-colors hover:text-blue-300"
        >
          {{ transactionQuery.data.value.referenceId }}
        </RouterLink>
        <p v-else class="mt-2 text-sm font-semibold text-white">Fara referinta</p>
      </Panel>
    </section>
  </div>

  <EmptyState v-else message="Nu am gasit tranzactia ceruta." />
</template>


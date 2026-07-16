<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Transaction } from '@/types/transaction.types'
import StatusBadge from '@/components/StatusBadge.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'

withDefaults(defineProps<{
  transaction: Transaction
  showEmail?: boolean
  routeName?: string
  linkLabel?: string
  compact?: boolean
}>(), {
  showEmail: false,
  routeName: '',
  linkLabel: 'Deschide detaliul tranzactiei',
  compact: false,
})
</script>

<template>
  <div
    class="rounded-xl border border-white/10 bg-black/40"
    :class="compact ? 'p-2 sm:p-2.5' : 'p-3 sm:p-4'"
  >
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <div class="flex items-center gap-2">
          <StatusBadge :status="transaction.transactionType" />
          <StatusBadge :status="transaction.status" />
        </div>
        <p class="text-xs text-gray-400" :class="compact ? 'mt-1' : 'mt-2'">
          <template v-if="showEmail">{{ transaction.userEmail }} - </template>
          {{ formatDateShort(transaction.createdAt) }}
        </p>
      </div>
      <span class="text-sm font-semibold text-white">{{ formatMoney(transaction.amount) }}</span>
    </div>
    <div v-if="routeName" class="flex justify-start" :class="compact ? 'mt-1.5' : 'mt-3'">
      <RouterLink
        :to="{ name: routeName, params: { id: transaction.id } }"
        class="text-xs font-semibold text-blue-300 transition-colors hover:text-blue-300"
        @click.stop
      >
        {{ linkLabel }}
      </RouterLink>
    </div>
    <slot />
  </div>
</template>

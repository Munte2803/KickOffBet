<script setup lang="ts">
import { translateEnumLabel } from '@/utils/labels.utils'

defineProps<{ status: string }>()

const colorMap: Record<string, string> = {
  // Ticket/Selection
  WON: 'bg-green-500/10 text-green-400 border-green-500/30',
  LOST: 'bg-red-500/10 text-red-400 border-red-500/30',
  PENDING: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30',
  CANCELLED: 'bg-gray-500/10 text-gray-400 border-gray-500/30',
  // Match
  SCHEDULED: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
  LIVE: 'bg-green-500/10 text-green-400 border-green-500/30 animate-pulse',
  FINISHED: 'bg-gray-500/10 text-gray-400 border-gray-500/30',
  POSTPONED: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
  SUSPENDED: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
  UNKNOWN: 'bg-gray-500/10 text-gray-400 border-gray-500/30',
  // User
  ACTIVE: 'bg-green-500/10 text-green-400 border-green-500/30',
  DECLINED: 'bg-red-500/10 text-red-400 border-red-500/30',
  DEACTIVATED: 'bg-gray-500/10 text-gray-400 border-gray-500/30',
  // Transaction
  COMPLETED: 'bg-green-500/10 text-green-400 border-green-500/30',
  REJECTED: 'bg-red-500/10 text-red-400 border-red-500/30',
  DEPOSIT: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
  WITHDRAWAL: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
  PAYOUT: 'bg-green-500/10 text-green-400 border-green-500/30',
  REFUND: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
  BET: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
}


</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded border px-1.25 py-0.5 text-[9px] font-semibold sm:px-2 sm:text-xs',
      colorMap[status] ?? 'bg-gray-500/10 text-gray-400 border-gray-500/30',
    ]"
  >
    {{ translateEnumLabel(status) }}
  </span>
</template>


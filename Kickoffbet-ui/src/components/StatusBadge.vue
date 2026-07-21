<script setup lang="ts">
import { translateEnumLabel } from '@/utils/labels.utils'

defineProps<{ status: string }>()

/**
 * Mono-shade per color family. Each semantic family (green/red/yellow/blue/gray/orange/purple)
 * uses ONLY the 500 shade across bg / text / border — opacity varies the depth (60-30-10 rule):
 *   bg = /10  (light surface)  ·  text = /1 (accent)  ·  border = /30 (mid)
 */
const colorMap: Record<string, string> = {
  // green — positive / finalized
  WON: 'bg-green-500/10 text-green-500 border-green-500/30',
  ACTIVE: 'bg-green-500/10 text-green-500 border-green-500/30',
  COMPLETED: 'bg-green-500/10 text-green-500 border-green-500/30',
  PAYOUT: 'bg-green-500/10 text-green-500 border-green-500/30',
  LIVE: 'bg-green-500/10 text-green-500 border-green-500/30 animate-pulse',
  // red — negative / failed
  LOST: 'bg-red-500/10 text-red-500 border-red-500/30',
  DECLINED: 'bg-red-500/10 text-red-500 border-red-500/30',
  REJECTED: 'bg-red-500/10 text-red-500 border-red-500/30',
  // yellow — pending / awaiting
  PENDING: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/30',
  // orange — paused / postponed
  POSTPONED: 'bg-orange-500/10 text-orange-500 border-orange-500/30',
  SUSPENDED: 'bg-orange-500/10 text-orange-500 border-orange-500/30',
  WITHDRAWAL: 'bg-orange-500/10 text-orange-500 border-orange-500/30',
  // blue — scheduled / incoming
  SCHEDULED: 'bg-blue-500/10 text-blue-500 border-blue-500/30',
  DEPOSIT: 'bg-blue-500/10 text-blue-500 border-blue-500/30',
  // purple — internal flow
  REFUND: 'bg-purple-500/10 text-purple-500 border-purple-500/30',
  BET: 'bg-purple-500/10 text-purple-500 border-purple-500/30',
  // gray — neutral / inactive
  CANCELLED: 'bg-gray-500/10 text-subtle border-gray-500/30',
  FINISHED: 'bg-gray-500/10 text-subtle border-gray-500/30',
  DEACTIVATED: 'bg-gray-500/10 text-subtle border-gray-500/30',
  UNKNOWN: 'bg-gray-500/10 text-subtle border-gray-500/30',
}
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded border px-1.25 py-0.5 text-[9px] font-semibold sm:px-2 sm:text-xs',
      colorMap[status] ?? 'bg-gray-500/10 text-subtle border-gray-500/30',
    ]"
  >
    {{ translateEnumLabel(status) }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTicketStore } from '@/stores/ticket.store'
import { useTicketActions } from '@/composables/useTicketActions'
import Panel from './Panel.vue'
import AppButton from './AppButton.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { MAX_STAKE, MAX_WIN, MIN_STAKE } from '@/constants/betting.constants'
import { translateEnumLabel } from '@/utils/labels.utils'

const ticketStore = useTicketStore()
const { placingTicket, placeCurrentTicket } = useTicketActions()

const rawPotentialWin = computed(() => ticketStore.stake * ticketStore.totalOdds)
const isWinCapped = computed(() => rawPotentialWin.value > MAX_WIN)

function updateStake(event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  if (!Number.isFinite(value)) {
    ticketStore.stake = MIN_STAKE
    return
  }

  ticketStore.stake = Math.min(MAX_STAKE, Math.max(MIN_STAKE, value))
}
</script>

<template>
  <Panel
    class="flex h-full min-h-0 flex-col rounded-none border-0 bg-transparent p-0 shadow-none lg:max-h-[calc(100vh-var(--app-slip-top)-16px)] lg:overflow-hidden lg:rounded-xl lg:border lg:border-white/10 lg:bg-black/70 lg:p-4 lg:shadow-[0_18px_42px_rgba(0,0,0,0.34)]"
    no-hover
  >
    <div class="hidden items-center justify-between gap-3 lg:flex">
      <div>
        <h2 class="text-lg font-bold text-white">Biletul meu</h2>
        <p class="text-xs text-gray-400">{{ ticketStore.selectionCount }} selectii</p>
      </div>

      <button
        v-if="ticketStore.selectionCount"
        class="text-xs font-medium text-gray-400 transition-colors hover:text-white"
        @click="ticketStore.clearSelections()"
      >
        Goleste
      </button>
    </div>

    <div
      v-if="!ticketStore.selectionCount"
      class="mx-3 rounded-xl border border-dashed border-white/10 bg-black/40 px-3 py-5 text-center text-[13px] text-gray-400 max-[390px]:mx-2.5 max-[390px]:px-2.5 max-[390px]:py-4 max-[390px]:text-[12px] max-[360px]:mx-2 max-[360px]:px-2 max-[360px]:py-3.5 max-[360px]:text-[11px] sm:mx-4 sm:px-4 sm:py-8 sm:text-sm lg:mx-0 lg:mt-4"
    >
      Selecteaza cote active pentru a construi biletul.
    </div>

    <div v-else class="flex min-h-0 flex-1 flex-col gap-3">
      <div class="flex items-center justify-between gap-3 border-b border-white/10 px-3 pb-2.5 max-[390px]:px-2.5 max-[390px]:pb-2 max-[360px]:px-2 max-[360px]:pb-1.5 lg:hidden">
        <p class="text-[13px] font-semibold text-white max-[390px]:text-[12px] max-[360px]:text-[11px]">{{ ticketStore.selectionCount }} selectii</p>
        <button
          class="text-[11px] font-medium text-gray-400 transition-colors hover:text-white max-[390px]:text-[10px] max-[360px]:text-[9px]"
          @click="ticketStore.clearSelections()"
        >
          Goleste
        </button>
      </div>

      <div class="mt-1.5 min-h-0 flex-1 overflow-y-auto px-3 max-[390px]:mt-1 max-[390px]:px-2.5 max-[360px]:px-2 sm:px-4 lg:mt-4 lg:px-0">
        <div class="space-y-2 pb-2 max-[390px]:space-y-1.5 max-[390px]:pb-1.5 max-[360px]:space-y-1 max-[360px]:pb-1 lg:pb-0 lg:pr-1 lg:overscroll-y-contain">
          <div
            v-for="selection in ticketStore.selections"
            :key="selection.offerId"
            class="rounded-xl border border-white/10 bg-black/40 p-2 max-[390px]:p-1.5 max-[360px]:p-1.25 sm:p-3"
          >
            <div class="flex items-start justify-between gap-2.5">
              <div class="min-w-0 flex-1">
                <p class="text-[12px] font-semibold text-white max-[390px]:text-[11px] max-[360px]:text-[10px] sm:text-sm sm:truncate">
                  {{ selection.homeTeamName }} vs {{ selection.awayTeamName }}
                </p>
                <p class="mt-0.5 break-words text-[10px] text-gray-400 max-[390px]:text-[9px] max-[360px]:text-[8px] sm:mt-1 sm:text-xs">
                  {{ translateEnumLabel(selection.marketType) }} -
                  {{ formatBetLabel(selection.option, selection.marketType, selection.line) }}
                </p>
              </div>

              <button
                class="shrink-0 text-[10px] font-semibold text-red-400 transition-colors hover:text-red-300 max-[390px]:text-[9px] max-[360px]:text-[8px] sm:text-xs"
                @click="ticketStore.removeSelection(selection.offerId)"
              >
                Sterge
              </button>
            </div>

            <div class="mt-1.5 flex flex-wrap items-center justify-between gap-1.5 text-[10px] text-gray-400 max-[390px]:mt-1 max-[390px]:gap-1 max-[390px]:text-[9px] max-[360px]:text-[8px] sm:mt-2 sm:gap-2 sm:text-xs">
              <span>{{ formatDateShort(selection.matchStartTime) }}</span>
              <span class="rounded bg-blue-600/20 px-1.5 py-0.5 font-semibold text-blue-300 max-[390px]:px-1.25 max-[390px]:text-[9px] max-[360px]:px-1 max-[360px]:text-[8px] sm:px-2 sm:py-1">
                {{ selection.odds.toFixed(2) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="space-y-2.5 border-t border-white/10 px-3 pb-3 pt-3 max-[390px]:space-y-2 max-[390px]:px-2.5 max-[390px]:pb-2.5 max-[390px]:pt-2.5 max-[360px]:px-2 max-[360px]:pb-2 max-[360px]:pt-2 sm:px-4 sm:pb-4 sm:pt-4 lg:shrink-0 lg:border-t-0 lg:px-0 lg:pb-0 lg:pt-0">
        <div class="space-y-2 rounded-xl border border-white/10 bg-black/40 p-2.5 max-[390px]:space-y-1.5 max-[390px]:p-2 max-[360px]:p-1.75 sm:p-3">
          <label class="block text-[10px] font-medium uppercase tracking-[0.18em] text-gray-500 max-[390px]:text-[9px] max-[390px]:tracking-[0.14em] max-[360px]:text-[8px] sm:text-xs sm:tracking-[0.2em]">
            Miza
          </label>
          <input
            :value="ticketStore.stake"
            type="number"
            class="no-spinner w-full rounded-lg border border-white/10 bg-black/60 px-2.5 py-1.5 text-[13px] text-white focus:border-blue-500 focus:outline-none max-[390px]:px-2 max-[390px]:py-1.25 max-[390px]:text-[12px] max-[360px]:px-1.75 max-[360px]:py-1 max-[360px]:text-[11px] sm:px-3 sm:py-2 sm:text-sm"
            :min="MIN_STAKE"
            :max="MAX_STAKE"
            step="1"
            @input="updateStake"
          />
          <p class="text-[11px] text-gray-400 max-[390px]:text-[10px] max-[360px]:text-[9px] sm:text-xs">Intre {{ MIN_STAKE }} si {{ MAX_STAKE }} RON</p>
        </div>

        <div class="space-y-2 rounded-xl border border-white/10 bg-black/40 p-2.5 text-[13px] max-[390px]:space-y-1.5 max-[390px]:p-2 max-[390px]:text-[12px] max-[360px]:p-1.75 max-[360px]:text-[11px] sm:p-3 sm:text-sm">
          <div class="flex items-center justify-between text-gray-400">
            <span>Cota totala</span>
            <span class="font-semibold text-white">{{ ticketStore.totalOdds.toFixed(2) }}</span>
          </div>
          <div class="flex items-center justify-between text-gray-400">
            <span>Castig potential</span>
            <span class="font-semibold text-white">{{ formatMoney(ticketStore.potentialWin) }}</span>
          </div>
          <p v-if="isWinCapped" class="text-[11px] text-amber-300 max-[390px]:text-[10px] max-[360px]:text-[9px] sm:text-xs">
            Castigul potential este plafonat la {{ formatMoney(MAX_WIN) }}.
          </p>
        </div>

        <AppButton
          class="w-full"
          :loading="placingTicket"
          :disabled="!ticketStore.selectionCount"
          @click="placeCurrentTicket"
        >
          Plaseaza biletul
        </AppButton>
      </div>
    </div>
  </Panel>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTicketStore } from '@/stores/ticket.store'
import { useToastStore } from '@/stores/toast.store'
import { useTicketActions } from '@/composables/useTicketActions'
import Panel from './Panel.vue'
import AppButton from './AppButton.vue'
import { formatMoney } from '@/utils/money.utils'
import { formatBetLabel } from '@/utils/odds.utils'
import { MAX_STAKE, MAX_WIN, MIN_STAKE } from '@/constants/betting.constants'
import { translateEnumLabel } from '@/utils/labels.utils'



const ticketStore = useTicketStore()
const toastStore = useToastStore()
const { placingTicket, placeCurrentTicket } = useTicketActions()

const rawPotentialWin = computed(() => ticketStore.stake * ticketStore.totalOdds)
const isWinCapped = computed(() => rawPotentialWin.value > MAX_WIN)
const hasTriedInvalidStake = ref(false)
const isStakeValid = computed(
  () => Number.isFinite(ticketStore.stake) && ticketStore.stake >= MIN_STAKE && ticketStore.stake <= MAX_STAKE,
)
const isPlaceDisabled = computed(
  () => !ticketStore.selectionCount || placingTicket.value || (hasTriedInvalidStake.value && !isStakeValid.value),
)

function updateStake(event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  ticketStore.stake = value
  

  if (isStakeValid.value) {
    hasTriedInvalidStake.value = false
  }
}

async function submitTicket() {
  if (!isStakeValid.value) {
    hasTriedInvalidStake.value = true
    toastStore.showError(`Miza trebuie sa fie intre ${MIN_STAKE} si ${MAX_STAKE} RON.`)
    
  }

  await placeCurrentTicket()
}
</script>

<template>
  <Panel
    class="flex h-full min-h-0 flex-col rounded-none border-0 bg-transparent p-0 shadow-none lg:h-[calc(100vh-var(--app-slip-top)-16px)] lg:overflow-hidden lg:rounded-xl lg:border lg:border-white/10 lg:bg-black/70 lg:p-4 lg:shadow-[0_18px_42px_rgba(0,0,0,0.34)]"
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

      <div class="slip-selections mt-1.5 min-h-0 flex-1 overflow-y-auto px-3 max-[390px]:mt-1 max-[390px]:px-2.5 max-[360px]:px-2 sm:px-4 lg:mt-4 lg:px-0">
        <div
          class="space-y-2 pb-2 max-[390px]:space-y-1.5 max-[390px]:pb-1.5 max-[360px]:space-y-1 max-[360px]:pb-1 lg:pb-0 lg:pr-1 lg:overscroll-y-contain"
        >
          <div
            v-for="selection in ticketStore.selections"
            :key="selection.offerId"
            class="relative min-h-[5.75rem] rounded-xl border border-white/10 bg-black/40 p-2 pr-12 max-[390px]:min-h-[5.25rem] max-[390px]:p-1.5 max-[390px]:pr-10 max-[360px]:min-h-[4.75rem] max-[360px]:p-1.25 max-[360px]:pr-9 sm:min-h-[6.25rem] sm:p-3 sm:pr-14"
          >
            <p class="min-w-0 pr-1 text-[12px] font-semibold text-white max-[390px]:text-[11px] max-[360px]:text-[10px] sm:text-sm sm:truncate">
              {{ selection.homeTeamName }} vs {{ selection.awayTeamName }}
            </p>

            <h3 class="absolute right-2 top-2 text-[15px] font-bold leading-none text-blue-300 max-[390px]:right-1.5 max-[390px]:top-1.5 max-[390px]:text-[14px] max-[360px]:right-1.25 max-[360px]:top-1.25 max-[360px]:text-[13px] sm:right-3 sm:top-3 sm:text-lg">
              {{ selection.odds.toFixed(2) }}
            </h3>

            <p class="absolute bottom-2 left-2 max-w-[calc(100%-4rem)] break-words text-[12px] font-medium leading-5 text-gray-300 max-[390px]:bottom-1.5 max-[390px]:left-1.5 max-[390px]:text-[11px] max-[390px]:leading-5 max-[360px]:bottom-1.25 max-[360px]:left-1.25 max-[360px]:text-[10px] max-[360px]:leading-5 sm:bottom-3 sm:left-3 sm:text-sm sm:leading-5">
              {{ translateEnumLabel(selection.marketType) }} -
              {{ formatBetLabel(selection.option, selection.marketType, selection.line) }}
            </p>

            <button
              class="absolute bottom-2 right-2 inline-flex h-5 w-5 items-center justify-center text-gray-400 hover:text-red-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-400/70 max-[390px]:bottom-1.5 max-[390px]:right-1.5 max-[360px]:bottom-1.25 max-[360px]:right-1.25 sm:bottom-3 sm:right-3"
              type="button"
              :aria-label="`Sterge ${selection.homeTeamName} vs ${selection.awayTeamName}`"
              @click="ticketStore.removeSelection(selection.offerId)"
            >
              <svg
                class="h-4 w-4 max-[360px]:h-3.5 max-[360px]:w-3.5 sm:h-5 sm:w-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M3 6h18" />
                <path d="M8 6V4h8v2" />
                <path d="M19 6l-1 14H6L5 6" />
                <path d="M10 11v5" />
                <path d="M14 11v5" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="space-y-2.5 border-t border-white/10 px-3 pb-3 pt-3 max-[390px]:space-y-2 max-[390px]:px-2.5 max-[390px]:pb-2.5 max-[390px]:pt-2.5 max-[360px]:px-2 max-[360px]:pb-2 max-[360px]:pt-2 sm:px-4 sm:pb-4 sm:pt-4 lg:shrink-0 lg:border-t-0 lg:px-0 lg:pb-0 lg:pt-0">
        <div class="space-y-2 bg-black/40 p-2.5 max-[390px]:space-y-1.5 max-[390px]:p-2 max-[360px]:p-1.75 sm:p-3">
          <label class="block text-[10px] font-medium uppercase tracking-[0.18em] text-gray-500 max-[390px]:text-[9px] max-[390px]:tracking-[0.14em] max-[360px]:text-[8px] sm:text-xs sm:tracking-[0.2em]">
            Miza
          </label>
          <input
            :value="ticketStore.stake"
            type="number"
            :aria-invalid="!isStakeValid"
            class="no-spinner w-full rounded-lg border border-white/10 bg-black/60 px-2.5 py-1.5 text-[13px] text-white transition-colors focus:border-blue-500 focus:outline-none aria-[invalid=true]:border-red-400/70 max-[390px]:px-2 max-[390px]:py-1.25 max-[390px]:text-[12px] max-[360px]:px-1.75 max-[360px]:py-1 max-[360px]:text-[11px] sm:px-3 sm:py-2 sm:text-sm"
            :min="MIN_STAKE"
            :max="MAX_STAKE"
            step="1"
            @input="updateStake"
          />
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
          :disabled="isPlaceDisabled"
          @click="submitTicket"
        >
          Plaseaza biletul
        </AppButton>
      </div>
    </div>
  </Panel>
</template>

<style scoped>
.slip-selections {
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
}
</style>

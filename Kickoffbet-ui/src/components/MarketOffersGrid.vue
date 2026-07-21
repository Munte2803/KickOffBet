<script setup lang="ts">
import { formatBetLabel } from '@/utils/odds.utils'
import type { EditableMarketOffer } from '@/types/match.types'
import { translateEnumLabel } from '@/utils/labels.utils'

const props = defineProps<{
  modelValue: EditableMarketOffer[]
  readonly?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: EditableMarketOffer[]] }>()

function updateOffers(nextValue: EditableMarketOffer[]) {
  emit('update:modelValue', nextValue)
}

function updateOffer(index: number, patch: Partial<EditableMarketOffer>) {
  updateOffers(
    props.modelValue.map((offer, currentIndex) => {
      if (currentIndex !== index) return offer

      const nextOdds = patch.odds
      return {
        ...offer,
        ...patch,
        odds: typeof nextOdds === 'number' && Number.isFinite(nextOdds) && nextOdds >= 1.01 ? nextOdds : offer.odds,
      }
    }),
  )
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h3 class="text-sm font-semibold text-fg">Oferte de pariere</h3>
        <p class="text-xs text-muted">
          Editeaza doar cotele existente. Structura pietelor se configureaza la crearea meciului.
        </p>
      </div>
    </div>

    <div v-if="!modelValue.length" class="rounded-xl border border-dashed border-line bg-surface px-4 py-6 text-sm text-muted">
      Nu exista oferte configurate.
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="(offer, index) in modelValue"
        :key="offer.id ?? `${offer.marketType}-${offer.option}-${offer.line ?? 'null'}-${index}`"
        class="rounded-xl border border-line bg-surface p-3"
      >
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="min-w-0">
            <p class="text-sm font-semibold text-fg">
              {{ translateEnumLabel(offer.marketType) }}
            </p>
            <p class="text-xs text-muted">
              {{ formatBetLabel(offer.option, offer.marketType, offer.line) }}
            </p>
          </div>

          <div class="grid grid-cols-1 gap-2 sm:grid-cols-3 lg:w-[360px]">
            <div class="rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-muted">
              {{ offer.marketType === 'OVER_UNDER' ? `Linie ${offer.line ?? '-'}` : 'Fara linie' }}
            </div>

            <input
              :value="offer.odds"
              :disabled="readonly"
              type="number"
              min="1.01"
              step="0.01"
              class="rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-fg focus:border-blue-500 focus:outline-none disabled:opacity-60"
              @input="updateOffer(index, { odds: Number(($event.target as HTMLInputElement).value) || 0 })"
            />

            <div class="rounded-lg border border-line bg-surface-2 px-3 py-2 text-center text-xs text-muted">
              {{ offer.active === false ? 'Inactiv' : 'Activ' }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>


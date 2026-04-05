<script setup lang="ts">
import { computed, ref } from 'vue'
import type { BetOption, MarketType } from '@/types/enums'
import type { EditableMarketOffer } from '@/types/match.types'
import { formatBetLabel } from '@/utils/odds.utils'
import { translateEnumLabel } from '@/utils/labels.utils'
import OverUnderBuilder from '@/components/OverUnderBuilder.vue'

interface OfferTemplate {
  marketType: MarketType
  option: BetOption
  line: number | null
  defaultOdds: number
}

type StandardMarketType = 'H2H' | 'DOUBLE_CHANCE' | 'BTTS'

interface StandardOfferTemplate extends OfferTemplate {
  marketType: StandardMarketType
}

const MIN_ODDS = 1.01

const standardTemplates: StandardOfferTemplate[] = [
  { marketType: 'H2H', option: 'HOME', line: null, defaultOdds: 1.8 },
  { marketType: 'H2H', option: 'DRAW', line: null, defaultOdds: 3.4 },
  { marketType: 'H2H', option: 'AWAY', line: null, defaultOdds: 2.2 },
  { marketType: 'DOUBLE_CHANCE', option: 'HOME_OR_DRAW', line: null, defaultOdds: 1.25 },
  { marketType: 'DOUBLE_CHANCE', option: 'AWAY_OR_DRAW', line: null, defaultOdds: 1.35 },
  { marketType: 'DOUBLE_CHANCE', option: 'HOME_OR_AWAY', line: null, defaultOdds: 1.18 },
  { marketType: 'BTTS', option: 'YES', line: null, defaultOdds: 1.75 },
  { marketType: 'BTTS', option: 'NO', line: null, defaultOdds: 2.0 },
]

const standardMarketTypes: StandardMarketType[] = ['H2H', 'DOUBLE_CHANCE', 'BTTS']

const offerSortOrder: Record<MarketType, Record<string, number>> = {
  H2H: { HOME: 0, DRAW: 1, AWAY: 2 },
  DOUBLE_CHANCE: { HOME_OR_DRAW: 0, AWAY_OR_DRAW: 1, HOME_OR_AWAY: 2 },
  OVER_UNDER: { OVER: 0, UNDER: 1 },
  BTTS: { YES: 0, NO: 1 },
}

const props = defineProps<{
  modelValue: EditableMarketOffer[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: EditableMarketOffer[]] }>()

const overUnderBuilderRef = ref<{ setError: (msg: string) => void } | null>(null)

const groupedTemplates = computed(() => {
  return standardTemplates.reduce<Record<StandardMarketType, StandardOfferTemplate[]>>((groups, template) => {
    groups[template.marketType].push(template)
    return groups
  }, {
    H2H: [],
    DOUBLE_CHANCE: [],
    BTTS: [],
  })
})

const sortedSelectedOffers = computed(() =>
  props.modelValue
    .map((offer, originalIndex) => ({ ...offer, originalIndex }))
    .sort((left, right) => {
      if (left.marketType !== right.marketType) {
        const marketRank: Record<MarketType, number> = {
          H2H: 0,
          DOUBLE_CHANCE: 1,
          OVER_UNDER: 2,
          BTTS: 3,
        }
        return marketRank[left.marketType] - marketRank[right.marketType]
      }

      if (left.marketType === 'OVER_UNDER' && right.marketType === 'OVER_UNDER' && left.line !== right.line) {
        return (left.line ?? 0) - (right.line ?? 0)
      }

      return (offerSortOrder[left.marketType][left.option] ?? 99) - (offerSortOrder[right.marketType][right.option] ?? 99)
    }),
)

function updateValue(nextValue: EditableMarketOffer[]) {
  emit('update:modelValue', nextValue)
}

function matchesTemplate(offer: EditableMarketOffer, template: OfferTemplate) {
  return offer.marketType === template.marketType
    && offer.option === template.option
    && (offer.line ?? null) === template.line
}

function hasOffer(template: OfferTemplate) {
  return props.modelValue.some((offer) => matchesTemplate(offer, template))
}

function getOffer(template: OfferTemplate) {
  return props.modelValue.find((offer) => matchesTemplate(offer, template))
}

function sanitizeOdds(value: string, fallback: number) {
  const nextOdds = Number(value)
  return Number.isFinite(nextOdds) && nextOdds >= MIN_ODDS ? nextOdds : fallback
}

function toggleOffer(template: OfferTemplate) {
  if (hasOffer(template)) {
    updateValue(props.modelValue.filter((offer) => !matchesTemplate(offer, template)))
    return
  }

  updateValue([
    ...props.modelValue,
    {
      marketType: template.marketType,
      option: template.option,
      line: template.line,
      odds: template.defaultOdds,
    },
  ])
}

function updateTemplateOdds(template: OfferTemplate, value: string) {
  updateValue(
    props.modelValue.map((offer) =>
      matchesTemplate(offer, template)
        ? { ...offer, odds: sanitizeOdds(value, offer.odds) }
        : offer,
    ),
  )
}

function updateSelectedOfferOdds(index: number, value: string) {
  updateValue(
    props.modelValue.map((offer, currentIndex) =>
      currentIndex === index
        ? { ...offer, odds: sanitizeOdds(value, offer.odds) }
        : offer,
    ),
  )
}

function removeOffer(index: number) {
  updateValue(props.modelValue.filter((_, currentIndex) => currentIndex !== index))
}

function addOverUnderOffer(line: number, option: 'OVER' | 'UNDER', odds: number) {
  const template: OfferTemplate = { marketType: 'OVER_UNDER', option, line, defaultOdds: odds }

  if (hasOffer(template)) {
    overUnderBuilderRef.value?.setError('Exista deja o oferta pentru aceasta combinatie line + option.')
    return
  }

  updateValue([...props.modelValue, { marketType: 'OVER_UNDER', option, line, odds }])
}
</script>

<template>
  <div class="space-y-5">
    <div>
      <h3 class="text-sm font-semibold text-white">Piata initiala a meciului</h3>
      <p class="text-xs text-gray-400">
        Selecteaza optiunile de pariere care vor fi activate in momentul in care meciul va fi creat.
       </p>
    </div>

    <div class="grid gap-4 xl:grid-cols-2">
      <div
        v-for="marketType in standardMarketTypes"
        :key="marketType"
        class="rounded-xl border border-white/10 bg-black/30 p-4"
      >
        <h4 class="text-sm font-semibold text-white">{{ translateEnumLabel(marketType) }}</h4>
        <div class="mt-3 space-y-3">
          <label
            v-for="template in groupedTemplates[marketType]"
            :key="`${template.marketType}-${template.option}`"
            class="block rounded-xl border border-white/10 bg-black/40 p-3"
          >
            <div class="grid gap-3 md:grid-cols-[auto_minmax(0,1fr)_120px] md:items-center">
              <div class="flex min-w-0 items-center gap-3">
                <input
                  :checked="hasOffer(template)"
                  type="checkbox"
                  class="h-4 w-4 rounded border-white/20 bg-black/40"
                  @change="toggleOffer(template)"
                >
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-white">
                    {{ formatBetLabel(template.option, template.marketType, null) }}
                  </p>
          <p class="text-xs text-gray-500">{{ translateEnumLabel(template.marketType) }}</p>
                </div>
              </div>

              <input
                :disabled="!hasOffer(template)"
                :value="getOffer(template)?.odds ?? template.defaultOdds"
                type="number"
                min="1.01"
                step="0.01"
                class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none disabled:opacity-40"
                @input="updateTemplateOdds(template, ($event.target as HTMLInputElement).value)"
              >
            </div>
          </label>
        </div>
      </div>

      <OverUnderBuilder ref="overUnderBuilderRef" @add="addOverUnderOffer" />
    </div>

    <div class="space-y-2">
      <div
        v-for="offer in sortedSelectedOffers"
        :key="offer.id ?? `${offer.marketType}-${offer.option}-${offer.line ?? 'null'}-${offer.originalIndex}`"
        class="rounded-xl border border-white/10 bg-black/30 p-3"
      >
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="min-w-0">
            <p class="text-sm font-semibold text-white">{{ translateEnumLabel(offer.marketType) }}</p>
            <p class="text-xs text-gray-400">{{ formatBetLabel(offer.option, offer.marketType, offer.line) }}</p>
          </div>

          <div class="grid gap-2 sm:grid-cols-[140px_120px_120px]">
            <div class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-gray-400">
              {{ offer.marketType === 'OVER_UNDER' ? `Linie ${offer.line ?? '-'}` : 'Fara linie' }}
            </div>

            <input
              :value="offer.odds"
              type="number"
              min="1.01"
              step="0.01"
              class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
              @input="updateSelectedOfferOdds(offer.originalIndex, ($event.target as HTMLInputElement).value)"
            >

            <button
              type="button"
              class="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-medium text-red-300 transition-colors hover:bg-red-500/20"
              @click="removeOffer(offer.originalIndex)"
            >
              Scoate
            </button>
          </div>
        </div>
      </div>

      <div v-if="!sortedSelectedOffers.length" class="rounded-xl border border-dashed border-white/10 bg-black/30 px-4 py-6 text-sm text-gray-400">
        Nu exista inca oferte configurate pentru meciul nou.
      </div>
    </div>
  </div>
</template>


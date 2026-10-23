import type { MarketType, BetOption } from '@/types/enums'
import { translateEnumLabel } from '@/utils/labels.utils'

/** The pick as shown on offers and tickets: "1", "X2", "Da", or "Peste 2.5" for a goals line. */
export const formatBetLabel = (option: BetOption, marketType: MarketType, line?: number | null): string => {
  if (marketType === 'OVER_UNDER' && line != null) {
    return `${translateEnumLabel(option)} ${line}`
  }

  return translateEnumLabel(option)
}

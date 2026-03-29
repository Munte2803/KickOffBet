import type { MarketType, BetOption} from '@/shared/types/enums';
import type { MarketOffer } from '@/features/matches/types/match.types';

export const formatBetLabel = (option: BetOption, marketType: MarketType, line?: number | null): string => {
  
  if (marketType === 'OVER_UNDER' && line != null) {
    return option === 'OVER' ? `Peste ${line}` : `Sub ${line}`;
  }

  const labels: Partial<Record<BetOption, string>> = {
    'HOME': '1',
    'DRAW': 'X',
    'AWAY': '2',
    'HOME_OR_DRAW': '1X',
    'AWAY_OR_DRAW': 'X2',
    'HOME_OR_AWAY': '12',
    'YES': 'Da',
    'NO': 'Nu'
  };

  return labels[option] || option;
};


export const getMarketDisplayName = (marketType: MarketType): string => {
  const displayNames: Record<MarketType, string> = {
    'H2H': 'Rezultat Final',
    'OVER_UNDER': 'Total Goluri',
    'BTTS': 'Ambele Marchează',
    'DOUBLE_CHANCE': 'Șansă Dublă'
  };

  return displayNames[marketType] || marketType;
};

export const sortMarketOffers = (offers: MarketOffer[]): MarketOffer[] => {
  const optionOrder: Partial<Record<BetOption, number>> = {
    'HOME': 1, 'DRAW': 2, 'AWAY': 3,
    'HOME_OR_DRAW': 1, 'HOME_OR_AWAY': 2, 'AWAY_OR_DRAW': 3,
    'OVER': 1, 'UNDER': 2,
    'YES': 1, 'NO': 2
  };

  return [...offers].sort((a, b) => {
    
    const lineA = a.line ?? 0;
    const lineB = b.line ?? 0;
    if (lineA !== lineB) return lineA - lineB;


    const orderA = optionOrder[a.option] ?? 99;
    const orderB = optionOrder[b.option] ?? 99;
    return orderA - orderB;
  });
};
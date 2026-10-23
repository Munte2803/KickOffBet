
export const formatMoney = (amount: number | string | undefined | null): string => {
  if (amount === undefined || amount === null) return "0,00 RON";
  
  const value = typeof amount === 'string' ? parseFloat(amount) : amount;

  return new Intl.NumberFormat('ro-RO', {
    style: 'currency',
    currency: 'RON',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value);
};
/** Rounds an amount to whole bani: the backend accepts at most 2 decimals (@Digits(fraction = 2)). */
export const roundMoney = (amount: number): number => Math.round(amount * 100) / 100;

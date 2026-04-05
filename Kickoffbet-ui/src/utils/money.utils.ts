
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
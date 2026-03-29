
const fullDateFormatter = new Intl.DateTimeFormat('ro-RO', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit'
});

const shortDateFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit'
});

const timeOnlyFormatter = new Intl.DateTimeFormat('ro-RO', {
  hour: '2-digit',
  minute: '2-digit'
});

const dayMonthFormatter = new Intl.DateTimeFormat('ro-RO', {
  day: '2-digit',
  month: '2-digit'
});

export const formatDateTime = (iso: string): string => {
  if (!iso) return '';
  const date = new Date(iso);
  return fullDateFormatter.format(date).replace(',', ' la');
};

export const formatDateShort = (iso: string): string => {
  if (!iso) return '';
  const date = new Date(iso);
  return shortDateFormatter.format(date);
};


export const formatTime = (iso: string): string => {
  if (!iso) return '';
  const date = new Date(iso);
  return timeOnlyFormatter.format(date);
};

export const formatDayMonth = (iso: string): string => {
  if (!iso) return '';
  const date = new Date(iso);
  return dayMonthFormatter.format(date);
};

export const isToday = (iso: string): boolean => {
  if (!iso) return false;
  const date = new Date(iso);
  const today = new Date();
  return date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear();
};


export const formatForInput = (iso: string): string => {
  if (!iso) return '';
  const date = new Date(iso);
  return date.toISOString().slice(0, 16);
};


export const toApiDate = (date: Date | string): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toISOString(); 
};
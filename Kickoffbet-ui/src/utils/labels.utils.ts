export const enumLabelMap: Record<string, string> = {
  SCHEDULED: 'Programat',
  LIVE: 'In direct',
  FINISHED: 'Terminat',
  POSTPONED: 'Amanat',
  CANCELLED: 'Anulat',
  SUSPENDED: 'Suspendat',
  UNKNOWN: 'Necunoscut',
  PENDING: 'In asteptare',
  PENDING_VERIFICATION: 'Verificare in asteptare',
  WON: 'Castigat',
  LOST: 'Pierdut',
  ACTIVE: 'Activ',
  INACTIVE: 'Inactiv',
  DECLINED: 'Respins',
  DEACTIVATED: 'Dezactivat',
  COMPLETED: 'Finalizat',
  REJECTED: 'Respins',
  DEPOSIT: 'Depunere',
  WITHDRAWAL: 'Retragere',
  PAYOUT: 'Castig platit',
  REFUND: 'Rambursare',
  BET: 'Pariu',
  USER: 'Utilizator',
  ADMIN: 'Administrator',
  H2H: 'Rezultat final',
  OVER_UNDER: 'Total goluri',
  BTTS: 'Ambele marcheaza',
  DOUBLE_CHANCE: 'Sansa dubla',
  HOME: '1',
  DRAW: 'X',
  AWAY: '2',
  OVER: 'Peste',
  UNDER: 'Sub',
  YES: 'Da',
  NO: 'Nu',
  HOME_OR_DRAW: '1X',
  AWAY_OR_DRAW: 'X2',
  HOME_OR_AWAY: '12',
}

export function translateEnumLabel(value?: string | null) {
  if (!value) return ''
  return enumLabelMap[value] ?? value
}

export function translateActiveLabel(active: boolean) {
  return active ? 'Activ' : 'Inactiv'
}

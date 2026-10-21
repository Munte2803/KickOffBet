import { describe, expect, it } from 'vitest'
import { TICKET_PLACEMENT_FALLBACK, translateTicketPlacementError } from '@/api/error-messages'

describe('translateTicketPlacementError', () => {
  it.each([
    ['Odds changed: 1,80 → 2,05', 'Cota s-a modificat: 1.80 → 2.05. Verifica biletul si plaseaza-l din nou.'],
    ['Offer 3f1c-aa is no longer active', 'O cota din bilet nu mai este activa. Elimin-o si incearca din nou.'],
    ['Match 3f1c-aa has already started', 'Un meci din bilet a inceput deja. Elimina selectia si incearca din nou.'],
    [
      'H2H and DOUBLE_CHANCE cannot be combined for match 3f1c-aa',
      'Rezultatul final si sansa dubla nu pot fi combinate pentru acelasi meci.',
    ],
  ])('translates the validator message "%s"', (serverMessage, expected) => {
    expect(translateTicketPlacementError(serverMessage)).toBe(expected)
  })

  it('keeps an unknown reason next to the generic text and falls back without one', () => {
    expect(translateTicketPlacementError('Stake too high')).toBe(`${TICKET_PLACEMENT_FALLBACK} (Stake too high)`)
    expect(translateTicketPlacementError(undefined)).toBe(TICKET_PLACEMENT_FALLBACK)
  })
})

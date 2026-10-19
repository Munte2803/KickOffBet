import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTicketStore } from '@/stores/ticket.store'
import { MAX_TICKET_SELECTIONS, MAX_WIN } from '@/constants/betting.constants'
import { makeMatch, makeOffer } from '@/test/factories'

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('ticket store (bet slip)', () => {
  it('starts empty with neutral totals and a default stake of 10', () => {
    const store = useTicketStore()
    expect(store.selections).toEqual([])
    expect(store.selectionCount).toBe(0)
    expect(store.totalOdds).toBe(1)
    expect(store.stake).toBe(10)
    expect(store.potentialWin).toBe(10)
  })

  it('adds a selection with the match context and offer details', () => {
    const store = useTicketStore()
    const match = makeMatch({ homeTeamLogo: 'h.png', awayTeamLogo: 'a.png' })
    const offer = makeOffer({ id: 'o1', option: 'HOME', odds: 1.85 })

    expect(store.toggleSelection(match, offer)).toEqual({ ok: true })
    expect(store.selections).toEqual([
      {
        offerId: 'o1',
        matchId: match.id,
        homeTeamName: 'Arsenal FC',
        awayTeamName: 'Chelsea FC',
        homeTeamLogo: 'h.png',
        awayTeamLogo: 'a.png',
        matchStartTime: match.startTime,
        marketType: 'H2H',
        option: 'HOME',
        line: null,
        odds: 1.85,
      },
    ])
    expect(store.isSelected('o1')).toBe(true)
    expect(store.isSelected('other')).toBe(false)
  })

  it('toggling the same offer again removes it', () => {
    const store = useTicketStore()
    const match = makeMatch()
    const offer = makeOffer({ id: 'o1' })
    store.toggleSelection(match, offer)
    expect(store.toggleSelection(match, offer)).toEqual({ ok: true })
    expect(store.selectionCount).toBe(0)
  })

  it('rejects inactive offers without changing the slip', () => {
    const store = useTicketStore()
    const result = store.toggleSelection(makeMatch(), makeOffer({ id: 'o1', active: false }))
    expect(result).toEqual({ ok: false, reason: 'Cota nu mai este activa.' })
    expect(store.selectionCount).toBe(0)
  })

  it('replaces a selection in the same market of the same match', () => {
    const store = useTicketStore()
    const match = makeMatch()
    store.toggleSelection(match, makeOffer({ id: 'home', option: 'HOME', odds: 1.8 }))
    store.toggleSelection(match, makeOffer({ id: 'draw', option: 'DRAW', odds: 3.4 }))

    expect(store.selections.map((s) => s.offerId)).toEqual(['draw'])
    expect(store.totalOdds).toBe(3.4)
  })

  it('replaces an over/under selection on the same match even with a different line', () => {
    const store = useTicketStore()
    const match = makeMatch()
    store.toggleSelection(match, makeOffer({ id: 'o15', marketType: 'OVER_UNDER', option: 'OVER', line: 1.5 }))
    store.toggleSelection(match, makeOffer({ id: 'u35', marketType: 'OVER_UNDER', option: 'UNDER', line: 3.5 }))
    expect(store.selections.map((s) => s.offerId)).toEqual(['u35'])
    expect(store.selections[0].line).toBe(3.5)
  })

  it('treats 1X2 and double chance on the same match as mutually exclusive', () => {
    const store = useTicketStore()
    const match = makeMatch()
    store.toggleSelection(match, makeOffer({ id: 'home', marketType: 'H2H', option: 'HOME' }))
    store.toggleSelection(match, makeOffer({ id: 'dc', marketType: 'DOUBLE_CHANCE', option: 'HOME_OR_DRAW' }))
    expect(store.selections.map((s) => s.offerId)).toEqual(['dc'])

    store.toggleSelection(match, makeOffer({ id: 'away', marketType: 'H2H', option: 'AWAY' }))
    expect(store.selections.map((s) => s.offerId)).toEqual(['away'])
  })

  it('keeps different, compatible markets of the same match together', () => {
    const store = useTicketStore()
    const match = makeMatch()
    store.toggleSelection(match, makeOffer({ id: 'home', marketType: 'H2H', option: 'HOME', odds: 2 }))
    store.toggleSelection(match, makeOffer({ id: 'btts', marketType: 'BTTS', option: 'YES', odds: 1.5 }))
    store.toggleSelection(match, makeOffer({ id: 'ou', marketType: 'OVER_UNDER', option: 'OVER', line: 2.5, odds: 1.9 }))
    expect(store.selections.map((s) => s.offerId)).toEqual(['home', 'btts', 'ou'])
    expect(store.getSelectionsForMatch(match.id)).toHaveLength(3)
  })

  it('keeps the same market on different matches', () => {
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a', matchId: 'm1' }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'b', matchId: 'm2' }))
    expect(store.selectionCount).toBe(2)
    expect(store.getSelectionsForMatch('m1').map((s) => s.offerId)).toEqual(['a'])
    expect(store.getSelectionsForMatch('m2').map((s) => s.offerId)).toEqual(['b'])
    expect(store.getSelectionsForMatch('m3')).toEqual([])
  })

  it(`enforces at most ${MAX_TICKET_SELECTIONS} selections, but still allows replacements at the limit`, () => {
    const store = useTicketStore()
    for (let i = 0; i < MAX_TICKET_SELECTIONS; i += 1) {
      expect(store.toggleSelection(makeMatch({ id: `m${i}` }), makeOffer({ id: `o${i}`, matchId: `m${i}` })).ok).toBe(true)
    }

    const overflow = store.toggleSelection(makeMatch({ id: 'extra' }), makeOffer({ id: 'extra-o', matchId: 'extra' }))
    expect(overflow).toEqual({ ok: false, reason: `Poti avea cel mult ${MAX_TICKET_SELECTIONS} selectii pe un bilet.` })
    expect(store.selectionCount).toBe(MAX_TICKET_SELECTIONS)

    const replacement = store.toggleSelection(makeMatch({ id: 'm0' }), makeOffer({ id: 'o0-draw', matchId: 'm0', option: 'DRAW' }))
    expect(replacement).toEqual({ ok: true })
    expect(store.selectionCount).toBe(MAX_TICKET_SELECTIONS)
    expect(store.isSelected('o0')).toBe(false)
    expect(store.isSelected('o0-draw')).toBe(true)
  })

  it('computes total odds as the product of selection odds', () => {
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a', odds: 1.5 }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'b', odds: 2 }))
    store.toggleSelection(makeMatch({ id: 'm3' }), makeOffer({ id: 'c', odds: 3.1 }))
    expect(store.totalOdds).toBeCloseTo(9.3, 10)
  })

  it('computes potential winnings as stake x total odds rounded to 2 decimals', () => {
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a', odds: 1.37 }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'b', odds: 2.11 }))
    store.stake = 15
    // 15 * 1.37 * 2.11 = 43.3605
    expect(store.potentialWin).toBe(43.36)
  })

  it(`caps potential winnings at ${MAX_WIN}`, () => {
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a', odds: 100 }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'b', odds: 100 }))
    store.stake = 50
    expect(store.potentialWin).toBe(MAX_WIN)
  })

  it('removeSelection removes by offer id and ignores unknown ids', () => {
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a' }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'b' }))
    store.removeSelection('unknown')
    expect(store.selectionCount).toBe(2)
    store.removeSelection('a')
    expect(store.selections.map((s) => s.offerId)).toEqual(['b'])
  })

  it('clearSelections empties the slip but keeps the stake', () => {
    const store = useTicketStore()
    store.stake = 25
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a' }))
    store.clearSelections()
    expect(store.selectionCount).toBe(0)
    expect(store.totalOdds).toBe(1)
    expect(store.stake).toBe(25)
  })
})

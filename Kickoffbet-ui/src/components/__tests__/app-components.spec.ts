import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { makeMatch, makeOffer, makeProfile, makeTicket, makeTransaction } from '@/test/factories'
import { mountWithApp } from '@/test/mount'

const api = vi.hoisted(() => ({
  placeTicket: vi.fn(),
  getProfile: vi.fn(),
}))

vi.mock('@/api/tickets.api', () => ({ placeTicket: api.placeTicket }))
vi.mock('@/api/profile.api', () => ({ getProfile: api.getProfile }))
vi.mock('@/api/axios', () => ({ default: { post: vi.fn() } }))

import BettingSlip from '@/components/BettingSlip.vue'
import MatchCard from '@/components/MatchCard.vue'
import AppConfirmDialog from '@/components/AppConfirmDialog.vue'
import TransactionRow from '@/components/TransactionRow.vue'
import { useTicketStore } from '@/stores/ticket.store'
import { useToastStore } from '@/stores/toast.store'
import { useAuthStore } from '@/stores/auth.store'
import { useConfirmStore } from '@/stores/confirm.store'

const norm = (value: string) => value.replace(/\s+/g, ' ')
const squash = (value: string) => value.replace(/\s+/g, '')

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  api.placeTicket.mockReset()
  api.getProfile.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

function loginUser() {
  useAuthStore().setAuth('access', 'refresh', { email: 'a@b.ro', firstName: 'Ana', lastName: 'Pop', role: 'USER', balance: 100 })
}

describe('BettingSlip', () => {
  async function mountSlip(setup?: () => void) {
    const pinia = createPinia()
    setActivePinia(pinia)
    setup?.()
    return mountWithApp(BettingSlip, { pinia })
  }

  function addTwoSelections() {
    const store = useTicketStore()
    store.toggleSelection(
      makeMatch({ id: 'm1', homeTeamName: 'Arsenal', awayTeamName: 'Chelsea' }),
      makeOffer({ id: 'o1', matchId: 'm1', option: 'HOME', odds: 1.5 }),
    )
    store.toggleSelection(
      makeMatch({ id: 'm2', homeTeamName: 'Rapid', awayTeamName: 'Dinamo' }),
      makeOffer({ id: 'o2', matchId: 'm2', marketType: 'OVER_UNDER', option: 'OVER', line: 2.5, odds: 2 }),
    )
  }

  const placeButton = (wrapper: Awaited<ReturnType<typeof mountSlip>>['wrapper']) =>
    wrapper.findAll('button').find((b) => b.text().includes('Plaseaza biletul'))!

  it('shows the empty state when there are no selections', async () => {
    const { wrapper } = await mountSlip()
    expect(wrapper.text()).toContain('Selecteaza cote active pentru a construi biletul.')
    expect(wrapper.text()).toContain('0 selectii')
    expect(placeButton(wrapper)).toBeUndefined()
  })

  it('lists selections with teams, market label and odds', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    const text = norm(wrapper.text())
    expect(text).toContain('Arsenal vs Chelsea')
    expect(text).toContain('Rezultat final - 1')
    expect(text).toContain('1.50')
    expect(text).toContain('Rapid vs Dinamo')
    expect(text).toContain('Total goluri - Peste 2.5')
    expect(text).toContain('2 selectii')
  })

  it('shows total odds and potential win for the current stake', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    const text = norm(wrapper.text())
    expect(squash(text)).toContain('Cotatotala3.00')
    expect(squash(text)).toContain('Castigpotential30,00RON') // default stake 10 x 3.00
  })

  it('updates the stake (and totals) from the input', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    await wrapper.find('input[type="number"]').setValue('25')
    expect(useTicketStore().stake).toBe(25)
    expect(squash(wrapper.text())).toContain('Castigpotential75,00RON')
  })

  it('flags an out-of-range stake as invalid', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    const input = wrapper.find('input[type="number"]')
    expect(input.attributes('aria-invalid')).toBe('false')
    await input.setValue('1')
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('removes a single selection with its delete button', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    await wrapper.find('button[aria-label="Sterge Arsenal vs Chelsea"]').trigger('click')
    expect(useTicketStore().selections.map((s) => s.offerId)).toEqual(['o2'])
    expect(wrapper.text()).not.toContain('Arsenal vs Chelsea')
  })

  it('clears all selections', async () => {
    const { wrapper } = await mountSlip(addTwoSelections)
    await wrapper.findAll('button').find((b) => b.text() === 'Goleste')!.trigger('click')
    expect(useTicketStore().selectionCount).toBe(0)
    expect(wrapper.text()).toContain('Selecteaza cote active pentru a construi biletul.')
  })

  it('warns when the potential win is capped', async () => {
    const { wrapper } = await mountSlip(() => {
      const store = useTicketStore()
      store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'a', odds: 1000 }))
      store.stake = 500
    })
    const text = norm(wrapper.text())
    expect(text).toContain('Castigul potential este plafonat la 200.000,00 RON.')
    expect(squash(text)).toContain('Castigpotential200.000,00RON')
  })

  it('places the ticket and navigates to it', async () => {
    const { wrapper, router } = await mountSlip(() => {
      loginUser()
      addTwoSelections()
    })
    api.placeTicket.mockResolvedValue(makeTicket({ id: 't-1' }))
    api.getProfile.mockResolvedValue(makeProfile({ balance: 90 }))

    await placeButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.placeTicket).toHaveBeenCalledWith({
      stake: 10,
      selections: [
        { marketOfferId: 'o1', oddsAtPlacement: 1.5 },
        { marketOfferId: 'o2', oddsAtPlacement: 2 },
      ],
    })
    expect(router.currentRoute.value.fullPath).toBe('/tickets/t-1')
    expect(useTicketStore().selectionCount).toBe(0)
  })

  it('rejects an invalid stake with a single error toast and disables the button until fixed', async () => {
    const { wrapper } = await mountSlip(() => {
      loginUser()
      addTwoSelections()
    })
    const input = wrapper.find('input[type="number"]')
    await input.setValue('1')

    await placeButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.placeTicket).not.toHaveBeenCalled()
    const toasts = useToastStore().toasts
    expect(toasts.map((t) => t.message)).toEqual(['Miza trebuie sa fie intre 2 si 50000 RON.'])
    expect(placeButton(wrapper).attributes('disabled')).toBeDefined()

    await input.setValue('5')
    expect(placeButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('sends anonymous users to login with a redirect back', async () => {
    const { wrapper, router } = await mountSlip(addTwoSelections)
    await placeButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.placeTicket).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
  })
})

describe('MatchCard', () => {
  async function mountCard(match = makeMatch(), props: Record<string, unknown> = {}) {
    return mountWithApp(MatchCard, { props: { match, ...props } })
  }

  it('shows league, team tags, status and kick-off time', async () => {
    const { wrapper } = await mountCard(makeMatch({ homeTeamTla: null, homeTeamName: 'Real Madrid' }))
    const text = norm(wrapper.text())
    expect(text).toContain('Premier League')
    expect(text).toContain('RM')
    expect(text).toContain('CHE')
    expect(text).toContain('Programat')
    expect(text).toContain('01.07.2026, 21:00') // 18:00 UTC in Bucharest summer time
  })

  it('shows the score only when both full-time goals are known', async () => {
    expect(norm((await mountCard(makeMatch({ ftHome: 2, ftAway: 0, status: 'FINISHED' }))).wrapper.text())).toContain('2 - 0')
    expect(norm((await mountCard(makeMatch({ ftHome: 2, ftAway: null }))).wrapper.text())).not.toContain('2 -')
  })

  it('renders 1 / X / 2 odds pills for active offers only', async () => {
    const match = makeMatch()
    match.oddX = { ...match.oddX!, active: false }
    const { wrapper } = await mountCard(match)
    const pills = wrapper.findAllComponents({ name: 'OddPill' })
    expect(pills.map((p) => p.props('label'))).toEqual(['1', '2'])
    expect(pills.map((p) => p.props('odds'))).toEqual([1.8, 4.2])
  })

  it('hides the odds row when no 1X2 offer is active', async () => {
    const { wrapper } = await mountCard(makeMatch({ odd1: null, oddX: null, odd2: null }))
    expect(wrapper.findAllComponents({ name: 'OddPill' })).toHaveLength(0)
  })

  it('adds an odd to the slip without opening the match, and marks it selected', async () => {
    const { wrapper, router } = await mountCard()
    const pill = wrapper.findAllComponents({ name: 'OddPill' })[0]
    await pill.trigger('click')

    expect(useTicketStore().isSelected('match-1-1')).toBe(true)
    expect(router.currentRoute.value.fullPath).toBe('/')
    expect(wrapper.findAllComponents({ name: 'OddPill' })[0].props('selected')).toBe(true)

    await wrapper.findAllComponents({ name: 'OddPill' })[2].trigger('click')
    expect(useTicketStore().selections.map((s) => s.offerId)).toEqual(['match-1-2'])
  })

  it('opens the match detail when the card is clicked', async () => {
    const { wrapper, router } = await mountCard()
    await wrapper.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/match/match-1')
  })

  it('opens the team page from a team tag without opening the match', async () => {
    const { wrapper, router } = await mountCard()
    await wrapper.findAll('button').find((b) => b.text() === 'CHE')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/teams/team-away')
  })

  it('links the league unless linkLeague is false', async () => {
    const linked = await mountCard()
    await linked.wrapper.findAll('button').find((b) => b.text() === 'Premier League')!.trigger('click')
    await flushPromises()
    expect(linked.router.currentRoute.value.fullPath).toBe('/leagues/PL')

    const unlinked = await mountCard(makeMatch(), { linkLeague: false })
    expect(unlinked.wrapper.findAll('button').some((b) => b.text() === 'Premier League')).toBe(false)
    expect(unlinked.wrapper.text()).toContain('Premier League')
  })
})

describe('AppConfirmDialog', () => {
  async function mountDialog() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(AppConfirmDialog, { global: { plugins: [pinia] }, attachTo: document.body })
    return { wrapper, store: useConfirmStore() }
  }

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('is hidden until a question is asked', async () => {
    const { wrapper, store } = await mountDialog()
    expect(wrapper.text()).toBe('')
    void store.ask({ title: 'Anuleaza biletul', message: 'Sigur?', description: 'Nu poate fi refacut.' })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('Anuleaza biletul')
    expect(text).toContain('Sigur?')
    expect(text).toContain('Nu poate fi refacut.')
    expect(text).toContain('Confirma')
    expect(text).toContain('Renunta')
  })

  it('resolves true when confirmed', async () => {
    const { wrapper, store } = await mountDialog()
    const answer = store.ask({ title: 'T', message: 'M', confirmLabel: 'Da, sterge', variant: 'danger' })
    await flushPromises()
    const confirmButton = wrapper.findAll('button').find((b) => b.text() === 'Da, sterge')!
    expect(confirmButton.classes()).toContain('bg-red-600')
    await confirmButton.trigger('click')
    await expect(answer).resolves.toBe(true)
  })

  it('resolves false when cancelled', async () => {
    const { wrapper, store } = await mountDialog()
    const answer = store.ask({ title: 'T', message: 'M', cancelLabel: 'Inapoi' })
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === 'Inapoi')!.trigger('click')
    await expect(answer).resolves.toBe(false)
  })

  it('cancels on Escape and on backdrop click, but not on clicks inside the dialog', async () => {
    const { wrapper, store } = await mountDialog()

    const viaEscape = store.ask({ title: 'T', message: 'M' })
    await flushPromises()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await expect(viaEscape).resolves.toBe(false)

    const viaBackdrop = store.ask({ title: 'T', message: 'Inside' })
    await flushPromises()
    await wrapper.find('p.text-sm').trigger('click')
    expect(store.open).toBe(true)
    await wrapper.find('.fixed').trigger('click')
    await expect(viaBackdrop).resolves.toBe(false)
  })

  it('stops listening for Escape once closed', async () => {
    const { store } = await mountDialog()
    const answer = store.ask({ title: 'T', message: 'M' })
    await flushPromises()
    store.confirm()
    await expect(answer).resolves.toBe(true)
    await flushPromises()

    const cancel = vi.spyOn(store, 'cancel')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(cancel).not.toHaveBeenCalled()
  })
})

describe('TransactionRow', () => {
  it('shows type, status, date and formatted amount', async () => {
    const { wrapper } = await mountWithApp(TransactionRow, {
      props: { transaction: makeTransaction({ amount: 1250.5, transactionType: 'WITHDRAWAL', status: 'PENDING' }) },
    })
    const text = norm(wrapper.text())
    expect(text).toContain('Retragere')
    expect(text).toContain('In asteptare')
    expect(text).toContain('01.07.2026, 12:30')
    expect(text).toContain('1.250,50 RON')
    expect(text).not.toContain('ana@example.com')
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('shows the user email when requested', async () => {
    const { wrapper } = await mountWithApp(TransactionRow, { props: { transaction: makeTransaction(), showEmail: true } })
    expect(wrapper.text()).toContain('ana@example.com -')
  })

  it('links to the given detail route', async () => {
    const { wrapper } = await mountWithApp(TransactionRow, {
      props: { transaction: makeTransaction({ id: 'tx-9' }), routeName: 'admin-transaction-detail', linkLabel: 'Detalii' },
    })
    const link = wrapper.find('a')
    expect(link.text()).toBe('Detalii')
    expect(link.attributes('href')).toBe('/admin/transactions/tx-9')
  })

  it('renders slot content', async () => {
    const { wrapper } = await mountWithApp(TransactionRow, {
      props: { transaction: makeTransaction() },
      slots: { default: '<button class="extra">Aproba</button>' },
    })
    expect(wrapper.find('button.extra').text()).toBe('Aproba')
  })
})

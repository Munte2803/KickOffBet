import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { makeMatch, makeOffer, makeProfile, makeTicket } from '@/test/factories'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  route: { fullPath: '/match/m1?tab=odds' },
  placeTicket: vi.fn(),
  getProfile: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))
vi.mock('@/api/tickets.api', () => ({ placeTicket: mocks.placeTicket }))
vi.mock('@/api/profile.api', () => ({ getProfile: mocks.getProfile }))
vi.mock('@/api/axios', () => ({ default: { post: vi.fn() } }))

import { useTicketActions } from '@/composables/useTicketActions'
import { ApiError } from '@/api/api-error'
import { useAuthStore } from '@/stores/auth.store'
import { useTicketStore } from '@/stores/ticket.store'
import { useToastStore } from '@/stores/toast.store'
import { queryClient } from '@/query/query-client'

function login(balance = 100) {
  useAuthStore().setAuth('access', 'refresh', { email: 'a@b.ro', firstName: 'Ana', lastName: 'Pop', role: 'USER', balance })
}

function addSelections() {
  const ticket = useTicketStore()
  ticket.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'o1', matchId: 'm1', odds: 1.5 }))
  ticket.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'o2', matchId: 'm2', option: 'AWAY', odds: 2 }))
}

const lastToast = () => useToastStore().toasts.at(-1)

beforeEach(() => {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  mocks.push.mockReset().mockResolvedValue(undefined)
  mocks.placeTicket.mockReset()
  mocks.getProfile.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useTicketActions.toggleOffer', () => {
  it('toggles the offer in the slip', () => {
    const { toggleOffer } = useTicketActions()
    const result = toggleOffer(makeMatch(), makeOffer({ id: 'o1' }))
    expect(result.ok).toBe(true)
    expect(useTicketStore().isSelected('o1')).toBe(true)
    expect(useToastStore().toasts).toHaveLength(0)
  })

  it('shows the rejection reason as an error toast', () => {
    const { toggleOffer } = useTicketActions()
    const result = toggleOffer(makeMatch(), makeOffer({ id: 'o1', active: false }))
    expect(result.ok).toBe(false)
    expect(lastToast()).toMatchObject({ type: 'error', message: 'Cota nu mai este activa.' })
  })
})

describe('useTicketActions.placeCurrentTicket', () => {
  it('sends anonymous users to login with a redirect back', async () => {
    addSelections()
    const { placeCurrentTicket } = useTicketActions()

    await expect(placeCurrentTicket()).resolves.toBeNull()

    expect(lastToast()).toMatchObject({ type: 'info', message: 'Autentifica-te pentru a plasa un bilet.' })
    expect(mocks.push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/match/m1?tab=odds' } })
    expect(mocks.placeTicket).not.toHaveBeenCalled()
  })

  it('requires at least one selection', async () => {
    login()
    const { placeCurrentTicket } = useTicketActions()
    await expect(placeCurrentTicket()).resolves.toBeNull()
    expect(lastToast()).toMatchObject({ type: 'error', message: 'Adauga cel putin o selectie.' })
    expect(mocks.placeTicket).not.toHaveBeenCalled()
  })

  it.each([1.99, 50000.01, Number.NaN])('rejects an out-of-range stake (%s)', async (stake) => {
    login()
    addSelections()
    useTicketStore().stake = stake
    const { placeCurrentTicket } = useTicketActions()

    await expect(placeCurrentTicket()).resolves.toBeNull()
    expect(lastToast()).toMatchObject({ type: 'error', message: 'Miza trebuie sa fie intre 2 si 50000 RON.' })
    expect(mocks.placeTicket).not.toHaveBeenCalled()
  })

  it('places the ticket with the backend contract payload, refreshes the profile and opens the ticket', async () => {
    login(100)
    addSelections()
    useTicketStore().stake = 20
    const ticket = makeTicket({ id: 't-77' })
    mocks.placeTicket.mockResolvedValue(ticket)
    mocks.getProfile.mockResolvedValue(makeProfile({ balance: 80 }))
    const { placeCurrentTicket, placingTicket } = useTicketActions()

    const pending = placeCurrentTicket()
    expect(placingTicket.value).toBe(true)
    await expect(pending).resolves.toEqual(ticket)

    expect(mocks.placeTicket).toHaveBeenCalledWith({
      stake: 20,
      selections: [
        { marketOfferId: 'o1', oddsAtPlacement: 1.5 },
        { marketOfferId: 'o2', oddsAtPlacement: 2 },
      ],
    })
    expect(useTicketStore().selectionCount).toBe(0)
    expect(useAuthStore().balance).toBe(80)
    expect(lastToast()).toMatchObject({ type: 'success', message: 'Bilet plasat cu succes.' })
    expect(mocks.push).toHaveBeenCalledWith({ name: 'ticket-detail', params: { id: 't-77' } })
    expect(placingTicket.value).toBe(false)
  })

  it('marks the cached tickets, wallet history and profile as stale after placing', async () => {
    login()
    addSelections()
    mocks.placeTicket.mockResolvedValue(makeTicket())
    mocks.getProfile.mockResolvedValue(makeProfile())
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await useTicketActions().placeCurrentTicket()

    const keys = invalidate.mock.calls.map(([filters]) => filters?.queryKey)
    expect(keys).toEqual(expect.arrayContaining([['tickets'], ['wallet-transactions'], ['profile']]))
    invalidate.mockRestore()
  })

  it('refuses to place a slip that still holds unavailable selections', async () => {
    login()
    addSelections()
    useTicketStore().selections[0].unavailable = true

    await expect(useTicketActions().placeCurrentTicket()).resolves.toBeNull()

    expect(mocks.placeTicket).not.toHaveBeenCalled()
    expect(lastToast()).toMatchObject({ type: 'error', message: 'Elimina selectiile care nu mai sunt disponibile.' })
  })

  it('warns when the potential win was capped', async () => {
    login(100000)
    const store = useTicketStore()
    store.toggleSelection(makeMatch({ id: 'm1' }), makeOffer({ id: 'o1', odds: 500 }))
    store.toggleSelection(makeMatch({ id: 'm2' }), makeOffer({ id: 'o2', odds: 500 }))
    store.stake = 10
    mocks.placeTicket.mockResolvedValue(makeTicket())
    mocks.getProfile.mockResolvedValue(makeProfile())

    await useTicketActions().placeCurrentTicket()
    expect(lastToast()?.message).toBe('Bilet plasat. Castigul potential a fost plafonat.')
  })

  it('falls back to deducting the stake locally when the profile refresh fails', async () => {
    login(100)
    addSelections()
    useTicketStore().stake = 30
    mocks.placeTicket.mockResolvedValue(makeTicket())
    mocks.getProfile.mockRejectedValue(new Error('offline'))

    await useTicketActions().placeCurrentTicket()
    expect(useAuthStore().balance).toBe(70)
  })

  it('never drives the local balance negative', async () => {
    login(10)
    addSelections()
    useTicketStore().stake = 30
    mocks.placeTicket.mockResolvedValue(makeTicket())
    mocks.getProfile.mockRejectedValue(new Error('offline'))

    await useTicketActions().placeCurrentTicket()
    expect(useAuthStore().balance).toBe(0)
  })

  it('keeps the slip and stays on the page when placement fails for a reason other than money', async () => {
    login()
    addSelections()
    mocks.placeTicket.mockRejectedValue(new ApiError('Cota nu mai este activa.', 'TICKET_PLACEMENT_ERROR'))
    const { placeCurrentTicket, placingTicket } = useTicketActions()

    await expect(placeCurrentTicket()).resolves.toBeNull()
    expect(lastToast()).toMatchObject({ type: 'error', message: 'Cota nu mai este activa.' })
    expect(useTicketStore().selectionCount).toBe(2)
    expect(placingTicket.value).toBe(false)
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('sends the user to the wallet only when funds are insufficient', async () => {
    login()
    addSelections()
    mocks.placeTicket.mockRejectedValue(new ApiError('Fonduri insuficiente.', 'INSUFFICIENT_FUNDS'))
    const { placeCurrentTicket } = useTicketActions()

    await expect(placeCurrentTicket()).resolves.toBeNull()
    expect(useTicketStore().selectionCount).toBe(2)
    expect(mocks.push).toHaveBeenCalledWith({ name: 'wallet', query: { ref: '/match/m1?tab=odds' } })
  })
})

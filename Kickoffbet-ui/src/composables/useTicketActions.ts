import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { useTicketStore } from '@/stores/ticket.store'
import { useToastStore } from '@/stores/toast.store'
import { placeTicket } from '@/api/tickets.api'
import { getProfile } from '@/api/profile.api'
import type { MatchList, MatchDetail, MarketOffer } from '@/types/match.types'
import { MAX_STAKE, MAX_WIN, MIN_STAKE } from '@/constants/betting.constants'

export function useTicketActions() {
  const authStore = useAuthStore()
  const ticketStore = useTicketStore()
  const toastStore = useToastStore()
  const router = useRouter()
  const route = useRoute()
  const placingTicket = ref(false)

  function toggleOffer(match: MatchList | MatchDetail, offer: MarketOffer) {
    const result = ticketStore.toggleSelection(match, offer)
    if (!result.ok && result.reason) {
      toastStore.showError(result.reason)
    }
    return result
  }

  async function placeCurrentTicket() {
    if (!authStore.isAuthenticated) {
      toastStore.showInfo('Autentifica-te pentru a plasa un bilet.')
      await router.push({ name: 'login', query: { redirect: route.fullPath } })
      return null
    }

    if (!ticketStore.selections.length) {
      toastStore.showError('Adauga cel putin o selectie.')
      return null
    }

    if (!Number.isFinite(ticketStore.stake) || ticketStore.stake < MIN_STAKE || ticketStore.stake > MAX_STAKE) {
      toastStore.showError(`Miza trebuie sa fie intre ${MIN_STAKE} si ${MAX_STAKE} RON.`)
      return null
    }

    placingTicket.value = true

    try {
      const placedStake = ticketStore.stake
      const ticket = await placeTicket({
        stake: ticketStore.stake,
        selections: ticketStore.selections.map((selection) => ({
          marketOfferId: selection.offerId,
          oddsAtPlacement: selection.odds,
        })),
      })

      const capped = ticketStore.stake * ticketStore.totalOdds > MAX_WIN
      ticketStore.clearSelections()
      try {
        const profile = await getProfile()
        authStore.hydrateProfile(profile)
      } catch {
        authStore.updateBalance(Math.max(authStore.balance - placedStake, 0))
      }
      toastStore.showSuccess(capped ? 'Bilet plasat. Castigul potential a fost plafonat.' : 'Bilet plasat cu succes.')
      await router.push({ name: 'ticket-detail', params: { id: ticket.id } })
      return ticket
    } catch (error) {
      toastStore.showError(error instanceof Error ? error.message : 'Nu am putut plasa biletul.')
      return null
    } finally {
      placingTicket.value = false
    }
  }

  return {
    placingTicket,
    toggleOffer,
    placeCurrentTicket,
  }
}


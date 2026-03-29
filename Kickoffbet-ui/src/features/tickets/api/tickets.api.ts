import api from '@/api/axios'
import type { Ticket, PlaceTicketRequest } from '../types/ticket.types'
import type { TicketStatus } from '@/shared/types/enums'
import type { PageRequest, PageResponse } from '@/shared/types/api.types'

export const placeTicket = (request: PlaceTicketRequest): Promise<Ticket> =>
  api.post('/api/tickets', request).then(res => res.data)

export const getTicket = (id: string): Promise<Ticket> =>
  api.get(`/api/tickets/${id}`).then(res => res.data)

export const getUserTickets = (
  status: TicketStatus,
  pageable?: PageRequest
): Promise<PageResponse<Ticket>> =>
  api.get('/api/tickets', { params: { status, ...pageable } }).then(res => res.data)

export const cancelTicket = (id: string): Promise<void> =>
  api.patch(`/api/tickets/${id}/cancel`).then(() => {})

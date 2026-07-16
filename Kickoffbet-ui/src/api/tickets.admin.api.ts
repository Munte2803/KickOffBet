import api from '@/api/axios'
import { withDefaultSort } from '@/api/utils/defaultSort'
import type { Ticket } from '@/types/ticket.types'
import type { TicketStatus } from '@/types/enums'
import type { PageRequest, PageResponse } from '@/types/api.types'

export const getTicketsByStatus = (
  status: TicketStatus,
  pageable?: PageRequest,
): Promise<PageResponse<Ticket>> =>
  api.get('/api/admin/tickets', {
    params: { status, ...withDefaultSort(pageable) },
  }).then((res) => res.data)

export const getTicketById = (id: string): Promise<Ticket> =>
  api.get(`/api/admin/tickets/${id}`).then((res) => res.data)

export const getUserTickets = (
  userId: string,
  pageable?: PageRequest,
): Promise<PageResponse<Ticket>> =>
  api.get(`/api/admin/tickets/users/${userId}`, {
    params: withDefaultSort(pageable),
  }).then((res) => res.data)

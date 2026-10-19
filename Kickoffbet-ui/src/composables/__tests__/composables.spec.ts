import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePagination } from '@/composables/usePagination'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useTimeSeriesChart } from '@/composables/useTimeSeriesChart'
import { useConfirmStore } from '@/stores/confirm.store'
import { useToastStore } from '@/stores/toast.store'

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.useRealTimers()
})

describe('usePagination', () => {
  it('defaults to page 0, size 12 and no sort', () => {
    const pagination = usePagination()
    expect(pagination.request.value).toEqual({ page: 0, size: 12 })
  })

  it('includes the sort only when set', () => {
    const pagination = usePagination(5, 'createdAt,desc')
    expect(pagination.request.value).toEqual({ page: 0, size: 5, sort: 'createdAt,desc' })
    pagination.sort.value = undefined
    expect(pagination.request.value).toEqual({ page: 0, size: 5 })
  })

  it('setPage updates the request and clamps negatives to 0', () => {
    const pagination = usePagination(3)
    pagination.setPage(4)
    expect(pagination.request.value.page).toBe(4)
    pagination.setPage(-2)
    expect(pagination.page.value).toBe(0)
  })

  it('reset returns to the first page and keeps size/sort', () => {
    const pagination = usePagination(8, 'name,asc')
    pagination.setPage(3)
    pagination.size.value = 20
    pagination.reset()
    expect(pagination.request.value).toEqual({ page: 0, size: 20, sort: 'name,asc' })
  })
})

describe('useConfirmDialog', () => {
  it('delegates to the confirm store and resolves with the user answer', async () => {
    const { confirm } = useConfirmDialog()
    const store = useConfirmStore()

    const answer = confirm({ title: 'Anuleaza biletul', message: 'Sigur?', variant: 'danger' })
    expect(store.open).toBe(true)
    expect(store.title).toBe('Anuleaza biletul')
    expect(store.variant).toBe('danger')

    store.confirm()
    await expect(answer).resolves.toBe(true)
  })
})

describe('useTimeSeriesChart', () => {
  it('refuses to load without a full range and shows the (custom) message', async () => {
    const fetcher = vi.fn()
    const chart = useTimeSeriesChart(fetcher, { missingRangeMessage: 'Alege intervalul' })
    const toast = useToastStore()
    vi.useFakeTimers()

    chart.chartStart.value = '2026-07-01T00:00'
    await expect(chart.loadChart()).resolves.toBe(false)
    expect(fetcher).not.toHaveBeenCalled()
    expect(toast.toasts.at(-1)).toMatchObject({ type: 'error', message: 'Alege intervalul' })
  })

  it('uses the default missing-range message', async () => {
    const chart = useTimeSeriesChart(vi.fn())
    vi.useFakeTimers()
    await chart.loadChart()
    expect(useToastStore().toasts.at(-1)?.message).toBe('Selecteaza intervalul pentru grafic.')
  })

  it('converts the local range to UTC API dates and stores the data', async () => {
    const points = [{ date: '2026-07-01', count: 3, totalAmount: 120 }]
    let resolveFetch!: (value: typeof points) => void
    const fetcher = vi.fn(() => new Promise<typeof points>((resolve) => { resolveFetch = resolve }))
    const chart = useTimeSeriesChart(fetcher)

    chart.chartStart.value = '2026-07-01T00:00'
    chart.chartEnd.value = '2026-07-07T23:59'
    const pending = chart.loadChart()
    expect(chart.chartLoading.value).toBe(true)

    resolveFetch(points)
    await expect(pending).resolves.toBe(true)
    // Europe/Bucharest is UTC+3 in July.
    expect(fetcher).toHaveBeenCalledWith('2026-06-30T21:00:00', '2026-07-07T20:59:00')
    expect(chart.chartData.value).toEqual(points)
    expect(chart.chartLoading.value).toBe(false)
  })

  it('shows the error message on failure and resets the loading flag', async () => {
    const chart = useTimeSeriesChart(vi.fn().mockRejectedValue(new Error('Server indisponibil')))
    vi.useFakeTimers()
    chart.chartStart.value = '2026-07-01T00:00'
    chart.chartEnd.value = '2026-07-02T00:00'

    await expect(chart.loadChart()).resolves.toBe(false)
    expect(chart.chartLoading.value).toBe(false)
    expect(useToastStore().toasts.at(-1)).toMatchObject({ type: 'error', message: 'Server indisponibil' })
  })

  it('falls back to the configured error message for non-Error rejections', async () => {
    const chart = useTimeSeriesChart(vi.fn().mockRejectedValue('nope'), { errorMessage: 'Eroare grafic' })
    vi.useFakeTimers()
    chart.chartStart.value = '2026-07-01T00:00'
    chart.chartEnd.value = '2026-07-02T00:00'
    await chart.loadChart()
    expect(useToastStore().toasts.at(-1)?.message).toBe('Eroare grafic')
  })
})

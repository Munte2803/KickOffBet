import { ref } from 'vue'
import type { TimeSeriesPoint } from '@/api/admin.timeseries.api'
import { inputToUtcApiDate } from '@/utils/date.utils'
import { useToastStore } from '@/stores/toast.store'

export type TimeSeriesFetcher = (utcStart: string, utcEnd: string) => Promise<TimeSeriesPoint[]>

export interface UseTimeSeriesChartOptions {
  missingRangeMessage?: string
  errorMessage?: string
}

export function useTimeSeriesChart(fetcher: TimeSeriesFetcher, options: UseTimeSeriesChartOptions = {}) {
  const toastStore = useToastStore()

  const chartStart = ref('')
  const chartEnd = ref('')
  const chartData = ref<TimeSeriesPoint[]>([])
  const chartLoading = ref(false)

  async function loadChart(): Promise<boolean> {
    if (!chartStart.value || !chartEnd.value) {
      toastStore.showError(options.missingRangeMessage ?? 'Selecteaza intervalul pentru grafic.')
      return false
    }
    chartLoading.value = true
    try {
      const utcStart = inputToUtcApiDate(chartStart.value) ?? chartStart.value
      const utcEnd = inputToUtcApiDate(chartEnd.value) ?? chartEnd.value
      chartData.value = await fetcher(utcStart, utcEnd)
      return true
    } catch (error) {
      toastStore.showError(
        error instanceof Error ? error.message : (options.errorMessage ?? 'Nu am putut incarca graficul.'),
      )
      return false
    } finally {
      chartLoading.value = false
    }
  }

  return { chartStart, chartEnd, chartData, chartLoading, loadChart }
}

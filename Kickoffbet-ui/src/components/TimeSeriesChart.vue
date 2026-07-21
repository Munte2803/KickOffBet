<script setup lang="ts">
import { computed, ref } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  Title,
  Tooltip,
  Legend,
  Filler,
  type ChartData,
  type ChartOptions,
} from 'chart.js'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  LineController,
  Title,
  Tooltip,
  Legend,
  Filler,
)

export interface TimeSeriesPoint {
  date: string
  count: number
  totalAmount?: number | null
}

const props = defineProps<{
  data: TimeSeriesPoint[]
  metric: 'count' | 'totalAmount'
  label: string
  color?: string
  height?: number
}>()

const color = computed(() => props.color ?? '#3b82f6')

const chartData = computed<ChartData<'line'>>(() => ({
  labels: props.data.map((p) => p.date),
  datasets: [
    {
      label: props.label,
      data: props.data.map((p) => {
        if (props.metric === 'count') return p.count
        return p.totalAmount != null ? Number(p.totalAmount) : 0
      }),
      borderColor: color.value,
      backgroundColor: color.value + '33',
      fill: true,
      tension: 0.3,
      pointRadius: 3,
      pointHoverRadius: 5,
      borderWidth: 2,
    },
  ],
}))

const chartRef = ref<InstanceType<typeof Line> | null>(null)

function exportPng(filename: string) {
  if (props.data.length === 0) return
  const inst = (chartRef.value as unknown as { chart?: ChartJS })?.chart
  if (!inst) return
  const dataUrl = inst.toBase64Image('image/png', 1.0)
  const link = document.createElement('a')
  link.href = dataUrl
  link.download = filename.endsWith('.png') ? filename : `${filename}.png`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

defineExpose({ exportPng })

const chartOptions = computed<ChartOptions<'line'>>(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: { color: '#cbd5e1' },
    },
    tooltip: {
      backgroundColor: '#0f172a',
      titleColor: '#f1f5f9',
      bodyColor: '#cbd5e1',
      borderColor: '#334155',
      borderWidth: 1,
    },
  },
  scales: {
    x: {
      ticks: { color: '#94a3b8', maxRotation: 0, autoSkip: true, maxTicksLimit: 12 },
      grid: { color: 'rgba(148,163,184,0.1)' },
    },
    y: {
      ticks: { color: '#94a3b8' },
      grid: { color: 'rgba(148,163,184,0.1)' },
      beginAtZero: true,
    },
  },
}))
</script>

<template>
  <div :style="{ height: (height ?? 280) + 'px' }">
    <div
      v-if="data.length === 0"
      class="flex h-full items-center justify-center text-sm text-muted"
    >
      No data for selected range
    </div>
    <Line v-else ref="chartRef" :data="chartData" :options="chartOptions" />
  </div>
</template>

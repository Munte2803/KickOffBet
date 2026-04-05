<script setup lang="ts">
import { computed, ref, watch } from 'vue'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  modelValue?: string
  type?: string
  disabled?: boolean
  min?: string
  max?: string
  invalid?: boolean
}>(), {
  modelValue: '',
  type: 'date',
  disabled: false,
  min: '',
  max: '',
  invalid: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const now = new Date()
const currentYear = now.getFullYear()
const inputType = computed(() => props.type ?? 'date')

const selectedYear = ref('')
const selectedMonth = ref('')
const selectedDay = ref('')
const selectedHour = ref('')
const selectedMinute = ref('')

function pad(value: number | string) {
  return String(value).padStart(2, '0')
}

function parseParts(value?: string) {
  const raw = value?.trim() ?? ''

  if (!raw) {
    return { year: '', month: '', day: '', hour: '', minute: '' }
  }

  if (inputType.value === 'datetime-local') {
    const match = raw.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
    return match
      ? { year: match[1], month: match[2], day: match[3], hour: match[4], minute: match[5] }
      : { year: '', month: '', day: '', hour: '', minute: '' }
  }

  if (inputType.value === 'time') {
    const match = raw.match(/^(\d{2}):(\d{2})/)
    return match
      ? { year: '', month: '', day: '', hour: match[1], minute: match[2] }
      : { year: '', month: '', day: '', hour: '', minute: '' }
  }

  if (inputType.value === 'month') {
    const match = raw.match(/^(\d{4})-(\d{2})/)
    return match
      ? { year: match[1], month: match[2], day: '', hour: '', minute: '' }
      : { year: '', month: '', day: '', hour: '', minute: '' }
  }

  const match = raw.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match
    ? { year: match[1], month: match[2], day: match[3], hour: '', minute: '' }
    : { year: '', month: '', day: '', hour: '', minute: '' }
}

function syncFromValue(value?: string) {
  const parts = parseParts(value)
  selectedYear.value = parts.year
  selectedMonth.value = parts.month
  selectedDay.value = parts.day
  selectedHour.value = parts.hour
  selectedMinute.value = parts.minute
}

function getYearFromValue(value?: string): number | null {
  const parts = parseParts(value)
  return parts.year ? Number(parts.year) : null
}

function getDaysInMonth(year: string, month: string) {
  const numericYear = Number(year || currentYear)
  const numericMonth = Number(month || 1)
  return new Date(numericYear, numericMonth, 0).getDate()
}

const yearOptions = computed(() => {
  const defaultStart = ['date', 'month'].includes(inputType.value) ? 1900 : currentYear - 5
  const defaultEnd = currentYear + 15
  const minYear = getYearFromValue(props.min)
  const maxYear = getYearFromValue(props.max)
  const selectedValueYear = selectedYear.value ? Number(selectedYear.value) : null

  const start = Math.min(...[defaultStart, minYear, maxYear, selectedValueYear].filter((value): value is number => Number.isFinite(value)))
  const end = Math.max(...[defaultEnd, minYear, maxYear, selectedValueYear].filter((value): value is number => Number.isFinite(value)))

  return Array.from({ length: end - start + 1 }, (_, index) => String(end - index))
})

const monthOptions = computed(() =>
  Array.from({ length: 12 }, (_, index) => ({
    value: pad(index + 1),
  })),
)

const dayOptions = computed(() =>
  Array.from({ length: getDaysInMonth(selectedYear.value, selectedMonth.value) }, (_, index) => pad(index + 1)),
)

const hourOptions = computed(() =>
  Array.from({ length: 24 }, (_, index) => pad(index)),
)

const minuteOptions = computed(() =>
  Array.from({ length: 60 }, (_, index) => pad(index)),
)

const fieldMeta = {
  year: { placeholder: 'An' },
  month: { placeholder: 'Luna' },
  day: { placeholder: 'Zi' },
  hour: { placeholder: 'Ora' },
  minute: { placeholder: 'Min' },
}


const assembledValue = computed(() => {
  if (inputType.value === 'datetime-local') {
    if (!selectedYear.value || !selectedMonth.value || !selectedDay.value || !selectedHour.value || !selectedMinute.value) {
      return ''
    }

    return `${selectedYear.value}-${selectedMonth.value}-${selectedDay.value}T${selectedHour.value}:${selectedMinute.value}`
  }

  if (inputType.value === 'time') {
    if (!selectedHour.value || !selectedMinute.value) {
      return ''
    }

    return `${selectedHour.value}:${selectedMinute.value}`
  }

  if (inputType.value === 'month') {
    if (!selectedYear.value || !selectedMonth.value) {
      return ''
    }

    return `${selectedYear.value}-${selectedMonth.value}`
  }

  if (!selectedYear.value || !selectedMonth.value || !selectedDay.value) {
    return ''
  }

  return `${selectedYear.value}-${selectedMonth.value}-${selectedDay.value}`
})

watch(
  () => props.modelValue,
  (value) => {
    if ((value ?? '') !== assembledValue.value) {
      syncFromValue(value)
    }
  },
  { immediate: true },
)

watch([selectedYear, selectedMonth], () => {
  if (!selectedDay.value) {
    return
  }

  const maxDay = getDaysInMonth(selectedYear.value, selectedMonth.value)
  if (Number(selectedDay.value) > maxDay) {
    selectedDay.value = pad(maxDay)
  }
})

watch(assembledValue, (value) => {
  if (value !== (props.modelValue ?? '')) {
    emit('update:modelValue', value)
  }
})

const selectClass = computed(() => [
  'app-select w-full min-w-0 rounded-lg border bg-black/50 px-2 py-1.75 pr-5 text-[12px] font-medium text-white focus:outline-none disabled:cursor-not-allowed disabled:opacity-50 sm:px-3 sm:py-2 sm:pr-7 sm:text-sm',
  props.invalid ? 'border-red-500 focus:border-red-400' : 'border-white/10 focus:border-blue-500',
])
</script>

<template>
  <div class="flex flex-wrap gap-1 sm:gap-2">
    <div v-if="['date', 'datetime-local', 'month'].includes(inputType)" class="min-w-[88px] flex-[1_1_100px] sm:min-w-[112px] sm:flex-[1_1_132px]">
      <select
        v-model="selectedYear"
        :disabled="disabled"
        :class="selectClass"
        aria-label="An"
      >
        <option value="">{{ fieldMeta.year.placeholder }}</option>
        <option v-for="year in yearOptions" :key="year" :value="year">{{ year }}</option>
      </select>
    </div>

    <div v-if="['date', 'datetime-local', 'month'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <select
        v-model="selectedMonth"
        :disabled="disabled"
        :class="selectClass"
        aria-label="Luna"
      >
        <option value="">{{ fieldMeta.month.placeholder }}</option>
        <option v-for="month in monthOptions" :key="month.value" :value="month.value">{{ month.value }}</option>
      </select>
    </div>

    <div v-if="['date', 'datetime-local'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <select
        v-model="selectedDay"
        :disabled="disabled"
        :class="selectClass"
        aria-label="Zi"
      >
        <option value="">{{ fieldMeta.day.placeholder }}</option>
        <option v-for="day in dayOptions" :key="day" :value="day">{{ day }}</option>
      </select>
    </div>

    <div v-if="['datetime-local', 'time'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <select
        v-model="selectedHour"
        :disabled="disabled"
        :class="selectClass"
        aria-label="Ora"
      >
        <option value="">{{ fieldMeta.hour.placeholder }}</option>
        <option v-for="hour in hourOptions" :key="hour" :value="hour">{{ hour }}</option>
      </select>
    </div>

    <div v-if="['datetime-local', 'time'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <select
        v-model="selectedMinute"
        :disabled="disabled"
        :class="selectClass"
        aria-label="Minute"
      >
        <option value="">{{ fieldMeta.minute.placeholder }}</option>
        <option v-for="minute in minuteOptions" :key="minute" :value="minute">{{ minute }}</option>
      </select>
    </div>
  </div>
</template>

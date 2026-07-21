<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppDropdownSelect from '@/components/AppDropdownSelect.vue'

defineOptions({
  inheritAttrs: false,
})

function toChoices(values: string[]) {
  return values.map((value) => ({ value, label: value }))
}

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

const yearChoices = computed(() => toChoices(yearOptions.value))

const monthChoices = computed(() =>
  toChoices(Array.from({ length: 12 }, (_, index) => pad(index + 1))),
)

const dayChoices = computed(() =>
  toChoices(Array.from({ length: getDaysInMonth(selectedYear.value, selectedMonth.value) }, (_, index) => pad(index + 1))),
)

const hourChoices = computed(() =>
  toChoices(Array.from({ length: 24 }, (_, index) => pad(index))),
)

const minuteChoices = computed(() =>
  toChoices(Array.from({ length: 60 }, (_, index) => pad(index))),
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

</script>

<template>
  <div class="flex flex-wrap gap-1 sm:gap-2">
    <div v-if="['date', 'datetime-local', 'month'].includes(inputType)" class="min-w-[88px] flex-[1_1_100px] sm:min-w-[112px] sm:flex-[1_1_132px]">
      <AppDropdownSelect
        v-model="selectedYear"
        :options="yearChoices"
        :placeholder="fieldMeta.year.placeholder"
        :disabled="disabled"
        :invalid="invalid"
        aria-label="An"
      />
    </div>

    <div v-if="['date', 'datetime-local', 'month'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <AppDropdownSelect
        v-model="selectedMonth"
        :options="monthChoices"
        :placeholder="fieldMeta.month.placeholder"
        :disabled="disabled"
        :invalid="invalid"
        aria-label="Luna"
      />
    </div>

    <div v-if="['date', 'datetime-local'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <AppDropdownSelect
        v-model="selectedDay"
        :options="dayChoices"
        :placeholder="fieldMeta.day.placeholder"
        :disabled="disabled"
        :invalid="invalid"
        aria-label="Zi"
      />
    </div>

    <div v-if="['datetime-local', 'time'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <AppDropdownSelect
        v-model="selectedHour"
        :options="hourChoices"
        :placeholder="fieldMeta.hour.placeholder"
        :disabled="disabled"
        :invalid="invalid"
        aria-label="Ora"
      />
    </div>

    <div v-if="['datetime-local', 'time'].includes(inputType)" class="min-w-[72px] flex-[1_1_80px] sm:min-w-[88px] sm:flex-[1_1_96px]">
      <AppDropdownSelect
        v-model="selectedMinute"
        :options="minuteChoices"
        :placeholder="fieldMeta.minute.placeholder"
        :disabled="disabled"
        :invalid="invalid"
        aria-label="Minute"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import AppButton from '@/components/AppButton.vue'
import { toLocalInputValue } from '@/utils/date.utils'

const emit = defineEmits<{
  select: [start: string, end: string]
}>()

/**
 * The range end is the last second of today. The backend filters these ranges inclusively
 * (BETWEEN / <=), so 23:59 with implicit :00 seconds would drop the day's last minute, while the
 * next midnight would pull in the following day.
 */
function endOfToday(now: Date) {
  return `${toLocalInputValue(new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59))}:59`
}

function selectToday() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0)
  emit('select', toLocalInputValue(start), endOfToday(now))
}

function selectWeek() {
  const now = new Date()
  const end = endOfToday(now)
  const dayOfWeek = now.getDay()
  const daysToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysToMonday, 0, 0)
  emit('select', toLocalInputValue(monday), end)
}

function selectMonth() {
  const now = new Date()
  const end = endOfToday(now)
  const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0)
  emit('select', toLocalInputValue(firstOfMonth), end)
}

function selectYear() {
  const now = new Date()
  const end = endOfToday(now)
  const firstOfYear = new Date(now.getFullYear(), 0, 1, 0, 0)
  emit('select', toLocalInputValue(firstOfYear), end)
}
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <AppButton variant="outline" @click="selectToday">Azi</AppButton>
    <AppButton variant="outline" @click="selectWeek">Saptamana</AppButton>
    <AppButton variant="outline" @click="selectMonth">Luna</AppButton>
    <AppButton variant="outline" @click="selectYear">An</AppButton>
  </div>
</template>

<script setup lang="ts">
import AppButton from '@/components/AppButton.vue'
import { toLocalInputValue } from '@/utils/date.utils'

const emit = defineEmits<{
  select: [start: string, end: string]
}>()

function selectToday() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0)
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59)
  emit('select', toLocalInputValue(start), toLocalInputValue(end))
}

function selectWeek() {
  const now = new Date()
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59)
  const dayOfWeek = now.getDay()
  const daysToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - daysToMonday, 0, 0)
  emit('select', toLocalInputValue(monday), toLocalInputValue(end))
}

function selectMonth() {
  const now = new Date()
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59)
  const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0)
  emit('select', toLocalInputValue(firstOfMonth), toLocalInputValue(end))
}

function selectYear() {
  const now = new Date()
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59)
  const firstOfYear = new Date(now.getFullYear(), 0, 1, 0, 0)
  emit('select', toLocalInputValue(firstOfYear), toLocalInputValue(end))
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

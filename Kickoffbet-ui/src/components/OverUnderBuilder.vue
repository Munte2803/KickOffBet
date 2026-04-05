<script setup lang="ts">
import { ref } from 'vue'
import AppButton from '@/components/AppButton.vue'

const MIN_ODDS = 1.01

const emit = defineEmits<{
  add: [line: number, option: 'OVER' | 'UNDER', odds: number]
}>()

const line = ref('2.5')
const option = ref<'OVER' | 'UNDER'>('OVER')
const odds = ref('1.85')
const error = ref('')

function isValidHalfLine(value: number) {
  if (!Number.isFinite(value) || value < 0.5) return false
  const remainder = value % 1
  return Math.abs(remainder - 0.5) < 0.00001
}

defineExpose({ setError: (msg: string) => { error.value = msg } })

function submit() {
  const parsedLine = Number(line.value)
  const parsedOdds = Number(odds.value)

  if (!isValidHalfLine(parsedLine)) {
    error.value = 'Line trebuie sa se termine in .5.'
    return
  }

  if (!Number.isFinite(parsedOdds) || parsedOdds < MIN_ODDS) {
    error.value = 'Cota trebuie sa fie cel putin 1.01.'
    return
  }

  error.value = ''
  emit('add', parsedLine, option.value, parsedOdds)
}
</script>

<template>
  <div class="rounded-xl border border-white/10 bg-black/30 p-4 xl:col-span-2">
    <div>
      <h4 class="text-sm font-semibold text-white">Total goluri</h4>
      <p class="mt-1 text-xs text-gray-400">Alegi optiunea, introduci cota si apoi adaugi oferta in lista de mai jos.</p>
    </div>

    <div class="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1.2fr)_140px_160px_140px_auto]">
      <div class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-white">
        Total goluri
      </div>

      <input
        v-model="line"
        type="number"
        min="0.5"
        step="1"
        class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
      >

      <select
        v-model="option"
        class="app-select-field app-select"
      >
        <option value="OVER">Peste</option>
        <option value="UNDER">Sub</option>
      </select>

      <input
        v-model="odds"
        type="number"
        min="1.01"
        step="0.01"
        class="rounded-lg border border-white/10 bg-black/60 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
      >

      <AppButton variant="outline" @click="submit">Adauga</AppButton>
    </div>

    <p v-if="error" class="mt-3 text-xs text-red-400">{{ error }}</p>
  </div>
</template>

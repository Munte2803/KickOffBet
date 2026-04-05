<script setup lang="ts">
import AppButton from '@/components/AppButton.vue'

defineProps<{
  open: boolean
  title: string
  src: string | null
  contentType?: string | null
  fileName?: string | null
  allowDownload?: boolean
}>()

defineEmits<{
  close: []
  download: []
}>()
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-[70] flex items-center justify-center bg-black/80 px-4 py-6 backdrop-blur-sm" @click.self="$emit('close')">
    <div class="w-full max-w-5xl rounded-2xl border border-white/10 bg-black shadow-2xl">
      <div class="flex items-center justify-between gap-4 border-b border-white/10 px-5 py-4">
        <div class="min-w-0">
          <h2 class="truncate text-lg font-bold text-white">{{ title }}</h2>
          <p v-if="fileName" class="truncate text-xs text-gray-400">{{ fileName }}</p>
        </div>

        <div class="flex items-center gap-2">
          <AppButton v-if="allowDownload" size="sm" variant="outline" @click="$emit('download')">
            Descarca
          </AppButton>
          <AppButton size="sm" variant="ghost" @click="$emit('close')">
            Inchide
          </AppButton>
        </div>
      </div>

      <div class="max-h-[80vh] overflow-auto p-5">
        <img
          v-if="src && contentType?.startsWith('image/')"
          :src="src"
          :alt="fileName ?? title"
          class="mx-auto max-h-[70vh] w-auto max-w-full rounded-xl border border-white/10 bg-white/5 object-contain"
        />

        <iframe
          v-else-if="src && contentType === 'application/pdf'"
          :src="src"
          class="h-[70vh] w-full rounded-xl border border-white/10 bg-white"
          title="Previzualizare document"
        />

        <div v-else class="rounded-xl border border-dashed border-white/10 bg-white/5 px-4 py-12 text-center">
          <p class="text-sm font-medium text-gray-300">Previzualizarea nu este disponibila pentru acest fisier.</p>
          <p class="mt-2 text-xs text-gray-500">Poti folosi descarcarea doar daca ai nevoie de fisierul brut.</p>
        </div>
      </div>
    </div>
  </div>
</template>

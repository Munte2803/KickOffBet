<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme.store'

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.resolved === 'dark')
</script>

<template>
  <button
    type="button"
    :aria-label="isDark ? 'Comuta pe tema deschisa' : 'Comuta pe tema intunecata'"
    :title="isDark ? 'Tema deschisa' : 'Tema intunecata'"
    class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-line bg-surface text-muted transition-colors hover:border-blue-500/30 hover:text-fg"
    @click="themeStore.toggle()"
  >
    <!-- Sun (shown in dark mode → click to go light) -->
    <svg v-if="isDark" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <circle cx="12" cy="12" r="4" stroke-width="2" />
      <path stroke-linecap="round" stroke-width="2" d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32l1.41 1.41M2 12h2m16 0h2M4.93 19.07l1.41-1.41m11.32-11.32l1.41-1.41" />
    </svg>
    <!-- Moon (shown in light mode → click to go dark) -->
    <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
    </svg>
  </button>
</template>

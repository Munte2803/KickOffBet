<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LogoFrame from '@/components/LogoFrame.vue'

const props = defineProps<{
  title?: string
  backRoute?: string | object
  logoUrl?: string | null
  subtitle?: string
  scoreboard?: boolean
  homeTeamName?: string
  homeTeamLogo?: string | null
  awayTeamName?: string
  awayTeamLogo?: string | null
}>()

const router = useRouter()
const route = useRoute()
const wrapperRef = ref<HTMLElement | null>(null)
const headerRef = ref<HTMLElement | null>(null)
const wrapperWidth = ref(0)
const wrapperLeft = ref(0)
const headerHeight = ref(0)
let resizeObserver: ResizeObserver | null = null

const fallbackRoute = computed(() => {
  if (props.backRoute) {
    return props.backRoute
  }

  if (String(route.name ?? '').startsWith('admin-')) {
    return { name: 'admin-dashboard' }
  }

  return { name: 'home' }
})

async function handleBack() {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    await router.back()
    return
  }

  await router.push(fallbackRoute.value)
}

function updateLayoutMetrics() {
  if (wrapperRef.value) {
    const rect = wrapperRef.value.getBoundingClientRect()
    wrapperWidth.value = rect.width
    wrapperLeft.value = rect.left
  }

  if (headerRef.value) {
    headerHeight.value = headerRef.value.offsetHeight
  }
}

onMounted(async () => {
  await nextTick()
  updateLayoutMetrics()

  if (typeof ResizeObserver !== 'undefined' && wrapperRef.value) {
    resizeObserver = new ResizeObserver(() => updateLayoutMetrics())
    resizeObserver.observe(wrapperRef.value)
    if (headerRef.value) {
      resizeObserver.observe(headerRef.value)
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('resize', updateLayoutMetrics)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', updateLayoutMetrics)
  }
})
</script>

<template>
  <div ref="wrapperRef" class="mb-3.5 max-[390px]:mb-3 max-[360px]:mb-2.5 sm:mb-6" :style="{ height: `${headerHeight}px` }">
    <div
      ref="headerRef"
      class="fixed z-40 border-b border-white/10 bg-black pb-2.5 pt-2.5 shadow-[0_18px_36px_rgba(0,0,0,0.58)] max-[390px]:pb-2 max-[390px]:pt-2 max-[360px]:pb-1.5 max-[360px]:pt-1.5 sm:pb-4 sm:pt-4"
      :style="{
        top: 'var(--app-page-header-top)',
        left: `${wrapperLeft}px`,
        width: `${wrapperWidth}px`,
      }"
    >
      <div class="flex items-center gap-2.5 max-[390px]:gap-2 max-[360px]:gap-1.5 sm:gap-4">
        <button
          v-if="backRoute"
          type="button"
          class="flex-shrink-0 rounded-full border border-white/10 bg-white/5 p-1.25 text-gray-300 transition-colors hover:border-blue-500/30 hover:text-blue-300 max-[390px]:p-1 max-[360px]:p-0.75 sm:p-2"
          @click="handleBack"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 max-[390px]:h-4 max-[390px]:w-4 max-[360px]:h-3.5 max-[360px]:w-3.5 sm:h-6 sm:w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
        </button>

        <div class="min-w-0 flex-1">
          <template v-if="scoreboard">
            <div class="flex min-w-0 items-center gap-1.5 max-[390px]:gap-1 max-[360px]:gap-0.5 sm:gap-4">
              <div class="flex min-w-0 flex-1 items-center justify-end gap-2 max-[390px]:gap-1.5 max-[360px]:gap-1">
                <span class="truncate text-right text-[15px] font-bold text-white max-[390px]:text-[13px] max-[360px]:text-[12px] sm:text-xl md:text-2xl">{{ homeTeamName }}</span>
                <LogoFrame v-if="homeTeamLogo" :src="homeTeamLogo" size="md" />
              </div>
            <span class="flex-shrink-0 px-0.5 text-[10px] font-bold italic text-gray-500 max-[390px]:text-[9px] max-[360px]:text-[8px] sm:px-2 sm:text-sm">vs</span>
              <div class="flex min-w-0 flex-1 items-center justify-start gap-2 max-[390px]:gap-1.5 max-[360px]:gap-1">
                <LogoFrame v-if="awayTeamLogo" :src="awayTeamLogo" size="md" />
                <span class="truncate text-left text-[15px] font-bold text-white max-[390px]:text-[13px] max-[360px]:text-[12px] sm:text-xl md:text-2xl">{{ awayTeamName }}</span>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="flex min-w-0 items-center gap-2 max-[390px]:gap-1.5 max-[360px]:gap-1 sm:gap-3">
              <LogoFrame v-if="logoUrl" :src="logoUrl" size="md" />
              <h1 class="truncate text-lg font-bold tracking-tight text-white max-[390px]:text-[17px] max-[360px]:text-[16px] sm:text-2xl md:text-3xl">{{ title }}</h1>
            </div>
          </template>

          <span v-if="subtitle" class="mt-0.5 block pl-0.5 text-[11px] text-gray-400 max-[390px]:text-[10px] max-[360px]:text-[9px] sm:mt-1 sm:pl-1 sm:text-sm">{{ subtitle }}</span>
        </div>

        <div class="flex-shrink-0">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import BettingSlip from '@/components/BettingSlip.vue'
import AppShellFrame from '@/components/AppShellFrame.vue'
import { useAuthStore } from '@/stores/auth.store'
import { useTicketStore } from '@/stores/ticket.store'
import { formatMoney } from '@/utils/money.utils'

const authStore = useAuthStore()
const ticketStore = useTicketStore()
const route = useRoute()
const mobileMenuOpen = ref(false)
const mobileSlipOpen = ref(false)

const mainLinks = computed(() => [
  { key: 'home', label: 'Meciuri', to: { name: 'home' } },
  { key: 'leagues', label: 'Ligi', to: { name: 'leagues' } },
  { key: 'teams', label: 'Echipe', to: { name: 'teams' } },
  { key: 'results', label: 'Rezultate', to: { name: 'results' } },
  { key: 'tickets', label: 'Biletele mele', to: { name: 'tickets' } },
])

const profileLinks = computed(() => [
  { key: 'profile', label: 'Profil', to: { name: 'profile' } },
  { key: 'wallet', label: 'Portofel', to: { name: 'wallet' } },
  { key: 'profile-settings', label: 'Setari', to: { name: 'profile-settings' } },
])

function isMainActive(key: string) {
  const name = String(route.name ?? '')
  if (key === 'teams') return ['teams', 'team', 'team-results'].includes(name)
  if (key === 'leagues') return ['leagues', 'league', 'league-results'].includes(name)
  return name === key
}

function isProfileActive(key: string) {
  const name = String(route.name ?? '')
  if (key === 'wallet') return name === 'wallet' || name === 'transaction-detail'
  return name === key
}

const isAdminActive = computed(() => String(route.name ?? '').startsWith('admin'))

const showSlip = computed(() => {
  const hiddenPrefixes = ['/login', '/register', '/confirm-email', '/forgot-password', '/reset-password', '/profile', '/wallet', '/tickets', '/admin']
  return authStore.isAuthenticated && !hiddenPrefixes.some((prefix) => route.path.startsWith(prefix)) && route.name !== 'not-found'
})

const slipStyle = computed(() => ({
  top: 'var(--app-slip-top)',
  width: 'var(--app-slip-width)',
  right: 'max(1rem, calc((100vw - var(--app-page-max-width)) / 2 + 1rem))',
}))

watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
    mobileSlipOpen.value = false
    void refreshUserProfileSilently()
  },
)

function refreshUserProfileSilently(force = false) {
  if (!authStore.isAuthenticated) {
    return
  }

  void authStore.refreshProfile(force).catch(() => {})
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    refreshUserProfileSilently()
  }
}

function handleWindowFocus() {
  refreshUserProfileSilently()
}

function handleGlobalPointerDown() {
  refreshUserProfileSilently()
}

onMounted(() => {
  refreshUserProfileSilently(true)
  window.addEventListener('focus', handleWindowFocus)
  window.addEventListener('pointerdown', handleGlobalPointerDown)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  window.removeEventListener('focus', handleWindowFocus)
  window.removeEventListener('pointerdown', handleGlobalPointerDown)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
  if (mobileMenuOpen.value) {
    mobileSlipOpen.value = false
  }
}

function toggleMobileSlip() {
  mobileSlipOpen.value = !mobileSlipOpen.value
  if (mobileSlipOpen.value) {
    mobileMenuOpen.value = false
  }
}

function closeMobileOverlays() {
  mobileMenuOpen.value = false
  mobileSlipOpen.value = false
}

function closeMobileSlip() {
  mobileSlipOpen.value = false
}
</script>

<template>
  <div class="min-h-screen bg-black text-white">
    <!-- Top bar (always visible; hamburger toggles sidebar drawer) -->
    <header class="fixed left-0 top-0 z-50 w-full border-b border-white/20 bg-black shadow-lg">
      <div class="mx-auto grid h-[var(--app-header-height)] app-shell-width grid-cols-[auto_1fr_auto] items-center gap-2 px-2.5 sm:gap-4 sm:px-4">
        <button
          v-if="authStore.isAuthenticated"
          type="button"
          class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/5 text-gray-200 hover:border-blue-500/30 hover:text-white"
          @click="toggleMobileMenu"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <span class="min-w-0 truncate text-[15px] font-bold tracking-tight text-white sm:text-xl">
          KickOffBet
        </span>
        <template v-if="authStore.isAuthenticated">
          <RouterLink
            :to="{ name: 'profile' }"
            class="min-w-0 text-right hover:text-blue-500"
            :class="String(route.name ?? '').startsWith('profile') ? 'text-white' : 'text-gray-200'"
          >
            <p class="max-w-[132px] truncate text-sm font-bold">{{ authStore.fullName }}</p>
            <p class="max-w-[132px] truncate text-[10px] font-semibold uppercase tracking-[0.18em] text-green-500">{{ formatMoney(authStore.balance) }}</p>
          </RouterLink>
        </template>
      </div>
    </header>

    <!-- Drawer overlay -->
    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="mobileMenuOpen || mobileSlipOpen"
        class="fixed inset-0 z-[60] bg-black/70 backdrop-blur-sm"
        @click="closeMobileOverlays"
      />
    </transition>

    <!-- Mobile drawer (left, sidebar contents) -->
    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="-translate-x-3 opacity-0"
      enter-to-class="translate-x-0 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-x-0 opacity-100"
      leave-to-class="-translate-x-3 opacity-0"
    >
      <aside
        v-if="mobileMenuOpen"
        class="fixed left-0 top-0 z-[65] flex h-screen w-[min(86vw,300px)] flex-col border-r border-white/10 bg-black p-3"
      >
        <div class="border-b border-white/10 pb-3">
          <RouterLink :to="{ name: 'profile' }" class="block rounded-xl border border-white/10 bg-white/5 p-3 hover:border-blue-500/30 hover:bg-white/10">
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Utilizator</p>
            <p class="mt-2 text-lg font-bold text-white">{{ authStore.fullName }}</p>
            <p class="mt-1 text-xs uppercase tracking-[0.2em] text-green-500">{{ formatMoney(authStore.balance) }}</p>
          </RouterLink>
        </div>

        <nav class="mt-3 flex-1 space-y-1 overflow-y-auto">
          <RouterLink
            v-for="link in mainLinks"
            :key="link.key"
            :to="link.to"
            class="flex items-center rounded-lg border px-3 py-2 text-sm font-semibold"
            :class="isMainActive(link.key) ? 'border-blue-500/40 bg-blue-600/15 text-white' : 'border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 hover:text-white'"
          >
            {{ link.label }}
          </RouterLink>

          <div class="mt-3 border-t border-white/10 pt-3 space-y-1">
            <RouterLink
              v-for="link in profileLinks"
              :key="link.key"
              :to="link.to"
              class="flex items-center rounded-lg border px-3 py-2 text-sm font-semibold"
              :class="isProfileActive(link.key) ? 'border-blue-500/40 bg-blue-600/15 text-white' : 'border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 hover:text-white'"
            >
              {{ link.label }}
            </RouterLink>
          </div>

          <RouterLink
            v-if="authStore.isAdmin"
            :to="{ name: 'admin-matches' }"
            class="mt-3 flex items-center rounded-lg border px-3 py-2 text-sm font-semibold"
            :class="isAdminActive ? 'border-red-500/40 bg-red-600/15 text-white' : 'border-red-500/20 bg-red-600/10 text-red-300 hover:bg-red-600/15 hover:text-white'"
          >
            Administrare
          </RouterLink>
        </nav>
      </aside>
    </transition>

    <!-- Mobile slip drawer (unchanged) -->
    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="translate-y-4 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-4 opacity-0"
    >
      <div
        v-if="mobileSlipOpen && showSlip"
        class="fixed inset-x-0 bottom-0 top-[var(--app-header-height)] z-[65] overflow-hidden lg:hidden sm:inset-x-3 sm:bottom-3 sm:top-[76px]"
      >
        <div class="flex h-full flex-col overflow-hidden border-y border-white/10 bg-black/95 shadow-[0_30px_60px_rgba(0,0,0,0.45)] sm:rounded-2xl sm:border">
          <div class="flex items-center justify-between border-b border-white/10 px-3 py-2.5 sm:px-4 sm:py-3">
            <div>
              <p class="text-[13px] font-semibold text-white sm:text-sm">Biletul meu</p>
              <p class="text-[11px] text-gray-400 sm:text-xs">{{ formatMoney(authStore.balance) }}</p>
            </div>
            <button
              type="button"
              class="rounded-full border border-white/10 bg-white/5 p-1.5 text-gray-300 hover:text-white sm:p-2"
              @click="closeMobileSlip"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 sm:h-5 sm:w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto">
            <BettingSlip />
          </div>
        </div>
      </div>
    </transition>

    <button
      v-if="showSlip && !mobileMenuOpen && !mobileSlipOpen"
      type="button"
      class="fixed bottom-2.5 right-2.5 z-[55] inline-flex items-center gap-1.5 rounded-full border border-blue-500/30 bg-blue-600/90 px-3 py-2.25 text-[11px] font-semibold text-white shadow-[0_16px_34px_rgba(37,99,235,0.35)] hover:bg-blue-500 sm:bottom-4 sm:right-4 sm:gap-2 sm:px-4 sm:py-3 sm:text-sm lg:hidden"
      @click="toggleMobileSlip"
    >
      <span>Bilet</span>
      <span class="rounded-full bg-white/15 px-2 py-0.5 text-xs">{{ ticketStore.selectionCount }}</span>
    </button>

    <AppShellFrame :main-class="showSlip ? 'grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_var(--app-slip-width)]' : 'block'">
      <RouterView :key="route.fullPath" />

      <template v-if="showSlip" #aside>
        <div style="width: var(--app-slip-width);" />
      </template>
    </AppShellFrame>

    <aside v-if="showSlip" class="fixed z-20 hidden lg:block" :style="slipStyle">
      <BettingSlip />
    </aside>
  </div>
</template>

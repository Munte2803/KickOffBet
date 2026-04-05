<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import BettingSlip from '@/components/BettingSlip.vue'
import AppButton from '@/components/AppButton.vue'
import AppShellFrame from '@/components/AppShellFrame.vue'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth.store'
import { useTicketStore } from '@/stores/ticket.store'
import { formatMoney } from '@/utils/money.utils'

const authStore = useAuthStore()
const ticketStore = useTicketStore()
const { confirm } = useConfirmDialog()
const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const mobileSlipOpen = ref(false)

const mainLinks = computed(() => [
  { key: 'home', label: 'Meciuri', to: { name: 'home' } },
  { key: 'leagues', label: 'Ligi', to: { name: 'leagues' } },
  { key: 'teams', label: 'Echipe', to: { name: 'teams' } },
  { key: 'results', label: 'Rezultate', to: { name: 'results' } },
  { key: 'tickets', label: 'Biletele mele', to: { name: 'tickets' } },
])

const headerLinks = computed(() =>
  mainLinks.value.map((link) => ({
    key: link.key,
    label: link.label,
    to: link.to,
    active:
      (link.key === 'teams' && ['teams', 'team', 'team-results'].includes(String(route.name ?? ''))) ||
      (link.key === 'leagues' && ['leagues', 'league', 'league-results'].includes(String(route.name ?? ''))) ||
      route.name === link.key,
  })),
)

const utilityLinks = computed(() => {
  const links = [
    {
      key: 'wallet',
      label: 'Portofel',
      to: { name: 'wallet' },
      active: route.name === 'wallet',
      desktopClass: 'md:block',
    },
    {
      key: 'profile',
      label: 'Profil',
      to: { name: 'profile' },
      active: route.name === 'profile',
      desktopClass: 'lg:block',
    },
  ]

  if (authStore.isAdmin) {
    links.unshift({
      key: 'admin',
      label: 'Administrare',
      to: { name: 'admin-dashboard' },
      active: String(route.name ?? '').startsWith('admin'),
      desktopClass: 'md:block',
    })
  }

  return links
})

const mobileQuickLinks = computed(() =>
  utilityLinks.value.filter((link) => ['wallet', 'profile'].includes(link.key)),
)

const mobileMenuLinks = computed(() =>
  utilityLinks.value.filter((link) => !['wallet', 'profile'].includes(link.key)),
)

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

async function handleLogout() {
  const confirmed = await confirm({
    title: 'Iesire din cont',
    message: 'Sunteti sigur ca vreti sa iesiti din cont?',
    description: 'Va trebui sa va autentificati din nou pentru a reveni in aplicatie.',
    confirmLabel: 'Iesi din cont',
    cancelLabel: 'Raman conectat',
    variant: 'danger',
  })

  if (!confirmed) {
    return
  }

  await authStore.logout()
  await router.push({ name: 'login' })
}

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
    <header class="fixed left-0 top-0 z-50 w-full border-b border-white/20 bg-black shadow-lg">
      <div class="mx-auto grid h-[var(--app-header-height)] app-shell-width grid-cols-[auto_1fr_auto] items-center gap-2 px-2.5 sm:gap-4 sm:px-4 md:gap-6 md:px-8">
        <div class="flex min-w-0 items-center">
          <RouterLink to="/" class="text-[15px] font-bold tracking-tight text-white max-[390px]:text-[14px] max-[360px]:text-[13px] sm:text-2xl">
            KickOffBet
          </RouterLink>
        </div>

        <nav v-if="authStore.isAuthenticated" class="hidden min-w-0 items-center justify-center gap-4 font-bold xl:flex">
          <RouterLink
            v-for="link in headerLinks"
            :key="link.key"
            :to="link.to"
            class="px-2 py-2 text-lg transition-colors 2xl:text-lg"
            :class="link.active ? 'text-white' : 'text-gray-300 hover:text-blue-400'"
          >
            {{ link.label }}
          </RouterLink>
        </nav>

        <div class="flex items-center justify-end gap-2.5 sm:gap-4 md:gap-6">
          <template v-if="authStore.isAuthenticated">
            <div class="flex min-w-0 items-center justify-end gap-2 xl:hidden max-[390px]:gap-1.5 max-[360px]:gap-1">
              <p class="max-w-[84px] truncate text-sm font-bold text-green-500 max-[390px]:max-w-[76px] max-[390px]:text-[13px] max-[360px]:max-w-[68px] max-[360px]:text-[12px]">
                {{ formatMoney(authStore.balance) }}
              </p>

              <div class="flex min-w-0 items-center justify-end gap-2 max-[390px]:gap-1.5 max-[360px]:gap-1">
                <RouterLink
                  v-for="link in mobileQuickLinks"
                  :key="link.key"
                  :to="link.to"
                  class="truncate text-sm font-bold transition-colors max-[390px]:text-[13px] max-[360px]:text-[12px]"
                  :class="link.active ? 'text-white' : 'text-gray-300 hover:text-blue-400'"
                >
                  {{ link.label }}
                </RouterLink>
              </div>
            </div>

            <button
              type="button"
              class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/5 text-gray-200 transition-colors hover:border-blue-500/30 hover:text-white xl:hidden"
              @click="toggleMobileMenu"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>

            <RouterLink
              v-for="link in utilityLinks"
              :key="link.key"
              :to="link.to"
              class="hidden text-lg font-bold transition-colors"
              :class="[
                link.desktopClass,
                link.key === 'admin'
                  ? 'text-red-500 hover:text-red-400'
                  : link.active
                    ? 'text-white'
                    : 'text-gray-300 hover:text-blue-400',
              ]"
            >
              {{ link.label }}
            </RouterLink>

            <div class="hidden text-right sm:block">
              <p class="text-sm font-semibold text-white">{{ authStore.fullName }}</p>
              <p class="text-xs uppercase tracking-[0.2em] text-green-500">{{ formatMoney(authStore.balance) }}</p>
            </div>

            <AppButton variant="ghost" class="!hidden text-lg font-bold xl:!inline-flex" @click="handleLogout">Iesire</AppButton>
          </template>
        </div>
      </div>
    </header>

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
        class="fixed inset-0 z-[60] bg-black/70 backdrop-blur-sm xl:hidden"
        @click="closeMobileOverlays"
      />
    </transition>

    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="-translate-y-3 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="-translate-y-3 opacity-0"
    >
      <div
        v-if="mobileMenuOpen"
        class="fixed inset-x-2 top-[64px] z-[65] max-h-[calc(100vh-74px)] overflow-y-auto rounded-2xl border border-white/10 bg-black/95 p-2.5 shadow-[0_30px_60px_rgba(0,0,0,0.45)] max-[390px]:inset-x-1.5 max-[390px]:top-[60px] max-[390px]:max-h-[calc(100vh-66px)] max-[390px]:p-2 max-[360px]:inset-x-1 max-[360px]:top-[58px] max-[360px]:max-h-[calc(100vh-62px)] max-[360px]:p-1.5 sm:inset-x-3 sm:top-[76px] sm:max-h-[calc(100vh-92px)] sm:p-4 xl:hidden"
      >
        <div class="space-y-4">
          <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Utilizator</p>
            <p class="mt-2 text-lg font-bold text-white">{{ authStore.fullName }}</p>
            <p class="mt-1 text-xs text-gray-400">Acces rapid din bara de sus.</p>
          </div>

          <nav class="space-y-2">
            <RouterLink
              v-for="link in headerLinks"
              :key="link.key"
              :to="link.to"
              class="flex items-center justify-between rounded-xl border px-4 py-3 text-sm font-semibold transition-colors"
              :class="link.active ? 'border-blue-500/40 bg-blue-600/15 text-white' : 'border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 hover:text-white'"
            >
              <span>{{ link.label }}</span>
            </RouterLink>
          </nav>

          <div class="grid gap-2">
            <RouterLink
              v-for="link in mobileMenuLinks"
              :key="link.key"
              :to="link.to"
              class="rounded-xl border px-4 py-3 text-sm font-semibold transition-colors"
              :class="
                link.key === 'admin'
                  ? 'border-red-500/20 bg-red-600/10 text-white hover:bg-red-600/15'
                  : link.active
                    ? 'border-blue-500/40 bg-blue-600/15 text-white'
                    : 'border-white/10 bg-white/5 text-gray-200 hover:bg-white/10 hover:text-white'
              "
            >
              {{ link.label }}
            </RouterLink>
          </div>

          <AppButton class="w-full" variant="outline" @click="handleLogout">Iesire</AppButton>
        </div>
      </div>
    </transition>

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
          <div class="flex items-center justify-between border-b border-white/10 px-3 py-2.5 max-[390px]:px-2.5 max-[390px]:py-2 max-[360px]:px-2 max-[360px]:py-1.5 sm:px-4 sm:py-3">
            <div>
              <p class="text-[13px] font-semibold text-white max-[390px]:text-[12px] max-[360px]:text-[11px] sm:text-sm">Biletul meu</p>
              <p class="text-[11px] text-gray-400 max-[390px]:text-[10px] max-[360px]:text-[9px] sm:text-xs">{{ formatMoney(authStore.balance) }}</p>
            </div>
            <button
              type="button"
              class="rounded-full border border-white/10 bg-white/5 p-1.5 text-gray-300 transition-colors hover:text-white max-[390px]:p-1.25 max-[360px]:p-1 sm:p-2"
              @click="closeMobileSlip"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 max-[390px]:h-3.5 max-[390px]:w-3.5 max-[360px]:h-3 max-[360px]:w-3 sm:h-5 sm:w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
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
      class="fixed bottom-2.5 right-2.5 z-[55] inline-flex items-center gap-1.5 rounded-full border border-blue-500/30 bg-blue-600/90 px-3 py-2.25 text-[11px] font-semibold text-white shadow-[0_16px_34px_rgba(37,99,235,0.35)] transition-colors hover:bg-blue-500 max-[390px]:bottom-2 max-[390px]:right-2 max-[390px]:gap-1 max-[390px]:px-2.5 max-[390px]:py-2 max-[390px]:text-[10px] max-[360px]:px-2 max-[360px]:py-1.75 max-[360px]:text-[9px] sm:bottom-4 sm:right-4 sm:gap-2 sm:px-4 sm:py-3 sm:text-sm lg:hidden"
      @click="toggleMobileSlip"
    >
      <span>Bilet</span>
      <span class="rounded-full bg-white/15 px-2 py-0.5 text-xs max-[390px]:px-1.5 max-[390px]:text-[10px] max-[360px]:px-1 max-[360px]:text-[9px]">{{ ticketStore.selectionCount }}</span>
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

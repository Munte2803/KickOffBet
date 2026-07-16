<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import AppShellFrame from '@/components/AppShellFrame.vue'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth.store'
import { adminTopTabs, getAdminResource } from '@/constants/admin-navigation'

const authStore = useAuthStore()
const { confirm } = useConfirmDialog()
const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)

const currentResource = computed(() => getAdminResource(String(route.name ?? '')))

function isTopTabActive(resource: (typeof adminTopTabs)[number]['resource']) {
  return currentResource.value === resource
}

watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
  },
)

async function handleLogout() {
  const confirmed = await confirm({
    title: 'Iesire din cont',
    message: 'Sunteti sigur ca vreti sa iesiti din cont?',
    description: 'Sesiunea curenta de administrare va fi inchisa imediat.',
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
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}
</script>

<template>
  <div class="min-h-screen bg-black text-white">
    <!-- Top bar (always visible; hamburger toggles sidebar drawer) -->
    <header class="fixed left-0 top-0 z-50 w-full border-b border-white/20 bg-black shadow-lg">
      <div class="mx-auto grid h-[var(--app-header-height)] app-shell-width grid-cols-[auto_1fr_auto] items-center gap-2 px-2.5 sm:gap-4 sm:px-4">
        <button
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
        <div class="text-right">
          <p class="text-sm font-semibold text-white">{{ authStore.fullName }}</p>
          <p class="text-[10px] uppercase tracking-[0.2em] text-gray-500">Administrator</p>
        </div>
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
        v-if="mobileMenuOpen"
        class="fixed inset-0 z-[60] bg-black/70 backdrop-blur-sm"
        @click="closeMobileMenu"
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
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Administrator</p>
          <p class="mt-2 text-lg font-bold text-white">{{ authStore.fullName }}</p>
        </div>

        <nav class="mt-3 flex-1 space-y-1 overflow-y-auto">
          <RouterLink
            v-for="tab in adminTopTabs"
            :key="tab.resource"
            :to="tab.to"
            class="flex items-center rounded-lg border px-3 py-2 text-sm font-semibold"
            :class="isTopTabActive(tab.resource) ? 'border-blue-500/40 bg-blue-600/15 text-white' : 'border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 hover:text-white'"
          >
            {{ tab.label }}
          </RouterLink>

          <RouterLink
            :to="{ name: 'home' }"
            class="mt-3 flex items-center rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm font-semibold text-gray-200 hover:bg-white/10 hover:text-white"
          >
            Zona utilizator
          </RouterLink>
        </nav>

        <AppButton class="mt-3 w-full" variant="outline" @click="handleLogout">Iesire</AppButton>
      </aside>
    </transition>

    <AppShellFrame content-class="mt-0">
      <RouterView :key="route.fullPath" />
    </AppShellFrame>
  </div>
</template>

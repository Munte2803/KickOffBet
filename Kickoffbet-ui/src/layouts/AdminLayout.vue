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
const responsiveMenuOpen = ref(false)

const currentResource = computed(() => getAdminResource(String(route.name ?? '')))

function isTopTabActive(resource: (typeof adminTopTabs)[number]['resource']) {
  return currentResource.value === resource
}

watch(
  () => route.fullPath,
  () => {
    responsiveMenuOpen.value = false
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

function toggleResponsiveMenu() {
  responsiveMenuOpen.value = !responsiveMenuOpen.value
}

function closeResponsiveMenu() {
  responsiveMenuOpen.value = false
}
</script>

<template>
  <div class="min-h-screen bg-black text-white">
    <header class="fixed left-0 top-0 z-50 w-full border-b border-white/20 bg-black shadow-lg">
      <div class="mx-auto grid h-[65px] app-shell-width grid-cols-[auto_1fr_auto] items-center gap-6 px-4 md:px-8">
        <div class="flex min-w-0 items-center">
          <RouterLink :to="{ name: 'admin-dashboard' }" class="text-2xl font-bold text-white">
            KickOffBet
          </RouterLink>
        </div>

        <nav class="hidden min-w-0 items-center justify-center gap-4 font-bold xl:flex">
          <RouterLink
            v-for="tab in adminTopTabs"
            :key="tab.resource"
            :to="tab.to"
            class="px-2 py-2 text-lg transition-colors 2xl:text-lg"
            :class="isTopTabActive(tab.resource) ? 'text-white' : 'text-gray-300 hover:text-blue-400'"
          >
            {{ tab.label }}
          </RouterLink>
        </nav>

        <div class="flex items-center justify-end gap-2.5 sm:gap-4 md:gap-6">
          <button
            type="button"
            class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/5 text-gray-200 transition-colors hover:border-blue-500/30 hover:text-white xl:hidden"
            @click="toggleResponsiveMenu"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <RouterLink :to="{ name: 'home' }" class="hidden text-lg font-bold text-gray-300 transition-colors hover:text-blue-400 md:block">
            Zona utilizator
          </RouterLink>
          <div class="hidden text-right sm:block">
            <p class="text-sm font-semibold text-white">{{ authStore.fullName }}</p>
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Administrator</p>
          </div>
          <AppButton variant="ghost" class="text-lg font-bold" @click="handleLogout">Iesire</AppButton>
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
        v-if="responsiveMenuOpen"
        class="fixed inset-0 z-[60] bg-black/70 backdrop-blur-sm xl:hidden"
        @click="closeResponsiveMenu"
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
        v-if="responsiveMenuOpen"
        class="fixed inset-x-3 top-[76px] z-[65] max-h-[calc(100vh-92px)] overflow-y-auto rounded-2xl border border-white/10 bg-black/95 p-4 shadow-[0_30px_60px_rgba(0,0,0,0.45)] xl:hidden"
      >
        <div class="space-y-4">
          <div class="rounded-2xl border border-white/10 bg-white/5 p-3">
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Administrator</p>
            <p class="mt-2 text-lg font-bold text-white">{{ authStore.fullName }}</p>
          </div>

          <nav class="space-y-2">
            <RouterLink
              v-for="tab in adminTopTabs"
              :key="tab.resource"
              :to="tab.to"
              class="flex items-center justify-between rounded-xl border px-4 py-3 text-sm font-semibold transition-colors"
              :class="isTopTabActive(tab.resource) ? 'border-blue-500/40 bg-blue-600/15 text-white' : 'border-white/10 bg-white/5 text-gray-300 hover:bg-white/10 hover:text-white'"
            >
              <span>{{ tab.label }}</span>
            </RouterLink>
          </nav>

          <div class="grid gap-2">
            <RouterLink
              :to="{ name: 'home' }"
              class="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm font-semibold text-gray-200 transition-colors hover:bg-white/10 hover:text-white"
            >
              Zona utilizator
            </RouterLink>
          </div>

          <AppButton class="w-full" variant="outline" @click="handleLogout">Iesire</AppButton>
        </div>
      </div>
    </transition>

    <AppShellFrame content-class="mt-0">

      <RouterView :key="route.fullPath" />
    </AppShellFrame>
  </div>
</template>

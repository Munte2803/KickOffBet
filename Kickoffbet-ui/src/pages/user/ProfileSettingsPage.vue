<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast.store'
import { deactivateAccount } from '@/api/profile.api'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import AppButton from '@/components/AppButton.vue'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth.store'

const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const router = useRouter()

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

async function handleDeactivate() {
  const confirmed = await confirm({
    title: 'Dezactivare cont',
    message: 'Sunteti sigur ca vreti sa va dezactivati contul?',
    description: 'Aceasta actiune va inchide sesiunea curenta si va bloca accesul pana la reactivare.',
    confirmLabel: 'Dezactiveaza contul',
    cancelLabel: 'Pastreaza contul',
    variant: 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await deactivateAccount()
    authStore.clearAuth()
    await router.push({ name: 'login' })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut dezactiva contul.')
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Setari" subtitle="Preferinte de cont si iesire din aplicatie" />

    <Panel no-hover>
      <SectionHeader title="Sesiune" />

      <div class="mt-4 rounded-xl border border-line bg-surface p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="text-sm font-bold text-fg">Iesire din cont</h3>
            <p class="mt-1 text-xs text-muted">Inchide sesiunea curenta pe acest dispozitiv.</p>
          </div>
          <AppButton variant="outline" @click="handleLogout">Iesire</AppButton>
        </div>
      </div>
    </Panel>

    <Panel no-hover>
      <SectionHeader title="Cont" />

      <div class="mt-4 rounded-xl border border-red-500/20 bg-red-500/5 p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="text-sm font-bold text-fg">Dezactiveaza contul</h3>
            <p class="mt-1 text-xs text-muted">Aceasta actiune iti blocheaza accesul pana la reactivare.</p>
          </div>
          <AppButton variant="danger" @click="handleDeactivate">Dezactiveaza contul</AppButton>
        </div>
      </div>
    </Panel>
  </div>
</template>

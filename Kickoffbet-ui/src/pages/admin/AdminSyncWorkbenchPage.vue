<script setup lang="ts">
import { ref } from 'vue'
import {
  triggerFullSync,
  syncAllMatches,
  quickSyncMatches,
  recalculateAutomaticOdds,
  triggerSeed,
  type SeedResult,
} from '@/api/admin-sync.api'
import { useToastStore } from '@/stores/toast.store'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'

const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const seedLoading = ref(false)
const lastSeedResult = ref<SeedResult | null>(null)

async function runAction(action: () => Promise<void>, message: string) {
  try {
    await action()
    toastStore.showSuccess(message)
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut porni sincronizarea.')
  }
}

async function runSeed() {
  const ok = await confirm({
    title: 'Genereaza date demo',
    message: 'Aceasta operatie va sterge toti utilizatorii seed existenti si va recrea ~510 utilizatori, ~10k bilete si ~25k tranzactii.',
    description: 'Datele reale (utilizatorii non-seed) nu sunt afectate. Poate dura cateva secunde.',
    confirmLabel: 'Genereaza',
    cancelLabel: 'Renunta',
    variant: 'primary',
  })
  if (!ok) return
  seedLoading.value = true
  try {
    const result = await triggerSeed()
    lastSeedResult.value = result
    toastStore.showSuccess(
      `Seed gata: ${result.usersCreated} utilizatori, ${result.ticketsCreated} bilete, ${result.transactionsCreated} tranzactii in ${result.durationMs} ms.`,
    )
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Seed-ul a esuat.')
  } finally {
    seedLoading.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - sincronizare" subtitle="Toate actiunile manuale de sincronizare, intr-o singura zona." />

    <Panel id="sync-actions" no-hover class="space-y-4">
      <p class="text-sm text-muted">Porneste manual fluxurile de sincronizare pentru ligi, echipe si meciuri.</p>
      <div class="flex flex-wrap gap-3">
        <div id="sync-full">
          <AppButton variant="outline" @click="runAction(triggerFullSync, 'Sincronizarea completa a pornit.')">Sincronizare completa</AppButton>
        </div>
        <div id="sync-all-matches">
          <AppButton variant="outline" @click="runAction(syncAllMatches, 'Sincronizarea tuturor meciurilor a pornit.')">Toate meciurile</AppButton>
        </div>
        <div id="sync-quick">
          <AppButton variant="outline" @click="runAction(quickSyncMatches, 'Sincronizarea rapida a pornit.')">Sincronizare rapida</AppButton>
        </div>
        <div id="sync-automatic-odds">
          <AppButton variant="outline" @click="runAction(recalculateAutomaticOdds, 'Recalcularea cotelor automate a pornit.')">
            Recalculeaza cotele automate
          </AppButton>
        </div>
      </div>
    </Panel>

    <Panel id="seed-demo" no-hover class="space-y-4">
      <h2 class="text-lg font-semibold text-fg">Date demo</h2>
      <p class="text-sm text-muted">
        Genereaza ~510 utilizatori seed cu istoric coerent de depozite, bilete (WON/LOST/PENDING/CANCELLED) si tranzactii pe ultimele 365 zile.
        Util pentru testarea graficelor, exporturilor si rapoartelor. Idempotent — re-rularea sterge si recreeaza.
      </p>
      <div>
        <AppButton :loading="seedLoading" @click="runSeed">Genereaza date demo</AppButton>
      </div>
      <div
        v-if="lastSeedResult"
        class="rounded-xl border border-line bg-surface px-4 py-3 text-sm text-muted"
      >
        <p>Ultima rulare:</p>
        <ul class="mt-1 list-disc space-y-0.5 pl-5 text-xs text-muted">
          <li>{{ lastSeedResult.usersCreated }} utilizatori</li>
          <li>{{ lastSeedResult.ticketsCreated }} bilete</li>
          <li>{{ lastSeedResult.transactionsCreated }} tranzactii</li>
          <li>{{ lastSeedResult.durationMs }} ms</li>
        </ul>
      </div>
    </Panel>
  </div>
</template>


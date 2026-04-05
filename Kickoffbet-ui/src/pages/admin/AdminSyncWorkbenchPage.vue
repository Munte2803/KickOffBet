<script setup lang="ts">
import {
  triggerFullSync,
  syncAllMatches,
  quickSyncMatches,
  recalculateAutomaticOdds,
} from '@/api/admin-sync.api'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'

const toastStore = useToastStore()

async function runAction(action: () => Promise<void>, message: string) {
  try {
    await action()
    toastStore.showSuccess(message)
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut porni sincronizarea.')
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - sincronizare" subtitle="Toate actiunile manuale de sincronizare, intr-o singura zona." />

    <Panel id="sync-actions" no-hover class="space-y-4">
      <p class="text-sm text-gray-400">Porneste manual fluxurile de sincronizare pentru ligi, echipe si meciuri.</p>
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
  </div>
</template>


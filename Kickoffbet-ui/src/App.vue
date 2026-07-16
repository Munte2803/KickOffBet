<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import AppConfirmDialog from '@/components/AppConfirmDialog.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import { ensureFreshSession } from '@/api/axios'

let sessionTimer: number | null = null

async function syncSession() {
  try {
    await ensureFreshSession(30_000)
  } catch {
  }
}

onMounted(() => {
  void syncSession()
  sessionTimer = window.setInterval(() => {
    void syncSession()
  }, 30_000)
})

onBeforeUnmount(() => {
  if (sessionTimer !== null) {
    window.clearInterval(sessionTimer)
  }
})
</script>

<template>
  <RouterView />
  <ToastContainer />
  <AppConfirmDialog />
</template>


<script setup lang="ts">
import { useToastStore } from '@/stores/toast.store'

const toast = useToastStore()
</script>

<template>
  <Teleport to="body">
    <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full">
      <TransitionGroup name="toast">
        <div
          v-for="t in toast.toasts"
          :key="t.id"
          @click="toast.dismiss(t.id)"
          :class="[
            'flex items-start gap-3 px-4 py-3 rounded-lg border shadow-lg cursor-pointer text-sm font-medium',
            t.type === 'success' ? 'bg-green-500/90 border-green-500/50 text-green-300' :
            t.type === 'error' ? 'bg-red-600/90 border-red-500/50 text-red-300' :
            'bg-blue-600/90 border-blue-500/50 text-blue-300',
          ]"
        >
          <span>{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(100%); }
</style>

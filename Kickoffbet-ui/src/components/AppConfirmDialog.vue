<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import AppButton from '@/components/AppButton.vue'
import { useConfirmStore } from '@/stores/confirm.store'

const confirmStore = useConfirmStore()

const confirmVariant = computed(() => (confirmStore.variant === 'danger' ? 'danger' : 'primary'))

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && confirmStore.open) {
    confirmStore.cancel()
  }
}

watch(
  () => confirmStore.open,
  (isOpen) => {
    if (isOpen) {
      window.addEventListener('keydown', handleKeydown)
      return
    }

    window.removeEventListener('keydown', handleKeydown)
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="confirmStore.open"
      class="fixed inset-0 z-[90] flex items-center justify-center bg-app/80 px-4 py-6 backdrop-blur-sm"
      @click.self="confirmStore.cancel()"
    >
      <div class="w-full max-w-md rounded-2xl border border-line bg-app shadow-[0_30px_60px_var(--c-shadow-strong)]">
        <div class="border-b border-line px-5 py-4">
          <p class="text-xs uppercase tracking-[0.22em] text-subtle">Confirmare</p>
          <h2 class="mt-2 text-lg font-bold text-fg">{{ confirmStore.title }}</h2>
        </div>

        <div class="space-y-3 px-5 py-4">
          <p class="text-sm text-fg">{{ confirmStore.message }}</p>
          <p v-if="confirmStore.description" class="text-xs text-muted">{{ confirmStore.description }}</p>
        </div>

        <div class="flex flex-col-reverse gap-2 border-t border-line px-5 py-4 sm:flex-row sm:justify-end">
          <AppButton variant="outline" @click="confirmStore.cancel()">
            {{ confirmStore.cancelLabel }}
          </AppButton>
          <AppButton :variant="confirmVariant" @click="confirmStore.confirm()">
            {{ confirmStore.confirmLabel }}
          </AppButton>
        </div>
      </div>
    </div>
  </transition>
</template>

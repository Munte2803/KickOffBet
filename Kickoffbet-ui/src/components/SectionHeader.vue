<script setup lang="ts">
import LogoFrame from '@/components/LogoFrame.vue'

defineProps<{
  title: string
  logo?: string | null
}>()
</script>

<template>
  <div class="mb-2 flex items-center justify-between border-b border-white/10 px-0.5 pb-1 max-[390px]:mb-1.5 max-[390px]:pb-0.75 max-[360px]:mb-1 max-[360px]:pb-0.5 sm:mb-3 sm:px-1 sm:pb-2">
    <div class="flex items-center gap-1.5 max-[390px]:gap-1 max-[360px]:gap-0.75 sm:gap-3">
      <LogoFrame v-if="logo" :src="logo" size="sm" />
      <span class="text-[15px] font-bold text-gray-100 max-[390px]:text-[14px] max-[360px]:text-[13px] sm:text-lg">{{ title }}</span>
    </div>
    <slot name="actions" />
  </div>
</template>


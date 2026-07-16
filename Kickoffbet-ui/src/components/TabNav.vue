<script setup lang="ts">
defineProps<{
  tabs: { key: string; label: string; count?: number }[]
  active: string
}>()

defineEmits<{ change: [key: string] }>()
</script>

<template>
  <div class="flex overflow-x-auto border-b border-white/10 bg-black/20">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      @click="$emit('change', tab.key)"
      :class="[
        'flex-1 whitespace-nowrap border-b-2 px-2.5 py-2.5 text-[11px] font-bold transition-colors sm:px-4 sm:py-4 sm:text-sm',
        tab.key === active
          ? 'border-blue-500 bg-white/5 text-blue-500'
          : 'border-transparent text-gray-400 hover:bg-white/5 hover:text-white',
      ]"
    >
      {{ tab.label }}
      <span v-if="tab.count !== undefined" class="ml-1 opacity-70">({{ tab.count }})</span>
    </button>
  </div>
</template>

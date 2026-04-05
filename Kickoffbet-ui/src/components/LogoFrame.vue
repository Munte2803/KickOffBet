<script setup lang="ts">
const props = withDefaults(defineProps<{
  src?: string | null
  alt?: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'card'
  shape?: 'circle' | 'rounded'
}>(), {
  src: null,
  alt: '',
  size: 'md',
  shape: 'circle',
})

const sizeClasses: Record<NonNullable<typeof props.size>, string> = {
  xs: 'h-4 w-4 p-[2px]',
  sm: 'h-8 w-8 p-[3px]',
  md: 'h-10 w-10 p-[3px]',
  lg: 'h-12 w-12 p-[4px]',
  xl: 'h-14 w-14 p-[4px]',
  card: 'h-6 w-6 p-[2px] sm:h-8 sm:w-8 sm:p-[3px] lg:h-10 lg:w-10 lg:p-[3px]',
}
</script>

<template>
  <div
    v-if="src"
    class="flex flex-shrink-0 items-center justify-center overflow-hidden border border-white/80 bg-white shadow-[0_12px_28px_rgba(0,0,0,0.28)]"
    :class="[
      sizeClasses[size],
      shape === 'rounded' ? 'rounded-xl' : 'rounded-full',
    ]"
  >
    <img
      :src="src"
      :alt="alt"
      class="h-full w-full object-contain"
      :class="shape === 'rounded' ? 'rounded-[10px]' : 'rounded-full'"
    >
  </div>
</template>

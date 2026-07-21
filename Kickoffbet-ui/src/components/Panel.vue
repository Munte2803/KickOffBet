<script setup lang="ts">
defineOptions({
  inheritAttrs: false,
})

const props = defineProps<{
  class?: string
  noHover?: boolean
}>()
</script>

<template>
  <div
    v-bind="$attrs"
    :class="[
      'rounded-xl bg-surface p-2.5 sm:p-4 transition-all duration-300',
      props.noHover ? 'border border-line' : 'border border-transparent hover:border-blue-600',
      props.class,
    ]"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  contentClass?: string
  mainClass?: string
}>(), {
  contentClass: 'mt-0',
  mainClass: 'block',
})
</script>

<template>
  <div class="mx-auto app-shell-width px-2.5 pb-7 max-[390px]:px-2 max-[390px]:pb-5 max-[360px]:px-1.5 max-[360px]:pb-4 sm:px-4 md:px-6" style="padding-top: var(--app-header-height);">
    <div v-if="$slots.mobileNav" class="mt-6">
      <slot name="mobileNav" />
    </div>

    <div :class="contentClass">
      <aside v-if="$slots.sidebar" class="hidden lg:block">
        <slot name="sidebar" />
      </aside>

      <div :class="mainClass">
        <main class="min-w-0">
          <slot />
        </main>

        <aside v-if="$slots.aside" class="hidden min-w-0 lg:block" aria-hidden="true">
          <slot name="aside" />
        </aside>
      </div>
    </div>
  </div>
</template>

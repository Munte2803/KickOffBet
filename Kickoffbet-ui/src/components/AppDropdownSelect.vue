<script setup lang="ts">
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'

interface Option {
  value: string
  label: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    options: Option[]
    placeholder?: string
    disabled?: boolean
    invalid?: boolean
    ariaLabel?: string
  }>(),
  { placeholder: '', disabled: false, invalid: false, ariaLabel: '' },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const menuStyle = ref<Record<string, string>>({})

const selectedLabel = computed(() => {
  const found = props.options.find((o) => o.value === props.modelValue)
  return found ? found.label : props.placeholder
})

const hasValue = computed(() => props.options.some((o) => o.value === props.modelValue))

function positionMenu() {
  const el = triggerRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom
  const maxHeight = Math.min(260, Math.max(spaceBelow - 12, 140))
  menuStyle.value = {
    left: `${rect.left}px`,
    top: `${rect.bottom + 4}px`,
    width: `${rect.width}px`,
    maxHeight: `${maxHeight}px`,
  }
}

function scrollToSelected() {
  const menu = menuRef.value
  if (!menu) return
  const active = menu.querySelector('[data-active="true"]') as HTMLElement | null
  if (active) active.scrollIntoView({ block: 'center' })
}

function onDocInteract(event: Event) {
  const target = event.target as Node
  if (triggerRef.value?.contains(target) || menuRef.value?.contains(target)) return
  close()
}

function addListeners() {
  document.addEventListener('pointerdown', onDocInteract, true)
  window.addEventListener('resize', positionMenu)
  window.addEventListener('scroll', positionMenu, true)
}

function removeListeners() {
  document.removeEventListener('pointerdown', onDocInteract, true)
  window.removeEventListener('resize', positionMenu)
  window.removeEventListener('scroll', positionMenu, true)
}

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    await nextTick()
    positionMenu()
    addListeners()
    scrollToSelected()
  } else {
    removeListeners()
  }
}

function close() {
  if (!open.value) return
  open.value = false
  removeListeners()
}

function select(value: string) {
  emit('update:modelValue', value)
  close()
  triggerRef.value?.focus()
}

onBeforeUnmount(removeListeners)
</script>

<template>
  <div class="relative w-full">
    <button
      ref="triggerRef"
      type="button"
      :disabled="disabled"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      @click="toggle"
      :class="[
        'app-select flex w-full min-w-0 items-center justify-between gap-1 rounded-lg border bg-surface-2 px-2 py-1.75 pr-5 text-left text-[12px] font-medium transition-colors focus:outline-none disabled:cursor-not-allowed disabled:opacity-50 sm:px-3 sm:py-2 sm:pr-7 sm:text-sm',
        invalid ? 'border-red-500 focus:border-red-400' : 'border-line focus:border-blue-500',
        hasValue ? 'text-fg' : 'text-subtle',
      ]"
    >
      <span class="truncate">{{ selectedLabel }}</span>
    </button>

    <Teleport to="body">
      <ul
        v-if="open"
        ref="menuRef"
        :style="menuStyle"
        class="fixed z-[1000] overflow-y-auto rounded-lg border border-line bg-surface py-1 shadow-lg shadow-black/20 ring-1 ring-line"
        role="listbox"
      >
        <li
          v-for="option in options"
          :key="option.value"
          role="option"
          :data-active="option.value === modelValue"
          :aria-selected="option.value === modelValue"
          @click="select(option.value)"
          :class="[
            'cursor-pointer px-3 py-1.5 text-[12px] sm:text-sm',
            option.value === modelValue ? 'bg-blue-600 text-white' : 'text-fg hover:bg-surface-2',
          ]"
        >
          {{ option.label }}
        </li>
      </ul>
    </Teleport>
  </div>
</template>

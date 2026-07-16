<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import FormInput from '@/components/FormInput.vue'
import AdminEntityPicker from '@/components/AdminEntityPicker.vue'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
import { searchUsers } from '@/api/admin-users.api'
import type { UserList } from '@/types/admin-user.types'

const props = withDefaults(defineProps<{
  modelValue: string
  title: string
  placeholder?: string
  emptyMessage?: string
  resultsTitle?: string
}>(), {
  placeholder: 'utilizator@exemplu.com',
  emptyMessage: 'Introdu cel putin 2 caractere pentru a cauta utilizatori.',
  resultsTitle: 'Rezultate cautare',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [value: UserList | null]
}>()

const emailSearch = ref('')
const pagination = usePagination(PAGE_SIZES.NORMAL)

const usersQuery = useQuery({
  queryKey: ['admin-user-search-picker', props.title, emailSearch, pagination.request],
  queryFn: () => searchUsers(emailSearch.value.trim(), pagination.request.value),
  enabled: computed(() => emailSearch.value.trim().length >= 2),
})

const items = computed(() =>
  (usersQuery.data.value?.content ?? []).map((user) => ({
    id: user.id,
    label: `${user.firstName} ${user.lastName}`,
    subtitle: user.email,
    active: user.status === 'ACTIVE',
  })),
)

watch(emailSearch, () => {
  pagination.reset()
})

watch(
  () => props.modelValue,
  (value) => {
    if (!value) {
      emit('select', null)
    }
  },
)

function handleSelection(value: string | string[]) {
  if (Array.isArray(value)) return

  emit('update:modelValue', value)

  const user = usersQuery.data.value?.content.find((candidate) => candidate.id === value) ?? null
  emit('select', user)
}
</script>

<template>
  <div class="space-y-3">
    <FormInput v-model="emailSearch" :label="title" :placeholder="placeholder" />

    <div
      v-if="emailSearch.trim().length < 2"
      class="rounded-xl border border-dashed border-white/10 bg-black/30 px-4 py-5 text-sm text-gray-400"
    >
      {{ emptyMessage }}
    </div>

    <AdminEntityPicker
      v-else
      :model-value="modelValue"
      :title="resultsTitle"
      :items="items"
      :page="usersQuery.data.value?.number ?? 0"
      :total-pages="usersQuery.data.value?.totalPages ?? 0"
      :loading="usersQuery.isLoading.value"
      empty-message="Nu exista utilizatori pentru emailul introdus."
      @update:model-value="handleSelection"
      @change-page="pagination.setPage"
    />
  </div>
</template>


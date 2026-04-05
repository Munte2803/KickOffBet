<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { RouterLink } from 'vue-router'
import { createAdmin, getPendingVerification, getUsers, getUsersByStatus } from '@/api/admin-users.api'
import { adminCreateSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AppPagination from '@/components/AppPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import AdminSortSelect from '@/components/AdminSortSelect.vue'
import AdminUserSearchPicker from '@/components/AdminUserSearchPicker.vue'
import type { UserStatus } from '@/types/enums'
import type { UserList } from '@/types/admin-user.types'
import { useToastStore } from '@/stores/toast.store'
import { formatDateShort } from '@/utils/date.utils'
import { translateEnumLabel } from '@/utils/labels.utils'

const toastStore = useToastStore()
const pagination = usePagination(12)
const selectedSearchUserId = ref('')
const selectedSearchUser = ref<UserList | null>(null)
const status = ref<'ALL' | 'PENDING_VERIFICATION' | UserStatus>('PENDING_VERIFICATION')
const sortBy = ref('createdAt,asc')
const listTriggered = ref(false)

const pageRequest = computed(() => ({
  ...pagination.request.value,
  sort: sortBy.value,
}))

const sortOptions = [
  { label: 'Data crearii descrescator', value: 'createdAt,desc' },
  { label: 'Data crearii crescator', value: 'createdAt,asc' },
  { label: 'Email A-Z', value: 'email,asc' },
  { label: 'Email Z-A', value: 'email,desc' },
  { label: 'Nume A-Z', value: 'lastName,asc' },
  { label: 'Nume Z-A', value: 'lastName,desc' },
  { label: 'Status A-Z', value: 'status,asc' },
  { label: 'Status Z-A', value: 'status,desc' },
]

const listedUsersQuery = useQuery({
  queryKey: ['admin-users-list', status, pageRequest],
  queryFn: async () => {
    if (status.value === 'PENDING_VERIFICATION') {
      return getPendingVerification(pageRequest.value)
    }

    if (status.value !== 'ALL') {
      return getUsersByStatus(status.value, pageRequest.value)
    }

    return getUsers(pageRequest.value)
  },
  enabled: false,
})

function resetSearchResults() {
  selectedSearchUserId.value = ''
  selectedSearchUser.value = null
}

function clearEmailSearch() {
  resetSearchResults()
}

async function clearListFilters() {
  pagination.reset()
  status.value = 'PENDING_VERIFICATION'
  sortBy.value = 'createdAt,asc'
  resetSearchResults()
  listTriggered.value = true
  await listedUsersQuery.refetch()
}

async function runListQuery() {
  pagination.reset()
  resetSearchResults()
  listTriggered.value = true
  await listedUsersQuery.refetch()
}

function handleSearchUserSelect(user: UserList | null) {
  selectedSearchUser.value = user
  if (user) {
    listTriggered.value = false
    pagination.reset()
  }
}

async function changePage(page: number) {
  pagination.setPage(page)

  if (listTriggered.value) {
    await listedUsersQuery.refetch()
  }
}

const adminForm = useForm({
  validationSchema: toTypedSchema(adminCreateSchema),
  initialValues: {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    birthDate: '',
  },
})

const [firstName] = adminForm.defineField('firstName')
const [lastName] = adminForm.defineField('lastName')
const [email] = adminForm.defineField('email')
const [password] = adminForm.defineField('password')
const [confirmPassword] = adminForm.defineField('confirmPassword')
const [birthDate] = adminForm.defineField('birthDate')

const submitCreateAdmin = adminForm.handleSubmit(async (values) => {
  try {
    await createAdmin({
      firstName: values.firstName,
      lastName: values.lastName,
      email: values.email,
      password: values.password,
      birthDate: values.birthDate,
    })

    adminForm.resetForm()
    toastStore.showSuccess('Administratorul a fost creat.')
    if (listTriggered.value) {
      await listedUsersQuery.refetch()
    }
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut crea administratorul.')
  }
})

onMounted(() => {
  void runListQuery()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - utilizatori" subtitle="Filtre complete pentru utilizatori si creare directa de administratori." />

    <Panel id="create-admin" no-hover>
      <h2 class="text-lg font-semibold text-white">Creeaza administrator</h2>
      <p class="mt-1 text-sm text-gray-400">Completeaza formularul de mai jos pentru a crea un nou administrator.</p>

      <form class="mt-4 grid gap-4 md:grid-cols-2" @submit.prevent="submitCreateAdmin">
        <FormInput v-model="firstName" label="Prenume" :error="adminForm.errors.value.firstName" />
        <FormInput v-model="lastName" label="Nume" :error="adminForm.errors.value.lastName" />
        <FormInput v-model="email" label="Email" :error="adminForm.errors.value.email" />
        <FormInput v-model="birthDate" label="Data nasterii" type="date" :error="adminForm.errors.value.birthDate" />
        <FormInput v-model="password" label="Parola" type="password" :error="adminForm.errors.value.password" />
        <FormInput v-model="confirmPassword" label="Confirma parola" type="password" :error="adminForm.errors.value.confirmPassword" />

        <div class="md:col-span-2 flex justify-end">
          <AppButton type="submit" :loading="adminForm.isSubmitting.value">Creeaza administrator</AppButton>
        </div>
      </form>
    </Panel>

    <Panel id="user-search" no-hover>
      <div class="flex items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-white">Cauta dupa email</h2>
          <p class="text-sm text-gray-400">Gaseste rapid un utilizator dupa email, cu sugestii live.</p>
        </div>
      </div>

      <div class="mt-4 space-y-3">
        <AdminUserSearchPicker
          v-model="selectedSearchUserId"
          title="Email"
          placeholder="Cauta utilizatorul dupa email"
          results-title="Sugestii utilizatori"
          @select="handleSearchUserSelect"
        />

        <div class="flex justify-end">
          <AppButton variant="outline" @click="clearEmailSearch">Reseteaza</AppButton>
        </div>
      </div>

      <div v-if="selectedSearchUser" class="mt-4 space-y-3">
        <RouterLink
          :to="{ name: 'admin-user-detail', params: { id: selectedSearchUser.id } }"
          class="block rounded-xl border border-white/10 bg-black/40 p-4 transition-colors hover:border-blue-700"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-white">{{ selectedSearchUser.firstName }} {{ selectedSearchUser.lastName }}</p>
              <p class="mt-1 text-xs text-gray-400">{{ selectedSearchUser.email }} - {{ formatDateShort(selectedSearchUser.createdAt) }}</p>
            </div>
            <StatusBadge :status="selectedSearchUser.status" />
          </div>
        </RouterLink>
      </div>
    </Panel>

    <Panel id="user-filters" no-hover>
      <div class="flex items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-white">Lista si filtre</h2>
          <p class="text-sm text-gray-400">Filtreaza utilizatorii dupa stare sau sorteaza rezultatele.</p>
        </div>
      </div>

      <div class="mt-4 grid gap-3 md:grid-cols-2">

        <label class="text-sm text-gray-300">
          <span class="mb-1 block">Stare</span>
          <select v-model="status" class="app-select-field app-select">
            <option value="ALL">Toate</option>
            <option value="PENDING_VERIFICATION">Verificare in asteptare</option>
            <option :value="'PENDING'">{{ translateEnumLabel('PENDING') }}</option>
            <option :value="'ACTIVE'">{{ translateEnumLabel('ACTIVE') }}</option>
            <option :value="'SUSPENDED'">{{ translateEnumLabel('SUSPENDED') }}</option>
            <option :value="'DECLINED'">{{ translateEnumLabel('DECLINED') }}</option>
            <option :value="'DEACTIVATED'">{{ translateEnumLabel('DEACTIVATED') }}</option>
          </select>
        </label>

        <AdminSortSelect v-model="sortBy" label="Sorteaza dupa" :options="sortOptions" />
      </div>

      <div class="mt-4 flex flex-wrap gap-3">
        <AppButton @click="runListQuery">Incarca lista</AppButton>
        <AppButton variant="outline" @click="clearListFilters">Reseteaza filtrele</AppButton>
      </div>

      <div v-if="listTriggered" class="mt-4 space-y-3">
        <RouterLink
          v-for="user in listedUsersQuery.data.value?.content ?? []"
          :key="user.id"
          :to="{ name: 'admin-user-detail', params: { id: user.id } }"
          class="block rounded-xl border border-white/10 bg-black/40 p-4 transition-colors hover:border-blue-700"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-white">{{ user.firstName }} {{ user.lastName }}</p>
              <p class="mt-1 text-xs text-gray-400">{{ user.email }} - {{ formatDateShort(user.createdAt) }}</p>
            </div>
            <StatusBadge :status="user.status" />
          </div>
        </RouterLink>
      </div>

      <EmptyState
        v-if="listTriggered && !listedUsersQuery.isLoading.value && !(listedUsersQuery.data.value?.content?.length ?? 0)"
        class="mt-4"
        message="Nu exista useri pentru filtrele selectate."
        description="Schimba emailul sau statusul si incearca din nou."
      />

      <AppPagination
        v-if="listTriggered && (listedUsersQuery.data.value?.totalPages ?? 0) > 1"
        :page="listedUsersQuery.data.value?.number ?? 0"
        :total-pages="listedUsersQuery.data.value?.totalPages ?? 0"
        @change="changePage"
      />
    </Panel>
  </div>
</template>


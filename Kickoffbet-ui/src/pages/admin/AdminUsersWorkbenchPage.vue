<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { RouterLink } from 'vue-router'
import { createAdmin, getPendingVerification, getUsers, getUsersByStatus } from '@/api/admin-users.api'
import { adminCreateSchema } from '@/validation/forms'
import { usePagination } from '@/composables/usePagination'
import { PAGE_SIZES } from '@/constants/pagination.constants'
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
import { useTimeSeriesChart } from '@/composables/useTimeSeriesChart'
import { translateEnumLabel } from '@/utils/labels.utils'
import ExportButton from '@/components/ExportButton.vue'
import TimeSeriesChart from '@/components/TimeSeriesChart.vue'
import DatePresetButtons from '@/components/DatePresetButtons.vue'
import type { ExportColumn } from '@/utils/export.utils'
import { getUsersTimeSeries } from '@/api/admin.timeseries.api'

const toastStore = useToastStore()
const pagination = usePagination(PAGE_SIZES.NORMAL)
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

const exportColumns: ExportColumn<UserList>[] = [
  { header: 'ID', accessor: (r) => r.id },
  { header: 'Email', accessor: (r) => r.email },
  { header: 'Prenume', accessor: (r) => r.firstName },
  { header: 'Nume', accessor: (r) => r.lastName },
  { header: 'Status', accessor: (r) => translateEnumLabel(r.status) },
  { header: 'Creat la', accessor: (r) => r.createdAt },
]

async function fetchAllUsersForExport(): Promise<UserList[]> {
  const sortParam = { page: 0, size: 10000, sort: sortBy.value }
  if (status.value === 'PENDING_VERIFICATION') {
    return (await getPendingVerification(sortParam)).content
  }
  if (status.value !== 'ALL') {
    return (await getUsersByStatus(status.value, sortParam)).content
  }
  return (await getUsers(sortParam)).content
}

const trendChartRef = ref<InstanceType<typeof TimeSeriesChart> | null>(null)
const { chartStart, chartEnd, chartData, chartLoading, loadChart } = useTimeSeriesChart(getUsersTimeSeries)
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Administrare - utilizatori" subtitle="Filtre complete pentru utilizatori si creare directa de administratori." />

    <Panel id="user-trend" no-hover>
      <h2 class="text-lg font-semibold text-fg">Utilizatori noi pe zi</h2>

      <div class="mt-4 grid items-end gap-3 md:grid-cols-2">
        <FormInput v-model="chartStart" label="Data inceput" type="datetime-local" />
        <FormInput v-model="chartEnd" label="Data sfarsit" type="datetime-local" />
      </div>

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <DatePresetButtons @select="(s, e) => { chartStart = s; chartEnd = e }" />
        <div class="flex flex-wrap gap-2">
          <AppButton :loading="chartLoading" @click="loadChart">Incarca graficul</AppButton>
          <AppButton variant="outline" :disabled="!chartData.length" @click="trendChartRef?.exportPng('utilizatori-noi-grafic')">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v12m0 0l-4-4m4 4l4-4M4 20h16" />
            </svg>
            <span>Descarca PNG</span>
          </AppButton>
        </div>
      </div>

      <div class="mt-6">
        <TimeSeriesChart
          ref="trendChartRef"
          :data="chartData"
          metric="count"
          label="Utilizatori noi"
          color="#f59e0b"
        />
      </div>
    </Panel>

    <Panel id="create-admin" no-hover>
      <h2 class="text-lg font-semibold text-fg">Creeaza administrator</h2>
      <p class="mt-1 text-sm text-muted">Completeaza formularul de mai jos pentru a crea un nou administrator.</p>

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
          <h2 class="text-lg font-semibold text-fg">Cauta dupa email</h2>
          <p class="text-sm text-muted">Gaseste rapid un utilizator dupa email, cu sugestii live.</p>
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
          class="block rounded-xl border border-line bg-surface p-4 transition-colors hover:border-blue-600"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-fg">{{ selectedSearchUser.firstName }} {{ selectedSearchUser.lastName }}</p>
              <p class="mt-1 text-xs text-muted">{{ selectedSearchUser.email }} - {{ formatDateShort(selectedSearchUser.createdAt) }}</p>
            </div>
            <StatusBadge :status="selectedSearchUser.status" />
          </div>
        </RouterLink>
      </div>
    </Panel>

    <Panel id="user-filters" no-hover>
      <div class="flex items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-semibold text-fg">Lista si filtre</h2>
          <p class="text-sm text-muted">Filtreaza utilizatorii dupa stare sau sorteaza rezultatele.</p>
        </div>
        <ExportButton
          v-if="listTriggered"
          :fetch-all="fetchAllUsersForExport"
          :columns="exportColumns"
          filename="utilizatori"
          title="Utilizatori"
          :disabled="!(listedUsersQuery.data.value?.content?.length ?? 0)"
        />
      </div>

      <div class="mt-4 grid gap-3 md:grid-cols-2">

        <label class="text-sm text-muted">
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

      <Transition v-if="listTriggered" name="page-fade" mode="out-in" appear>
        <div :key="listedUsersQuery.data.value?.number ?? 0" class="mt-4 space-y-3">
          <RouterLink
            v-for="user in listedUsersQuery.data.value?.content ?? []"
            :key="user.id"
            :to="{ name: 'admin-user-detail', params: { id: user.id } }"
            class="block rounded-xl border border-line bg-surface p-4 transition-colors hover:border-blue-600"
          >
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-fg">{{ user.firstName }} {{ user.lastName }}</p>
                <p class="mt-1 text-xs text-muted">{{ user.email }} - {{ formatDateShort(user.createdAt) }}</p>
              </div>
              <StatusBadge :status="user.status" />
            </div>
          </RouterLink>
        </div>
      </Transition>

      <EmptyState
        v-if="listTriggered && !listedUsersQuery.isLoading.value && !(listedUsersQuery.data.value?.content?.length ?? 0)"
        class="mt-4"
        message="Nu exista utilizatori pentru filtrele selectate."
        description="Schimba emailul sau statusul si incearca din nou."
      />

      <AppPagination
        v-if="listTriggered && (listedUsersQuery.data.value?.totalElements ?? 0) > 0"
        :page="listedUsersQuery.data.value?.number ?? 0"
        :total-pages="listedUsersQuery.data.value?.totalPages ?? 0"
        :total-elements="listedUsersQuery.data.value?.totalElements"
        @change="changePage"
      />
    </Panel>
  </div>
</template>


<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import {
  activateUser,
  approveUser,
  getUserById,
  getUserIdCard,
  rejectUser,
  suspendUser,
} from '@/api/admin-users.api'
import { getUserTransactions } from '@/api/transactions.admin.api'
import { getUserTickets } from '@/api/tickets.admin.api'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useToastStore } from '@/stores/toast.store'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import AppButton from '@/components/AppButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import FilePreviewModal from '@/components/FilePreviewModal.vue'
import { downloadRemoteFile } from '@/utils/file.utils'
import { formatDateShort } from '@/utils/date.utils'
import { formatMoney } from '@/utils/money.utils'

const route = useRoute()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const userId = route.params.id as string
const previewOpen = ref(false)
const previewUrl = ref<string | null>(null)
const previewContentType = ref<string | null>(null)
const previewFileName = ref<string | null>(null)
const previewLoading = ref(false)
const previewFile = ref<Awaited<ReturnType<typeof getUserIdCard>> | null>(null)

const userQuery = useQuery({
  queryKey: ['admin-user-detail', userId],
  queryFn: () => getUserById(userId),
})

const user = computed(() => userQuery.data.value)
const isAdminUser = computed(() => user.value?.role === 'ADMIN')
const isSuspendedUser = computed(() => user.value?.status === 'SUSPENDED')
const canApproveOrRejectIdCard = computed(() => {
  if (!user.value) return false
  if (isAdminUser.value || isSuspendedUser.value) return false
  return Boolean(user.value.idCardUrl) && !user.value.idCardVerified
})
const canSuspendUser = computed(() => {
  if (!user.value) return false
  if (isAdminUser.value || isSuspendedUser.value) return false
  return user.value.status === 'ACTIVE' || user.value.status === 'PENDING' || user.value.status === 'DECLINED'
})
const canActivateUser = computed(() => {
  if (!user.value) return false
  return user.value.status === 'SUSPENDED' || user.value.status === 'DEACTIVATED' || user.value.status === 'DECLINED'
})
const canViewIdCard = computed(() => {
  if (!user.value) return false
  if (isSuspendedUser.value) return false
  return Boolean(user.value.idCardUrl)
})

const transactionsQuery = useQuery({
  queryKey: ['admin-user-transactions', userId],
  queryFn: () => getUserTransactions(userId, { page: 0, size: 5 }),
})

const ticketsQuery = useQuery({
  queryKey: ['admin-user-tickets', userId],
  queryFn: () => getUserTickets(userId, { page: 0, size: 5 }),
})

async function runAction(
  action: () => Promise<void>,
  message: string,
  options: {
    title: string
    prompt: string
    confirmLabel: string
    variant?: 'primary' | 'danger'
  },
) {
  const confirmed = await confirm({
    title: options.title,
    message: options.prompt,
    description: 'Actiunea se aplica imediat contului selectat.',
    confirmLabel: options.confirmLabel,
    cancelLabel: 'Renunta',
    variant: options.variant ?? 'primary',
  })

  if (!confirmed) {
    return
  }

  try {
    await action()
    await userQuery.refetch()
    toastStore.showSuccess(message)
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Actiunea a esuat.')
  }
}

function cleanupPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = null
  previewContentType.value = null
  previewFileName.value = null
  previewFile.value = null
}

function closePreview() {
  previewOpen.value = false
  cleanupPreview()
}

async function viewIdCard() {
  try {
    previewLoading.value = true
    cleanupPreview()
    const file = await getUserIdCard(userId)
    previewFile.value = file
    previewContentType.value = file.contentType
    previewFileName.value = file.fileName
    previewUrl.value = URL.createObjectURL(file.blob)
    previewOpen.value = true
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca documentul.')
  } finally {
    previewLoading.value = false
  }
}

function downloadIdCard() {
  if (!previewFile.value) return
  downloadRemoteFile(previewFile.value, 'id-card')
}

onBeforeUnmount(() => {
  cleanupPreview()
})
</script>

<template>
  <div v-if="userQuery.data.value" class="space-y-6">
    <PageHeader title="Administrare - detaliu utilizator" :back-route="{ name: 'admin-users' }" />

    <Panel id="user-overview" no-hover>
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-black text-white">{{ userQuery.data.value.firstName }} {{ userQuery.data.value.lastName }}</h2>
          <p class="mt-1 text-sm text-gray-400">{{ userQuery.data.value.email }}</p>
        </div>
        <StatusBadge :status="userQuery.data.value.status" />
      </div>

      <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div class="rounded-xl border border-white/10 bg-black/40 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Balanta</p>
          <p class="mt-2 text-lg font-semibold text-white">{{ formatMoney(userQuery.data.value.balance) }}</p>
        </div>
        <div class="rounded-xl border border-white/10 bg-black/40 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Email</p>
          <p class="mt-2 text-sm font-semibold text-white">{{ userQuery.data.value.emailVerified ? 'Verificat' : 'Neverificat' }}</p>
        </div>
        <div class="rounded-xl border border-white/10 bg-black/40 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Act de identitate</p>
          <p class="mt-2 text-sm font-semibold text-white">{{ userQuery.data.value.idCardVerified ? 'Verificat' : 'Neverificat' }}</p>
        </div>
        <div class="rounded-xl border border-white/10 bg-black/40 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Creat la</p>
          <p class="mt-2 text-sm font-semibold text-white">{{ formatDateShort(userQuery.data.value.createdAt) }}</p>
        </div>
      </div>

      <div class="mt-4 rounded-xl border border-white/10 bg-black/30 p-4">
        <p class="text-sm font-semibold text-white">Actiuni verificare si cont</p>
        <p class="mt-1 text-xs text-gray-400">
          Aprobarea actului de identitate valideaza documentul incarcat. Daca emailul este deja verificat, utilizatorul poate deveni activ imediat.
        </p>

        <div class="mt-4 flex flex-wrap gap-3">
          <AppButton
            v-if="canApproveOrRejectIdCard"
            variant="outline"
            @click="runAction(
              () => approveUser(userId),
              'Documentul de identitate a fost aprobat.',
              {
                title: 'Aprobare utilizator',
                prompt: 'Sunteti sigur ca vreti sa aprobati actul de identitate al utilizatorului?',
                confirmLabel: 'Aproba',
              },
            )"
          >
            Aproba actul de identitate
          </AppButton>
          <AppButton
            v-if="canApproveOrRejectIdCard"
            variant="outline"
            @click="runAction(
              () => rejectUser(userId),
              'Utilizator respins.',
              {
                title: 'Respingere utilizator',
                prompt: 'Sunteti sigur ca vreti sa respingeti verificarea utilizatorului?',
                confirmLabel: 'Respinge',
                variant: 'danger',
              },
            )"
          >
            Respinge verificarea
          </AppButton>
          <AppButton
            v-if="canSuspendUser"
            variant="outline"
            @click="runAction(
              () => suspendUser(userId),
              'Utilizator suspendat.',
              {
                title: 'Suspendare utilizator',
                prompt: 'Sunteti sigur ca vreti sa suspendati utilizatorul?',
                confirmLabel: 'Suspenda',
                variant: 'danger',
              },
            )"
          >
            Suspenda
          </AppButton>
          <AppButton
            v-if="canActivateUser"
            variant="outline"
            @click="runAction(
              () => activateUser(userId),
              'Utilizator activat.',
              {
                title: 'Activare utilizator',
                prompt: 'Sunteti sigur ca vreti sa reactivati utilizatorul?',
                confirmLabel: 'Activeaza',
              },
            )"
          >
            Activeaza
          </AppButton>
          <AppButton
            v-if="canViewIdCard"
            variant="outline"
            :loading="previewLoading"
            @click="viewIdCard"
          >
            Vezi actul de identitate
          </AppButton>
        </div>
      </div>
    </Panel>

    <div class="grid gap-6 xl:grid-cols-2">
      <Panel id="user-transactions" no-hover>
        <h2 class="text-lg font-semibold text-white">Tranzactii recente</h2>

        <div class="mt-4 space-y-3">
          <div
            v-for="transaction in transactionsQuery.data.value?.content ?? []"
            :key="transaction.id"
            class="rounded-xl border border-white/10 bg-black/40 p-3"
          >
            <div class="flex items-center justify-between gap-3">
              <StatusBadge :status="transaction.transactionType" />
              <span class="text-sm font-semibold text-white">{{ formatMoney(transaction.amount) }}</span>
            </div>
            <p class="mt-2 text-xs text-gray-400">{{ formatDateShort(transaction.createdAt) }}</p>
          </div>
        </div>

        <EmptyState v-if="!(transactionsQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Utilizatorul nu are tranzactii recente." />
      </Panel>

      <Panel id="user-tickets" no-hover>
        <h2 class="text-lg font-semibold text-white">Bilete recente</h2>

        <div class="mt-4 space-y-3">
          <div
            v-for="ticket in ticketsQuery.data.value?.content ?? []"
            :key="ticket.id"
            class="rounded-xl border border-white/10 bg-black/40 p-3"
          >
            <div class="flex items-center justify-between gap-3">
              <StatusBadge :status="ticket.status" />
              <span class="text-sm font-semibold text-white">{{ formatMoney(ticket.potentialPayout) }}</span>
            </div>
            <p class="mt-2 text-xs text-gray-400">{{ formatDateShort(ticket.createdAt) }}</p>
          </div>
        </div>

        <EmptyState v-if="!(ticketsQuery.data.value?.content?.length ?? 0)" class="mt-4" message="Utilizatorul nu are bilete recente." />
      </Panel>
    </div>

    <FilePreviewModal
      :open="previewOpen"
      title="Actul de identitate al utilizatorului"
      :src="previewUrl"
      :content-type="previewContentType"
      :file-name="previewFileName"
      :allow-download="true"
      @close="closePreview"
      @download="downloadIdCard"
    />
  </div>
</template>


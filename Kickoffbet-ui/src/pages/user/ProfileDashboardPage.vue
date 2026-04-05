<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { resendConfirmationEmail } from '@/api/auth.api'
import { deactivateAccount, getIdCard, getProfile, uploadIdCard } from '@/api/profile.api'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'
import SectionHeader from '@/components/SectionHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import AppButton from '@/components/AppButton.vue'
import EmptyState from '@/components/AppEmptyState.vue'
import LoadingState from '@/components/AppLoadingState.vue'
import FilePreviewModal from '@/components/FilePreviewModal.vue'
import FileInputField from '@/components/FileInputField.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth.store'
import { useToastStore } from '@/stores/toast.store'
import { formatMoney } from '@/utils/money.utils'
import { formatDateShort } from '@/utils/date.utils'

const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirmDialog()
const router = useRouter()

const selectedFile = ref<File | null>(null)
const fileInputVersion = ref(0)
const uploading = ref(false)
const resendingVerification = ref(false)
const previewLoading = ref(false)
const previewOpen = ref(false)
const previewUrl = ref<string | null>(null)
const previewContentType = ref<string | null>(null)
const previewFileName = ref<string | null>(null)

const profileQuery = useQuery({
  queryKey: ['profile'],
  queryFn: getProfile,
})

const profile = computed(() => profileQuery.data.value)

const canUploadIdCard = computed(() => {
  if (!profile.value) return false
  return !profile.value.idCardVerified
    && !profile.value.idCardUrl
    && (profile.value.status === 'PENDING' || profile.value.status === 'DECLINED')
})

const canViewIdCard = computed(() => Boolean(profile.value?.idCardUrl))

const idCardStateMessage = computed(() => {
  if (!profile.value) return ''
  if (profile.value.idCardVerified) return 'Documentul tau este verificat si disponibil pentru vizualizare.'
  if (profile.value.idCardUrl) return 'Documentul este incarcat si asteapta validarea.'
  if (profile.value.status === 'DECLINED') return 'Documentul anterior a fost respins. Incarca unul nou pentru reverificare.'
  return 'Nu exista inca un document disponibil pentru contul tau.'
})

const selectedFileName = computed(() => selectedFile.value?.name ?? '')

function handleFileSelect(file: File | null) {
  selectedFile.value = file
}

function cleanupPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = null
  previewContentType.value = null
  previewFileName.value = null
}

function closePreview() {
  previewOpen.value = false
  cleanupPreview()
}

async function handleUpload() {
  if (!selectedFile.value) {
    toastStore.showError('Selecteaza un fisier imagine pentru buletin.')
    return
  }

  try {
    uploading.value = true
    await uploadIdCard(selectedFile.value)
    selectedFile.value = null
    fileInputVersion.value += 1
    await profileQuery.refetch()
    toastStore.showSuccess('Buletinul a fost incarcat cu succes.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca buletinul.')
  } finally {
    uploading.value = false
  }
}

async function handlePreviewIdCard() {
  try {
    previewLoading.value = true
    cleanupPreview()
    const file = await getIdCard()
    previewContentType.value = file.contentType
    previewFileName.value = file.fileName
    previewUrl.value = URL.createObjectURL(file.blob)
    previewOpen.value = true
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut incarca buletinul.')
  } finally {
    previewLoading.value = false
  }
}

async function handleResendVerificationEmail() {
  if (!profile.value?.email) {
    toastStore.showError('Nu am gasit emailul contului.')
    return
  }

  try {
    resendingVerification.value = true
    await resendConfirmationEmail(profile.value.email)
    toastStore.showSuccess('Am retrimis emailul de verificare.')
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut retrimite emailul de verificare.')
  } finally {
    resendingVerification.value = false
  }
}

async function handleDeactivate() {
  const confirmed = await confirm({
    title: 'Dezactivare cont',
    message: 'Sunteti sigur ca vreti sa va dezactivati contul?',
    description: 'Aceasta actiune va inchide sesiunea curenta si va bloca accesul pana la reactivare.',
    confirmLabel: 'Dezactiveaza contul',
    cancelLabel: 'Pastreaza contul',
    variant: 'danger',
  })

  if (!confirmed) {
    return
  }

  try {
    await deactivateAccount()
    authStore.clearAuth()
    await router.push({ name: 'login' })
  } catch (error) {
    toastStore.showError(error instanceof Error ? error.message : 'Nu am putut dezactiva contul.')
  }
}

onBeforeUnmount(() => {
  cleanupPreview()
})
</script>

<template>
  <LoadingState v-if="profileQuery.isLoading.value" message="Se incarca profilul..." />

  <div v-else-if="profile" class="space-y-6">
    <PageHeader title="Profil" subtitle="Date personale, verificare si acces rapid catre portofel" />

    <div class="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,0.95fr)]">
      <Panel no-hover>
        <div class="flex flex-wrap items-start justify-between gap-4 border-b border-white/10 pb-4 sm:pb-5">
          <div class="flex items-center gap-3 sm:gap-4">
            <div class="flex h-14 w-14 items-center justify-center rounded-full bg-white/10 text-xl font-black text-white sm:h-16 sm:w-16 sm:text-2xl">
              {{ profile.firstName[0] }}{{ profile.lastName[0] }}
            </div>
            <div>
              <h2 class="text-xl font-black text-white sm:text-2xl">{{ profile.firstName }} {{ profile.lastName }}</h2>
              <p class="mt-1 text-[13px] text-gray-400 sm:text-sm">{{ profile.email }}</p>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <StatusBadge :status="profile.status" />
                <span class="rounded-full border border-white/10 px-3 py-1 text-xs text-gray-300">
                  Email {{ profile.emailVerified ? 'verificat' : 'neverificat' }}
                </span>
                <span class="rounded-full border border-white/10 px-3 py-1 text-xs text-gray-300">
                  ID {{ profile.idCardVerified ? 'verificat' : 'in asteptare' }}
                </span>
              </div>
            </div>
          </div>

          <AppLinkButton
            :to="{ name: 'wallet' }"
            variant="outline"
          >
            Mergi la portofel
          </AppLinkButton>
        </div>

        <div class="mt-5 grid gap-4 md:grid-cols-2">
          <div class="rounded-xl border border-white/10 bg-black/40 p-3 sm:p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Balanta</p>
            <p class="mt-2 text-2xl font-black text-white sm:text-3xl">{{ formatMoney(profile.balance) }}</p>
          </div>

          <div class="rounded-xl border border-white/10 bg-black/40 p-3 sm:p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Membru din</p>
            <p class="mt-2 text-[13px] font-semibold text-white sm:text-sm">{{ formatDateShort(profile.createdAt) }}</p>
            <p class="mt-3 text-xs uppercase tracking-[0.2em] text-gray-500">Data nasterii</p>
            <p class="mt-2 text-[13px] font-semibold text-white sm:text-sm">{{ profile.birthDate }}</p>
          </div>
        </div>

        <div class="mt-6 rounded-xl border border-red-500/20 bg-red-500/5 p-4">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 class="text-sm font-bold text-white">Dezactiveaza contul</h3>
              <p class="mt-1 text-xs text-gray-400">Aceasta actiune iti blocheaza accesul pana la reactivare.</p>
            </div>
            <AppButton variant="danger" @click="handleDeactivate">Dezactiveaza contul</AppButton>
          </div>
        </div>
      </Panel>

      <div class="space-y-6">
        <Panel v-if="!profile.emailVerified" no-hover>
          <SectionHeader title="Verificare email" />

          <div class="space-y-4">
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Stare email</p>
              <p class="mt-2 text-sm text-gray-300">
                Emailul contului tau nu este verificat inca. Poti retrimite mesajul de confirmare pe {{ profile.email }}.
              </p>
            </div>

            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <AppButton variant="outline" :loading="resendingVerification" @click="handleResendVerificationEmail">
                Retrimite emailul de verificare
              </AppButton>
            </div>
          </div>
        </Panel>

        <Panel no-hover>
          <SectionHeader title="Buletin" />

          <div class="space-y-4">
            <div class="rounded-xl border border-white/10 bg-black/40 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-gray-500">Stare document</p>
              <p class="mt-2 text-sm text-gray-300">{{ idCardStateMessage }}</p>
            </div>

            <div v-if="canUploadIdCard" class="space-y-3 rounded-xl border border-white/10 bg-black/40 p-4">
              <FileInputField
                label="Fisier pentru verificare"
                :file-name="selectedFileName"
                helper="Acceptam imagini pentru verificare."
                :clear-version="fileInputVersion"
                @select="handleFileSelect"
              />

              <AppButton
                v-if="selectedFile"
                variant="primary"
                :loading="uploading"
                @click="handleUpload"
              >
                Incarca buletinul
              </AppButton>
            </div>

            <div v-else-if="canViewIdCard" class="space-y-3 rounded-xl border border-white/10 bg-black/40 p-4">
              <AppButton variant="outline" :loading="previewLoading" @click="handlePreviewIdCard">
                Vezi buletinul meu
              </AppButton>
              <p class="text-xs text-gray-400">Previzualizarea este privata si disponibila doar pentru documentul tau.</p>
            </div>

            <EmptyState v-else message="Nu exista un document care poate fi vizualizat sau reincarcat in acest moment." />
          </div>
        </Panel>
      </div>
    </div>

    <FilePreviewModal
      :open="previewOpen"
      title="Buletinul meu"
      :src="previewUrl"
      :content-type="previewContentType"
      :file-name="previewFileName"
      :allow-download="false"
      @close="closePreview"
    />
  </div>
</template>

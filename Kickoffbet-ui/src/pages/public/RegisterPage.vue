<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { register } from '@/api/auth.api'
import { getProfile } from '@/api/profile.api'
import { useAuthStore } from '@/stores/auth.store'
import { useToastStore } from '@/stores/toast.store'
import { registerSchema } from '@/validation/forms'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import FileInputField from '@/components/FileInputField.vue'

const authStore = useAuthStore()
const toastStore = useToastStore()
const router = useRouter()
const errorMessage = ref('')

const { defineField, handleSubmit, errors, isSubmitting, setFieldValue, values } = useForm({
  validationSchema: toTypedSchema(registerSchema),
  initialValues: {
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    birthDate: '',
    idCard: undefined,
  },
})

const [firstName] = defineField('firstName')
const [lastName] = defineField('lastName')
const [email] = defineField('email')
const [password] = defineField('password')
const [confirmPassword] = defineField('confirmPassword')
const [birthDate] = defineField('birthDate')

const selectedIdCardName = computed(() => {
  const file = values.idCard
  return file instanceof File ? file.name : 'Fisier obligatoriu pentru inregistrare.'
})

function handleFileSelect(file: File | null) {
  setFieldValue('idCard', file ?? undefined)
}

const onSubmit = handleSubmit(async (formValues) => {
  errorMessage.value = ''

  try {
    const response = await register(
      {
        firstName: formValues.firstName,
        lastName: formValues.lastName,
        email: formValues.email,
        password: formValues.password,
        birthDate: formValues.birthDate,
      },
      formValues.idCard,
    )

    authStore.setAuth(response.token, response.refreshToken, response)

    try {
      const profile = await getProfile()
      authStore.hydrateProfile(profile)
    } catch {
      
    }

    toastStore.showSuccess('Cont creat. Verifica emailul pentru confirmare.')
    await router.push({ name: 'profile' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Nu am putut crea contul.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-2xl py-12">
    <Panel class="p-8">
      <div class="mb-6">
        <p class="text-xs uppercase tracking-[0.3em] text-subtle">Creeaza cont</p>
        <h1 class="mt-2 text-3xl font-black text-fg">Inregistrare</h1>
        <p class="mt-2 text-sm text-muted">Buletin obligatoriu pentru inregistrare</p>
      </div>

      <form class="grid gap-4 md:grid-cols-2" @submit.prevent="onSubmit">
        <FormInput v-model="firstName" label="Prenume" required :error="errors.firstName" />
        <FormInput v-model="lastName" label="Nume" required :error="errors.lastName" />
        <FormInput v-model="email" class="md:col-span-2" label="Email" type="email" required :error="errors.email" />
        <FormInput v-model="password" label="Parola" type="password" required :error="errors.password" />
        <FormInput v-model="confirmPassword" label="Confirma parola" type="password" required :error="errors.confirmPassword" />
        <FormInput v-model="birthDate" class="md:col-span-2" label="Data nasterii" type="date" required :error="errors.birthDate" />

        <FileInputField
          class="md:col-span-2"
          label="Buletin"
          required
          :file-name="selectedIdCardName"
          helper="Fisier obligatoriu pentru inregistrare."
          :error="errors.idCard"
          @select="handleFileSelect"
        />

        <div class="md:col-span-2">
          <AlertMessage type="error" :message="errorMessage" />
        </div>

        <div class="md:col-span-2">
          <AppButton class="w-full" type="submit" size="lg" :loading="isSubmitting">Creeaza cont</AppButton>
        </div>
      </form>

      <div class="mt-6 text-sm text-muted">
        <RouterLink :to="{ name: 'login' }" class="hover:text-fg">Ai deja cont? Autentifica-te</RouterLink>
      </div>
    </Panel>
  </div>
</template>


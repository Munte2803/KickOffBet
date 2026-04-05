<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { login } from '@/api/auth.api'
import { getProfile } from '@/api/profile.api'
import { useAuthStore } from '@/stores/auth.store'
import { useToastStore } from '@/stores/toast.store'
import { loginSchema } from '@/validation/forms'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const authStore = useAuthStore()
const toastStore = useToastStore()
const router = useRouter()
const route = useRoute()
const errorMessage = ref('')

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(loginSchema),
  initialValues: {
    email: '',
    password: '',
  },
})

const [email] = defineField('email')
const [password] = defineField('password')

const onSubmit = handleSubmit(async (values) => {
  errorMessage.value = ''

  try {
    const response = await login(values)
    authStore.setAuth(response.token, response.refreshToken, response)

    try {
      const profile = await getProfile()
      authStore.hydrateProfile(profile)
    } catch {
      // Keep the auth session even if profile hydration fails.
    }

    toastStore.showSuccess('Autentificare reusita.')

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
    if (redirect) {
      await router.push(redirect)
      return
    }

    await router.push(response.role === 'ADMIN' ? { name: 'admin-dashboard' } : { name: 'home' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Nu am putut face autentificarea.'
  }
})
</script>

<template>
  <div class="mx-auto mt-8 max-w-md">
    <Panel class="p-8">
      <div class="mb-6">
        <p class="text-xs uppercase tracking-[0.3em] text-gray-500">Bine ai revenit</p>
        <h1 class="mt-2 text-3xl font-black text-white">Autentificare</h1>
        <p class="mt-2 text-sm text-gray-400">Intra in cont pentru a vedea meciurile si biletele tale.</p>
      </div>

      <form class="space-y-4" @submit.prevent="onSubmit">
        <FormInput v-model="email" label="Email" type="email" required :error="errors.email" />
        <FormInput v-model="password" label="Parola" type="password" required :error="errors.password" />

        <AlertMessage type="error" :message="errorMessage" />

        <AppButton class="w-full" type="submit" size="lg" :loading="isSubmitting">Intra in cont</AppButton>
      </form>

      <div class="mt-6 flex flex-wrap items-center justify-between gap-3 text-sm text-gray-400">
        <RouterLink :to="{ name: 'forgot-password' }" class="hover:text-white">Ai uitat parola?</RouterLink>
        <RouterLink :to="{ name: 'register' }" class="hover:text-white">Nu ai cont? Creeaza unul</RouterLink>
      </div>
    </Panel>
  </div>
</template>


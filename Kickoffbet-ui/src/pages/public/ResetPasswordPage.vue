<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { resetPassword } from '@/api/auth.api'
import { resetPasswordSchema } from '@/validation/forms'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'

const route = useRoute()
const statusMessage = ref('')
const statusType = ref<'success' | 'error'>('success')
const resetCompleted = ref(false)

const token = computed(() => typeof route.query.token === 'string' ? route.query.token : '')

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(resetPasswordSchema),
  initialValues: {
    token: token.value,
    newPassword: '',
    confirmPassword: '',
  },
})

const [newPassword] = defineField('newPassword')
const [confirmPassword] = defineField('confirmPassword')

const onSubmit = handleSubmit(async (values) => {
  if (!token.value) {
    statusType.value = 'error'
    statusMessage.value = 'Linkul de resetare este invalid sau incomplet. Cere un email nou de resetare.'
    return
  }

  try {
    await resetPassword({
      token: values.token,
      password: values.newPassword,
      newPassword: values.newPassword,
      confirmPassword: values.confirmPassword,
    })
    resetCompleted.value = true
    statusType.value = 'success'
    statusMessage.value = 'Parola a fost schimbata. Te poti autentifica acum.'
  } catch (error) {
    statusType.value = 'error'
    statusMessage.value = error instanceof Error ? error.message : 'Nu am putut schimba parola.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-12">
    <Panel class="p-8 pt-6">
      <h1 class="text-3xl font-black text-white">Seteaza parola noua</h1>
      <p class="mt-2 text-sm text-gray-400">Pagina de resetare foloseste tokenul din emailul primit. Daca linkul a expirat, cere unul nou din fluxul de recuperare.</p>

      <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
        <FormInput v-model="newPassword" label="Parola noua" type="password" required :error="errors.newPassword" />
        <FormInput v-model="confirmPassword" label="Confirma parola" type="password" required :error="errors.confirmPassword" />
        <AlertMessage :type="statusType" :message="statusMessage" />
        <AppButton class="w-full" type="submit" :loading="isSubmitting" :disabled="resetCompleted">Actualizeaza parola</AppButton>
      </form>

      <div class="mt-6 flex flex-wrap items-center justify-between gap-3 text-sm text-gray-400">
        <RouterLink :to="{ name: 'forgot-password' }" class="hover:text-white">Cere un nou link</RouterLink>
        <AppLinkButton
          v-if="resetCompleted"
          :to="{ name: 'login' }"
          variant="outline"
        >
          Mergi la autentificare
        </AppLinkButton>
      </div>
    </Panel>
  </div>
</template>


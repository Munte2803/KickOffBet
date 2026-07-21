<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { confirmEmail, resendConfirmationEmail } from '@/api/auth.api'
import { emailSchema } from '@/validation/forms'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import AppLinkButton from '@/components/AppLinkButton.vue'

const route = useRoute()
const statusMessage = ref('')
const statusType = ref<'success' | 'error' | 'info'>('info')
const checkingToken = ref(false)
const emailConfirmed = ref(false)
const token = computed(() => typeof route.query.token === 'string' ? route.query.token : '')
const canResend = computed(() => !checkingToken.value && !emailConfirmed.value)

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(emailSchema),
  initialValues: {
    email: '',
  },
})

const [email] = defineField('email')

onMounted(async () => {
  if (!token.value) {
    statusType.value = 'info'
    statusMessage.value = 'Deschide linkul complet din email sau retrimite mesajul de confirmare.'
    return
  }

  checkingToken.value = true
  try {
    await confirmEmail(token.value)
    emailConfirmed.value = true
    statusType.value = 'success'
    statusMessage.value = 'Email confirmat cu succes.'
  } catch (error) {
    statusType.value = 'error'
    statusMessage.value = error instanceof Error ? error.message : 'Nu am putut confirma emailul.'
  } finally {
    checkingToken.value = false
  }
})

const onSubmit = handleSubmit(async (values) => {
  try {
    await resendConfirmationEmail(values.email)
    statusType.value = 'success'
    statusMessage.value = 'Emailul de confirmare a fost retrimis.'
  } catch (error) {
    statusType.value = 'error'
    statusMessage.value = error instanceof Error ? error.message : 'Nu am putut retrimite emailul.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-12">
    <Panel class="p-8 pt-6">
      <h1 class="text-3xl font-black text-fg">Confirma emailul</h1>
      <p class="mt-2 text-sm text-muted">Pagina de confirmare pentru linkul primit pe email. Daca linkul nu mai este valid, poti retrimite mesajul de confirmare.</p>

      <AlertMessage
        class="mt-4"
        :type="statusType"
        :message="checkingToken ? 'Se verifica tokenul...' : statusMessage"
      />

      <form v-if="canResend" class="mt-6 space-y-4" @submit.prevent="onSubmit">
        <FormInput v-model="email" label="Email" type="email" :error="errors.email" />
        <AppButton class="w-full" type="submit" :loading="isSubmitting">Retrimite emailul</AppButton>
      </form>

      <div v-if="emailConfirmed" class="mt-6 flex justify-end">
        <AppLinkButton
          :to="{ name: 'login' }"
          variant="outline"
        >
          Mergi la autentificare
        </AppLinkButton>
      </div>
    </Panel>
  </div>
</template>


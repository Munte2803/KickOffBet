<script setup lang="ts">
import { ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { forgotPassword } from '@/api/auth.api'
import { emailSchema } from '@/validation/forms'
import Panel from '@/components/Panel.vue'
import FormInput from '@/components/FormInput.vue'
import AppButton from '@/components/AppButton.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const statusMessage = ref('')
const statusType = ref<'success' | 'error'>('success')

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(emailSchema),
  initialValues: {
    email: '',
  },
})

const [email] = defineField('email')

const onSubmit = handleSubmit(async (values) => {
  try {
    await forgotPassword(values.email)
    statusType.value = 'success'
    statusMessage.value = 'Emailul de resetare a fost trimis.'
  } catch (error) {
    statusType.value = 'error'
    statusMessage.value = error instanceof Error ? error.message : 'Nu am putut trimite emailul.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-12">
    <Panel class="p-8 pt-6">
      <h1 class="text-3xl font-black text-fg">Reseteaza parola</h1>
      <p class="mt-2 text-sm text-muted">Introdu emailul si iti trimitem linkul de resetare.</p>

      <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
        <FormInput v-model="email" label="Email" type="email" required :error="errors.email" />
        <AlertMessage :type="statusType" :message="statusMessage" />
        <AppButton class="w-full" type="submit" :loading="isSubmitting">Trimite link</AppButton>
      </form>
    </Panel>
  </div>
</template>


import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { makeProfile } from '@/test/factories'
import { mountWithApp } from '@/test/mount'

const api = vi.hoisted(() => ({
  login: vi.fn(),
  resetPassword: vi.fn(),
  getProfile: vi.fn(),
}))

vi.mock('@/api/auth.api', () => ({ login: api.login, resetPassword: api.resetPassword }))
vi.mock('@/api/profile.api', () => ({ getProfile: api.getProfile }))
vi.mock('@/api/axios', () => ({ default: { post: vi.fn() } }))

import LoginPage from '@/pages/public/LoginPage.vue'
import ResetPasswordPage from '@/pages/public/ResetPasswordPage.vue'
import { useAuthStore } from '@/stores/auth.store'
import { useToastStore } from '@/stores/toast.store'

const authResponse = (role: 'USER' | 'ADMIN' = 'USER') => ({
  token: 'access-token',
  refreshToken: 'refresh-token',
  email: 'ana@example.com',
  firstName: 'Ana',
  lastName: 'Pop',
  role,
})

beforeEach(() => {
  api.login.mockReset()
  api.resetPassword.mockReset()
  api.getProfile.mockReset()
})

async function settle() {
  // vee-validate debounces schema validation by a few milliseconds (real timers), then runs the
  // async submit handler; wait past the debounce and drain the promise queue, twice.
  for (let round = 0; round < 2; round += 1) {
    await new Promise((resolve) => setTimeout(resolve, 20))
    await flushPromises()
  }
}

describe('LoginPage', () => {
  async function mountLogin(initialPath = '/login') {
    const result = await mountWithApp(LoginPage, { initialPath })
    const inputs = result.wrapper.findAll('input')
    return {
      ...result,
      email: inputs.find((i) => i.attributes('type') === 'email')!,
      password: inputs.find((i) => i.attributes('type') === 'password')!,
      submit: async () => {
        await result.wrapper.find('form').trigger('submit')
        await settle()
      },
    }
  }

  it('renders the email/password form and links', async () => {
    const { wrapper, email, password } = await mountLogin()
    expect(wrapper.find('h1').text()).toBe('Autentificare')
    expect(email.exists()).toBe(true)
    expect(password.exists()).toBe(true)
    expect(wrapper.findAll('a').map((a) => a.attributes('href'))).toEqual(['/forgot-password', '/register'])
  })

  it('shows validation errors and does not call the API for an empty form', async () => {
    const { wrapper, submit } = await mountLogin()
    await submit()
    expect(api.login).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Emailul este obligatoriu')
    expect(wrapper.text()).toContain('Parola este obligatorie')
  })

  it('rejects a malformed email', async () => {
    const { wrapper, email, password, submit } = await mountLogin()
    await email.setValue('ana')
    await password.setValue('x')
    await submit()
    expect(api.login).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Format de email invalid')
  })

  it('logs in, stores the session, hydrates the profile and goes home', async () => {
    api.login.mockResolvedValue(authResponse('USER'))
    api.getProfile.mockResolvedValue(makeProfile({ id: 'u-1', balance: 321 }))
    const { email, password, submit, router } = await mountLogin()

    await email.setValue('  ana@example.com ')
    await password.setValue('Parola123')
    await submit()

    expect(api.login).toHaveBeenCalledWith({ email: 'ana@example.com', password: 'Parola123' })
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.balance).toBe(321)
    expect(sessionStorage.getItem('accessToken')).toBe('access-token')
    expect(sessionStorage.getItem('refreshToken')).toBe('refresh-token')
    expect(useToastStore().toasts.at(-1)?.message).toBe('Autentificare reusita.')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('sends admins to the admin dashboard', async () => {
    api.login.mockResolvedValue(authResponse('ADMIN'))
    api.getProfile.mockResolvedValue(makeProfile({ role: 'ADMIN' }))
    const { email, password, submit, router } = await mountLogin()
    await email.setValue('boss@example.com')
    await password.setValue('x')
    await submit()
    expect(router.currentRoute.value.name).toBe('admin-dashboard')
  })

  it('returns to the redirect target from the query string', async () => {
    api.login.mockResolvedValue(authResponse('USER'))
    api.getProfile.mockResolvedValue(makeProfile())
    const { email, password, submit, router } = await mountLogin('/login?redirect=%2Fwallet%3Ftab%3Ddeposit')
    await email.setValue('ana@example.com')
    await password.setValue('x')
    await submit()
    expect(router.currentRoute.value.fullPath).toBe('/wallet?tab=deposit')
  })

  it('still logs in when the profile request fails', async () => {
    api.login.mockResolvedValue(authResponse('USER'))
    api.getProfile.mockRejectedValue(new Error('offline'))
    const { email, password, submit, router } = await mountLogin()
    await email.setValue('ana@example.com')
    await password.setValue('x')
    await submit()
    expect(useAuthStore().isAuthenticated).toBe(true)
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('shows the API error and stays logged out on failure', async () => {
    api.login.mockRejectedValue(new Error('Email sau parola incorecta.'))
    const { wrapper, email, password, submit, router } = await mountLogin()
    await email.setValue('ana@example.com')
    await password.setValue('wrong')
    await submit()

    expect(wrapper.find('[role="alert"]').text()).toBe('Email sau parola incorecta.')
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(router.currentRoute.value.name).toBe('login')
  })
})

describe('ResetPasswordPage', () => {
  async function mountReset(initialPath: string) {
    const result = await mountWithApp(ResetPasswordPage, { initialPath })
    const [newPassword, confirmPassword] = result.wrapper.findAll('input[type="password"]')
    return {
      ...result,
      fill: async (password: string, confirmation = password) => {
        await newPassword.setValue(password)
        await confirmPassword.setValue(confirmation)
      },
      submit: async () => {
        await result.wrapper.find('form').trigger('submit')
        await settle()
      },
    }
  }

  it('sends token, password and confirmation using the backend field names', async () => {
    api.resetPassword.mockResolvedValue(undefined)
    const { wrapper, fill, submit } = await mountReset('/reset-password?token=abc123')
    await fill('Parola123')
    await submit()

    expect(api.resetPassword).toHaveBeenCalledWith({ token: 'abc123', password: 'Parola123', confirmPassword: 'Parola123' })
    expect(wrapper.find('[role="alert"]').text()).toBe('Parola a fost schimbata. Te poti autentifica acum.')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('validates password strength and confirmation before calling the API', async () => {
    const { wrapper, fill, submit } = await mountReset('/reset-password?token=abc123')
    await fill('parola123')
    await submit()
    expect(wrapper.text()).toContain('Parola trebuie sa contina litera mare, litera mica si o cifra')

    await fill('Parola123', 'Parola124')
    await submit()
    expect(wrapper.text()).toContain('Parolele nu coincid')
    expect(api.resetPassword).not.toHaveBeenCalled()
  })

  it('does not call the API without a token in the link', async () => {
    const { fill, submit } = await mountReset('/reset-password')
    await fill('Parola123')
    await submit()
    expect(api.resetPassword).not.toHaveBeenCalled()
  })

  it('shows the API error', async () => {
    api.resetPassword.mockRejectedValue(new Error('Token expirat'))
    const { wrapper, fill, submit } = await mountReset('/reset-password?token=abc123')
    await fill('Parola123')
    await submit()
    expect(wrapper.find('[role="alert"]').text()).toBe('Token expirat')
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

// Route components are lazy imports; replace them with a stub so the guard tests do not
// evaluate every page (and its API modules / chart libraries).
const Stub = defineComponent({ render: () => h('div') })
vi.doMock('@/layouts/MainLayout.vue', () => ({ default: Stub }))
vi.doMock('@/layouts/AdminLayout.vue', () => ({ default: Stub }))
for (const page of [
  'public/LoginPage', 'public/RegisterPage', 'public/ConfirmEmailPage', 'public/ForgotPasswordPage',
  'public/ResetPasswordPage', 'public/NotFoundPage', 'user/MatchesPage', 'user/LeaguesPage', 'user/TicketsPage',
  'user/WalletPage', 'user/ProfileDashboardPage', 'admin/AdminMatchesDashboardPage', 'admin/AdminUsersWorkbenchPage',
]) {
  vi.doMock(`@/pages/${page}.vue`, () => ({ default: Stub }))
}

type Role = 'USER' | 'ADMIN'

async function setup(role?: Role) {
  vi.resetModules()
  if (role) {
    sessionStorage.setItem('accessToken', 'access')
    sessionStorage.setItem('refreshToken', 'refresh')
    sessionStorage.setItem('authUser', JSON.stringify({ email: 'x@y.ro', firstName: 'X', lastName: 'Y', role }))
  }
  const pinia = await import('pinia')
  pinia.setActivePinia(pinia.createPinia())
  const { default: router } = await import('@/router')
  return router
}

async function navigate(path: string, role?: Role) {
  const router = await setup(role)
  await router.push(path)
  return router.currentRoute.value
}

beforeEach(() => {
  window.history.replaceState(null, '', '/__start')
})

describe('router guards', () => {
  describe('anonymous visitor', () => {
    it('is sent to login without a redirect from the home page', async () => {
      const route = await navigate('/')
      expect(route.name).toBe('login')
      expect(route.query).toEqual({})
    })

    it('is sent to login with a redirect back to protected pages', async () => {
      const route = await navigate('/tickets?status=WON')
      expect(route.name).toBe('login')
      expect(route.query.redirect).toBe('/tickets?status=WON')
    })

    it('cannot open admin pages', async () => {
      const route = await navigate('/admin/users')
      expect(route.name).toBe('login')
      expect(route.query.redirect).toBe('/admin/users')
    })

    it.each(['/login', '/register', '/forgot-password', '/reset-password?token=abc', '/confirm-email?token=abc'])(
      'can open the public page %s',
      async (path) => {
        const route = await navigate(path)
        expect(route.fullPath).toBe(path)
      },
    )

    it('sees the not-found page for unknown URLs', async () => {
      const route = await navigate('/does/not/exist')
      expect(route.name).toBe('not-found')
    })
  })

  describe('regular user', () => {
    it('can open protected user pages', async () => {
      const route = await navigate('/wallet', 'USER')
      expect(route.name).toBe('wallet')
    })

    it('is redirected home from admin pages', async () => {
      const route = await navigate('/admin/users', 'USER')
      expect(route.name).toBe('home')
    })

    it.each(['/login', '/register'])('is redirected home from guest-only page %s', async (path) => {
      const route = await navigate(path, 'USER')
      expect(route.name).toBe('home')
    })

    it('can still open the password reset page', async () => {
      const route = await navigate('/reset-password?token=abc', 'USER')
      expect(route.name).toBe('reset-password')
    })
  })

  describe('admin', () => {
    it('lands on the admin matches dashboard from /admin', async () => {
      const route = await navigate('/admin', 'ADMIN')
      expect(route.name).toBe('admin-matches')
    })

    it('can open admin pages', async () => {
      const route = await navigate('/admin/users', 'ADMIN')
      expect(route.name).toBe('admin-users')
    })

    it('is redirected to the admin dashboard from guest-only pages', async () => {
      const route = await navigate('/login', 'ADMIN')
      expect(route.name).toBe('admin-matches')
    })

    it('can also open the regular user pages', async () => {
      const route = await navigate('/leagues', 'ADMIN')
      expect(route.name).toBe('leagues')
    })
  })

  it('reacts to logout: protected pages require login again', async () => {
    const router = await setup('USER')
    await router.push('/tickets')
    expect(router.currentRoute.value.name).toBe('tickets')

    const { useAuthStore } = await import('@/stores/auth.store')
    useAuthStore().clearAuth()
    await router.push('/wallet')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/wallet')
  })
})

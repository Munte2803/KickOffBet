import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ─── Public routes ───────────────────────────────────────────────────────
    {
      path: '/',
      component: () => import('@/layouts/PublicLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/features/matches/pages/MatchesPage.vue'),
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/features/auth/pages/LoginPage.vue'),
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/features/auth/pages/RegisterPage.vue'),
        },
        {
          path: 'confirm-email',
          name: 'confirm-email',
          component: () => import('@/features/auth/pages/ConfirmEmailPage.vue'),
        },
        {
          path: 'forgot-password',
          name: 'forgot-password',
          component: () => import('@/features/auth/pages/ForgotPasswordPage.vue'),
        },
        {
          path: 'reset-password',
          name: 'reset-password',
          component: () => import('@/features/auth/pages/ResetPasswordPage.vue'),
        },
      ],
    },

    // ─── Authenticated user routes ────────────────────────────────────────────
    {
      path: '/user',
      component: () => import('@/layouts/UserLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/features/profile/pages/ProfilePage.vue'),
        },
        {
          path: 'wallet',
          name: 'wallet',
          component: () => import('@/features/wallet/pages/WalletPage.vue'),
        },
        {
          path: 'tickets',
          name: 'tickets',
          component: () => import('@/features/tickets/pages/TicketsPage.vue'),
        },
        {
          path: 'tickets/:id',
          name: 'ticket-detail',
          component: () => import('@/features/tickets/pages/TicketDetailPage.vue'),
        },
      ],
    },

    // ─── Admin routes ─────────────────────────────────────────────────────────
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('@/features/matches/admin/pages/AdminMatchesPage.vue'),
        },
        {
          path: 'matches',
          name: 'admin-matches',
          component: () => import('@/features/matches/admin/pages/AdminMatchesPage.vue'),
        },
        {
          path: 'matches/create',
          name: 'admin-matches-create',
          component: () => import('@/features/matches/admin/pages/AdminCreateMatchPage.vue'),
        },
        {
          path: 'matches/:id',
          name: 'admin-match-detail',
          component: () => import('@/features/matches/admin/pages/AdminMatchDetailPage.vue'),
        },
        {
          path: 'leagues',
          name: 'admin-leagues',
          component: () => import('@/features/leagues/admin/pages/AdminLeaguesPage.vue'),
        },
        {
          path: 'teams',
          name: 'admin-teams',
          component: () => import('@/features/teams/admin/pages/AdminTeamsPage.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/features/users/admin/pages/AdminUsersPage.vue'),
        },
        {
          path: 'users/:id',
          name: 'admin-user-detail',
          component: () => import('@/features/users/admin/pages/AdminUserDetailPage.vue'),
        },
        {
          path: 'transactions',
          name: 'admin-transactions',
          component: () => import('@/features/transactions/admin/pages/AdminTransactionsPage.vue'),
        },
        {
          path: 'tickets',
          name: 'admin-tickets',
          component: () => import('@/features/tickets/admin/pages/AdminTicketsPage.vue'),
        },
        {
          path: 'sync',
          name: 'admin-sync',
          component: () => import('@/features/sync/admin/pages/AdminSyncPage.vue'),
        },
      ],
    },

    // ─── Catch-all 404 ───────────────────────────────────────────────────────
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
    },
  ],
})

// ─── Navigation Guards ───────────────────────────────────────────────────────
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }

  // Redirect logged-in users away from login/register
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return auth.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' }
  }
})

export default router

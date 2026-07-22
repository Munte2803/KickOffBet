import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition
    }

    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth',
      }
    }

    return { top: 0 }
  },
  routes: [
   
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        {
          path: 'login',
          name: 'login',
          component: () => import('@/pages/public/LoginPage.vue'),
          meta: { redirectIfAuth: true },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/pages/public/RegisterPage.vue'),
          meta: { redirectIfAuth: true },
        },
        {
          path: 'confirm-email',
          name: 'confirm-email',
          component: () => import('@/pages/public/ConfirmEmailPage.vue'),
        },
        {
          path: 'forgot-password',
          name: 'forgot-password',
          component: () => import('@/pages/public/ForgotPasswordPage.vue'),
        },
        {
          path: 'reset-password',
          name: 'reset-password',
          component: () => import('@/pages/public/ResetPasswordPage.vue'),
        },
        {
          path: '',
          name: 'home',
          component: () => import('@/pages/user/MatchesPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'leagues',
          name: 'leagues',
          component: () => import('@/pages/user/LeaguesPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'leagues/:code',
          name: 'league',
          component: () => import('@/pages/user/LeaguePage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'leagues/:code/results',
          name: 'league-results',
          component: () => import('@/pages/user/LeagueResultsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'results',
          name: 'results',
          component: () => import('@/pages/user/ResultsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'teams',
          name: 'teams',
          component: () => import('@/pages/user/TeamsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'match/:id',
          name: 'match-detail',
          component: () => import('@/pages/user/MatchDetailPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'teams/:id',
          name: 'team',
          component: () => import('@/pages/user/TeamPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'teams/:id/results',
          name: 'team-results',
          component: () => import('@/pages/user/TeamResultsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/pages/user/ProfileDashboardPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'profile/settings',
          name: 'profile-settings',
          component: () => import('@/pages/user/ProfileSettingsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'wallet',
          name: 'wallet',
          component: () => import('@/pages/user/WalletPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'wallet/transactions/:id',
          name: 'transaction-detail',
          component: () => import('@/pages/user/TransactionDetailPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'tickets',
          name: 'tickets',
          component: () => import('@/pages/user/TicketsPage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'tickets/:id',
          name: 'ticket-detail',
          component: () => import('@/pages/user/TicketDetailPage.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },

    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          redirect: { name: 'admin-matches' },
        },
        {
          path: 'matches',
          name: 'admin-matches',
          component: () => import('@/pages/admin/AdminMatchesDashboardPage.vue'),
        },
        {
          path: 'matches/create',
          name: 'admin-matches-create',
          component: () => import('@/pages/admin/AdminCreateMatchComposerPage.vue'),
        },
        {
          path: 'matches/:id',
          name: 'admin-match-detail',
          component: () => import('@/pages/admin/AdminMatchWorkbenchPage.vue'),
        },
        {
          path: 'leagues',
          name: 'admin-leagues',
          component: () => import('@/pages/admin/AdminLeaguesWorkspacePage.vue'),
        },
        {
          path: 'leagues/:code',
          name: 'admin-league-detail',
          component: () => import('@/pages/admin/AdminLeagueDetailPage.vue'),
        },
        {
          path: 'teams',
          name: 'admin-teams',
          component: () => import('@/pages/admin/AdminTeamsWorkspacePage.vue'),
        },
        {
          path: 'teams/:id',
          name: 'admin-team-detail',
          component: () => import('@/pages/admin/AdminTeamDetailPage.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/pages/admin/AdminUsersWorkbenchPage.vue'),
        },
        {
          path: 'users/:id',
          name: 'admin-user-detail',
          component: () => import('@/pages/admin/AdminUserProfilePage.vue'),
        },
        {
          path: 'transactions',
          name: 'admin-transactions',
          component: () => import('@/pages/admin/AdminTransactionsWorkbenchPage.vue'),
        },
        {
          path: 'transactions/:id',
          name: 'admin-transaction-detail',
          component: () => import('@/pages/admin/AdminTransactionDetailPage.vue'),
        },
        {
          path: 'tickets',
          name: 'admin-tickets',
          component: () => import('@/pages/admin/AdminTicketsWorkbenchPage.vue'),
        },
        {
          path: 'tickets/:id',
          name: 'admin-ticket-detail',
          component: () => import('@/pages/admin/AdminTicketDetailPage.vue'),
        },
        {
          path: 'sync',
          name: 'admin-sync',
          component: () => import('@/pages/admin/AdminSyncWorkbenchPage.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/public/NotFoundPage.vue'),
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return to.fullPath === '/'
      ? { name: 'login' }
      : { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }

  if (to.meta.redirectIfAuth && auth.isAuthenticated) {
    return auth.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' }
  }
})

router.onError((error, to) => {
  const message = error instanceof Error ? error.message : String(error)
  if (/Failed to fetch dynamically imported module|Importing a module script failed/i.test(message)) {
    if (to?.fullPath) {
      window.location.assign(to.fullPath)
    } else {
      window.location.reload()
    }
  }
})

export default router


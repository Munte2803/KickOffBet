import { defineComponent, h, type Component } from 'vue'
import { mount, type ComponentMountingOptions } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

const Blank = defineComponent({ name: 'BlankRoute', render: () => h('div') })

const routeNames: Array<[string, string]> = [
  ['/', 'home'],
  ['/login', 'login'],
  ['/register', 'register'],
  ['/forgot-password', 'forgot-password'],
  ['/reset-password', 'reset-password'],
  ['/wallet', 'wallet'],
  ['/wallet/transactions/:id', 'transaction-detail'],
  ['/tickets/:id', 'ticket-detail'],
  ['/match/:id', 'match-detail'],
  ['/teams/:id', 'team'],
  ['/leagues/:code', 'league'],
  ['/admin', 'admin-dashboard'],
  ['/admin/transactions/:id', 'admin-transaction-detail'],
]

/** A memory-history router exposing the named routes components link to. */
export function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: routeNames.map(([path, name]) => ({ path, name, component: Blank })),
  })
}

export interface MountContext {
  pinia: Pinia
  router: Router
}

/** Mounts a component with a fresh active Pinia and a test router (navigated to `initialPath`). */
export async function mountWithApp<C extends Component>(
  component: C,
  options: ComponentMountingOptions<C> & { initialPath?: string; pinia?: Pinia } = {},
) {
  const { initialPath = '/', pinia: providedPinia, ...mountOptions } = options
  const pinia = providedPinia ?? createPinia()
  setActivePinia(pinia)
  const router = createTestRouter()
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(component, {
    ...mountOptions,
    global: {
      ...(mountOptions.global ?? {}),
      plugins: [...(mountOptions.global?.plugins ?? []), pinia, router],
    },
  } as ComponentMountingOptions<C>)

  return { wrapper, pinia, router }
}

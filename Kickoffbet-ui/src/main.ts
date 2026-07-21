import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import router from './router'
import './assets/main.css'
import App from './App.vue'
import { useThemeStore } from './stores/theme.store'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

useThemeStore(pinia).init()

app.mount('#app')

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import router from './router'
import './assets/main.css'
import App from './App.vue'
import { useThemeStore } from './stores/theme.store'
import { useTicketStore } from './stores/ticket.store'
import { useToastStore } from './stores/toast.store'
import { queryClient } from './query/query-client'
import { bindSlipOddsSync } from './query/slip-odds-sync'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

useThemeStore(pinia).init()

bindSlipOddsSync(queryClient, (matches) => {
  if (useTicketStore(pinia).refreshFromMatches(matches) > 0) {
    useToastStore(pinia).showInfo('Unele cote din bilet s-au modificat. Verifica biletul inainte de plasare.')
  }
})

app.mount('#app')

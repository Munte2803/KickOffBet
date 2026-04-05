<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Panel from '@/components/Panel.vue'

const resources = [
  {
    title: 'Meciuri',
    description: 'Cauta, editeaza si gestioneaza toate meciurile din sistem',
    to: { name: 'admin-matches' },
  },
  {
    title: 'Ligi',
    description: 'Gestioneaza ligile, adauga sau sterge echipele participante si controleaza detaliile fiecarei ligi.',
    to: { name: 'admin-leagues' },
  },
  {
    title: 'Echipe',
    description: 'Creezi, editezi si asociezi echipele cu ligile',
    to: { name: 'admin-teams' },
  },
  {
    title: 'Useri',
    description: 'Gasesti utilizatorii dupa email si aprobi documentele utilizatorilor pending',
    to: { name: 'admin-users' },
  },
  {
    title: 'Tranzactii',
    description: 'Lucrezi pe tranzactiile in asteptare, cautari complete si rapoarte ',
    to: { name: 'admin-transactions' },
  },
  {
    title: 'Bilete',
    description: 'Vezi biletele dupa status sau dupa utilizatorul selectat, cu detalii din selectie.',
    to: { name: 'admin-tickets' },
  },
  {
    title: 'Sincronizare',
    description: 'Controlezi sincronizarile datelor despre fotbal',
    to: { name: 'admin-sync' },
  },
]
</script>

<template>
  <div class="space-y-6">
    <PageHeader
      title="Zona de administrare"
      subtitle="Alege resursa pe care vrei sa lucrezi"
    />

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <RouterLink
        v-for="resource in resources"
        :key="resource.title"
        :to="resource.to"
        class="block"
      >
        <Panel class="h-full bg-black/40 transition-transform hover:-translate-y-0.5">
          <p class="text-xs uppercase tracking-[0.24em] text-gray-500">Sectiune</p>
          <h2 class="mt-3 text-xl font-black text-white">{{ resource.title }}</h2>
          <p class="mt-2 text-sm text-gray-400">{{ resource.description }}</p>
        </Panel>
      </RouterLink>
    </div>
  </div>
</template>


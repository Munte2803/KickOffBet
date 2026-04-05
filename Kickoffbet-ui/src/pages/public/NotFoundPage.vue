<template>
  <div class="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
    <p class="text-sm uppercase tracking-[0.3em] text-gray-500">404</p>
    <h1 class="text-3xl font-black text-white">Pagina nu a fost gasita</h1>
    <p class="max-w-md text-sm text-gray-400">
      Ruta ceruta nu exista sau nu mai este disponibila.
    </p>
    <RouterLink
      :to="{ name: 'home' }"
      class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
    >
      Inapoi la meciuri
    </RouterLink>
  </div>
</template>

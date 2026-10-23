import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemePreference = 'system' | 'light' | 'dark'
type ResolvedTheme = 'light' | 'dark'

// Must match the key read by the no-FOUC inline script in index.html.
const STORAGE_KEY = 'kob-theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')

// Storage can be blocked (privacy settings, sandboxed iframes): the theme must never break startup
function readStoredPreference(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function storePreference(pref: ThemePreference) {
  try {
    storePreference(pref)
  } catch {
    // storage unavailable: the choice simply isn't remembered across reloads
  }
}

function applyTheme(theme: ResolvedTheme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

export const useThemeStore = defineStore('theme', () => {
  const stored = readStoredPreference()
  const preference = ref<ThemePreference>(stored === 'light' || stored === 'dark' ? stored : 'system')
  const systemDark = ref(media.matches)

  const resolved = computed<ResolvedTheme>(() =>
    preference.value === 'system' ? (systemDark.value ? 'dark' : 'light') : preference.value,
  )

  function setPreference(pref: ThemePreference) {
    preference.value = pref
    storePreference(pref)
    applyTheme(resolved.value)
  }

  function toggle() {
    setPreference(resolved.value === 'dark' ? 'light' : 'dark')
  }

  // Called once at startup: apply the resolved theme and keep it in sync with
  // the OS setting while the user hasn't picked an explicit preference.
  function init() {
    applyTheme(resolved.value)
    media.addEventListener('change', (e) => {
      systemDark.value = e.matches
      if (preference.value === 'system') {
        applyTheme(resolved.value)
      }
    })
  }

  return { preference, resolved, setPreference, toggle, init }
})

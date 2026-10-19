import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useToastStore } from '@/stores/toast.store'
import { useConfirmStore } from '@/stores/confirm.store'

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.useRealTimers()
})

describe('toast store', () => {
  it('adds typed toasts with unique ids', () => {
    vi.useFakeTimers()
    const store = useToastStore()
    store.showSuccess('ok')
    store.showError('fail')
    store.showInfo('fyi')

    expect(store.toasts.map(({ type, message }) => ({ type, message }))).toEqual([
      { type: 'success', message: 'ok' },
      { type: 'error', message: 'fail' },
      { type: 'info', message: 'fyi' },
    ])
    expect(new Set(store.toasts.map((t) => t.id)).size).toBe(3)
  })

  it('auto-dismisses each toast after 4 seconds', () => {
    vi.useFakeTimers()
    const store = useToastStore()
    store.showSuccess('first')
    vi.advanceTimersByTime(2000)
    store.showError('second')

    vi.advanceTimersByTime(1999)
    expect(store.toasts).toHaveLength(2)
    vi.advanceTimersByTime(1)
    expect(store.toasts.map((t) => t.message)).toEqual(['second'])
    vi.advanceTimersByTime(2000)
    expect(store.toasts).toHaveLength(0)
  })

  it('dismiss removes a toast immediately and the later timeout is harmless', () => {
    vi.useFakeTimers()
    const store = useToastStore()
    store.showInfo('a')
    store.showInfo('b')
    const [first] = store.toasts
    store.dismiss(first.id)
    expect(store.toasts.map((t) => t.message)).toEqual(['b'])
    store.dismiss(9999)
    vi.advanceTimersByTime(4000)
    expect(store.toasts).toHaveLength(0)
  })
})

describe('confirm store', () => {
  it('opens with the given options and default labels/variant', () => {
    const store = useConfirmStore()
    void store.ask({ title: 'Sterge', message: 'Esti sigur?' })
    expect(store.open).toBe(true)
    expect(store.title).toBe('Sterge')
    expect(store.message).toBe('Esti sigur?')
    expect(store.description).toBe('')
    expect(store.confirmLabel).toBe('Confirma')
    expect(store.cancelLabel).toBe('Renunta')
    expect(store.variant).toBe('primary')
  })

  it('uses custom labels, description and variant', () => {
    const store = useConfirmStore()
    void store.ask({
      title: 'T',
      message: 'M',
      description: 'D',
      confirmLabel: 'Da',
      cancelLabel: 'Nu',
      variant: 'danger',
    })
    expect([store.description, store.confirmLabel, store.cancelLabel, store.variant]).toEqual(['D', 'Da', 'Nu', 'danger'])
  })

  it('resolves true on confirm and resets the dialog state', async () => {
    const store = useConfirmStore()
    const answer = store.ask({ title: 'T', message: 'M', variant: 'danger', confirmLabel: 'Da' })
    store.confirm()
    await expect(answer).resolves.toBe(true)
    expect(store.open).toBe(false)
    expect(store.title).toBe('')
    expect(store.variant).toBe('primary')
    expect(store.confirmLabel).toBe('Confirma')
  })

  it('resolves false on cancel', async () => {
    const store = useConfirmStore()
    const answer = store.ask({ title: 'T', message: 'M' })
    store.cancel()
    await expect(answer).resolves.toBe(false)
    expect(store.open).toBe(false)
  })

  it('a new question resolves the pending one as false', async () => {
    const store = useConfirmStore()
    const first = store.ask({ title: 'First', message: 'M' })
    const second = store.ask({ title: 'Second', message: 'M' })
    await expect(first).resolves.toBe(false)
    expect(store.title).toBe('Second')
    store.confirm()
    await expect(second).resolves.toBe(true)
  })

  it('confirm/cancel without a pending question are harmless', () => {
    const store = useConfirmStore()
    expect(() => store.confirm()).not.toThrow()
    expect(() => store.cancel()).not.toThrow()
    expect(store.open).toBe(false)
  })
})

describe('theme store', () => {
  function createMedia(matches: boolean) {
    const listeners: Array<(event: { matches: boolean }) => void> = []
    const media = {
      matches,
      addEventListener: vi.fn((_type: string, listener: (event: { matches: boolean }) => void) => {
        listeners.push(listener)
      }),
      removeEventListener: vi.fn(),
    }
    return {
      media,
      emit(nextMatches: boolean) {
        media.matches = nextMatches
        listeners.forEach((listener) => listener({ matches: nextMatches }))
      },
    }
  }

  async function loadThemeStore(systemDark: boolean) {
    const controller = createMedia(systemDark)
    vi.stubGlobal('matchMedia', vi.fn(() => controller.media))
    vi.resetModules()
    const pinia = await import('pinia')
    pinia.setActivePinia(pinia.createPinia())
    const { useThemeStore } = await import('@/stores/theme.store')
    return { store: useThemeStore(), controller }
  }

  const isDarkApplied = () => document.documentElement.classList.contains('dark')

  beforeEach(() => {
    document.documentElement.classList.remove('dark')
  })

  it('follows the system preference by default', async () => {
    const { store } = await loadThemeStore(true)
    expect(store.preference).toBe('system')
    expect(store.resolved).toBe('dark')
    store.init()
    expect(isDarkApplied()).toBe(true)
  })

  it('restores an explicit preference from localStorage (key kob-theme)', async () => {
    localStorage.setItem('kob-theme', 'light')
    const { store } = await loadThemeStore(true)
    expect(store.preference).toBe('light')
    expect(store.resolved).toBe('light')
  })

  it('ignores invalid stored values', async () => {
    localStorage.setItem('kob-theme', 'purple')
    const { store } = await loadThemeStore(false)
    expect(store.preference).toBe('system')
  })

  it('setPreference persists explicit choices, clears storage for system, and applies the class', async () => {
    const { store } = await loadThemeStore(false)
    store.setPreference('dark')
    expect(localStorage.getItem('kob-theme')).toBe('dark')
    expect(isDarkApplied()).toBe(true)

    store.setPreference('system')
    expect(localStorage.getItem('kob-theme')).toBeNull()
    expect(store.resolved).toBe('light')
    expect(isDarkApplied()).toBe(false)
  })

  it('toggle flips the resolved theme', async () => {
    const { store } = await loadThemeStore(true)
    store.toggle()
    expect(store.preference).toBe('light')
    expect(isDarkApplied()).toBe(false)
    store.toggle()
    expect(store.preference).toBe('dark')
    expect(isDarkApplied()).toBe(true)
  })

  it('reacts to OS theme changes only while following the system', async () => {
    const { store, controller } = await loadThemeStore(false)
    store.init()
    expect(isDarkApplied()).toBe(false)

    controller.emit(true)
    expect(store.resolved).toBe('dark')
    expect(isDarkApplied()).toBe(true)

    store.setPreference('light')
    controller.emit(true)
    expect(isDarkApplied()).toBe(false)
    expect(store.resolved).toBe('light')
  })
})

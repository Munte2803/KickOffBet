import { afterEach, vi } from 'vitest'

// jsdom does not implement matchMedia; the theme store reads it at module load.
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
}

// jsdom only logs "not implemented" for scrolling (used by the router's scrollBehavior).
window.scrollTo = (() => undefined) as typeof window.scrollTo

afterEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

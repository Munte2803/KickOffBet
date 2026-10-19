import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// Pin the timezone so date/time formatting tests are deterministic.
// Europe/Bucharest observes DST, so the date utils are exercised across UTC offsets.
process.env.TZ = 'Europe/Bucharest'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      include: ['src/**/*.spec.ts'],
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
      unstubGlobals: true,
      unstubEnvs: true,
    },
  }),
)

import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const VALID_DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

/** Throws when the shared deployment schedule configuration is missing or invalid. */
function validateSchedule(env: Record<string, string>): void {
  const tz = env.VITE_OPEN_TZ
  const days = env.VITE_OPEN_DAYS
  const from = env.VITE_OPEN_FROM
  const to = env.VITE_OPEN_TO

  const missing = ['VITE_OPEN_TZ', 'VITE_OPEN_DAYS', 'VITE_OPEN_FROM', 'VITE_OPEN_TO'].filter(
    (k) => !env[k],
  )
  if (missing.length > 0) {
    throw new Error(`Missing schedule configuration: ${missing.join(', ')}`)
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz }).format()
  } catch {
    throw new Error(`Invalid IANA time zone in VITE_OPEN_TZ: ${tz}`)
  }
  if (!/^\d{1,2}:\d{2}$/.test(from) || !/^\d{1,2}:\d{2}$/.test(to)) {
    throw new Error('VITE_OPEN_FROM and VITE_OPEN_TO must be HH:MM (24-hour)')
  }
  const dayNames = days.split(',').map((d) => d.trim().toLowerCase()).filter(Boolean)
  if (dayNames.length === 0 || dayNames.some((d) => !VALID_DAYS.includes(d))) {
    throw new Error(`Invalid VITE_OPEN_DAYS: ${days}`)
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  if (mode === 'production') {
    validateSchedule(env)
  }

  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        '/api': {
          target: env.VITE_DEV_BACKEND_URL ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: false,
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
    },
  }
})

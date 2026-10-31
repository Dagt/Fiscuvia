import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vitest/config'
import { readFileSync } from 'node:fs'
import { Agent } from 'node:https'

// Server-only, ignored ephemeral fixture configuration. Never use a VITE_ variable for private material.
const auth = process.env.FISCUVIA_AUTH_HARNESS_FILE
  ? (JSON.parse(
      readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE, 'utf8'),
    ) as {
      pfx: string
      passphrase: string
      certificate: string
      backendOrigin: string
    })
  : null

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    ...(auth
      ? { https: { pfx: readFileSync(auth.pfx), passphrase: auth.passphrase } }
      : {}),
    proxy: {
      '/api': {
        target: auth?.backendOrigin ?? 'http://127.0.0.1:8080',
        changeOrigin: false,
        ...(auth ? { agent: new Agent({ ca: auth.certificate }) } : {}),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    clearMocks: true,
    restoreMocks: true,
  },
})

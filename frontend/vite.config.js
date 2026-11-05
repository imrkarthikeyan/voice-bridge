import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'

// https://vite.js.org/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    basicSsl({
      name: 'voicebridge-lan',
      domains: ['localhost', '127.0.0.1', '192.168.31.9'],
    }),
  ],
  define: {
    global: 'window',
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})

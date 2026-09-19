import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The gateway enforces CORS for the single origin http://localhost:5173.
// Dev proxy keeps the browser on one origin and lets WS/STOMP upgrade through.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true },
    },
  },
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'window'
  },
  server: {
    port: 3000,
    proxy: {
      // Proxy tất cả /api/* đến Spring Boot
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Không rewrite — Spring Boot context path đã là /api
      },
      // WebSocket proxy cho STOMP chat
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      }
    }
  }
})

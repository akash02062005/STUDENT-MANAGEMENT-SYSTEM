import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Frontend dev server: http://localhost:5173
// All /api requests are transparently proxied to the Spring Boot backend
// running on http://localhost:8080. This avoids CORS entirely and means
// you can use relative URLs from the React app.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true,
        // Don't buffer SSE — let events stream through immediately
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req) => {
            if (req.url?.endsWith('/stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform';
            }
          });
        },
      },
    },
  },
})

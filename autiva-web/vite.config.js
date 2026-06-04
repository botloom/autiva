import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:9527',
      '/ws': { target: 'ws://localhost:9527', ws: true }
    }
  },
  build: {
    outDir: '../autiva-backend/src/main/resources/static',
    emptyOutDir: true
  }
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

function workspaceUrls() {
  return {
    name: 'studio-workspace-urls',
    configureServer(server) {
      server.printUrls = () => {
        const base = server.resolvedUrls?.local[0]?.replace(/\/$/, '')
        if (!base) return
        server.config.logger.info([
          '',
          '  见微 · 前端服务已启动',
          '',
          `  客户端    ${base}/customer`,
          `  管理端    ${base}/admin`,
          `  员工端    ${base}/staff`,
          '',
          '  管理端、员工端需使用对应角色账号登录。',
          '  后端请在 IDEA 启动（8080）；按 Ctrl+C 停止前端。',
          '',
        ].join('\n'))
      }
    },
  }
}

export default defineConfig({
  plugins: [vue(), workspaceUrls()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    open: false,
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
  build: { outDir: 'dist' },
})

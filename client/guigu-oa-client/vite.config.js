import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 9530,
    open: !process.env.CI,
    proxy: {
      // 接口文档页（#246，ADR 0061）：Swagger UI 内部按绝对路径取 spec，
      // 所以 /v3 与 /swagger-ui 不能走 /dev-api 的重写。
      '/v3': {
        target: 'http://localhost:8800',
        changeOrigin: true,
      },
      '/swagger-ui': {
        target: 'http://localhost:8800',
        changeOrigin: true,
      },
      '/dev-api': {
        target: 'http://localhost:8800',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/dev-api/, ''),
      },
    },
  },
  build: {
    target: ['chrome85', 'firefox79', 'safari14.1'],
    outDir: 'dist',
    assetsDir: 'static',
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/unit/**/*.spec.{js,ts}'],
    setupFiles: ['tests/unit/setup.js'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/main.ts', 'src/env.d.ts', 'src/types/**'],
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      deps: {
        // element-plus 外置时,其内部 import 的 async-validator 会与测试侧形成两份实例,
        // el-form 的校验链在单测里静默失真(空必填也判 VALID)。inline 进来保证单一实例,
        // 与生产(rollup 单实例)语义一致。
        inline: ['element-plus'],
      },
    },
  },
})

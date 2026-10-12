/// <reference types="vite/client" />
/// <reference types="element-plus/global" />

import type { DefineComponent } from 'vue'

declare module '*.vue' {
  const component: DefineComponent
  export default component
}

declare global {
  interface ViteTypeOptions {
    strictImportMetaEnv: unknown
  }

  interface ImportMetaEnv {
    readonly VITE_APP_BASE_API?: string
  }
}

export {}

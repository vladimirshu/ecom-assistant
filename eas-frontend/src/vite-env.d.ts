/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEV_BACKEND_URL?: string
  readonly VITE_OPEN_TZ?: string
  readonly VITE_OPEN_DAYS?: string
  readonly VITE_OPEN_FROM?: string
  readonly VITE_OPEN_TO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

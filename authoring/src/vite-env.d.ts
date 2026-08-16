/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Client-side Anthropic API key for the authoring assistant (spec §4.2). */
  readonly VITE_ANTHROPIC_API_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

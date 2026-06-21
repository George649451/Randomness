import { defineConfig } from "vitest/config";

// Kept separate from vite.config.ts to avoid the dual-Vite type clash (vitest
// bundles its own Vite copy). The pure cores (diff, validate) are node-env unit
// tests and need no React plugin.
export default defineConfig({
  test: {
    globals: true,
    environment: "node",
  },
});

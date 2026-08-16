import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// The authoring assistant calls Claude. Rather than have the browser hit
// api.anthropic.com directly (CORS + the key riding in client code), the dev
// server proxies /anthropic → api.anthropic.com and injects the API key
// server-side from .env.local. The browser only ever talks to its own origin.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const key = env.VITE_ANTHROPIC_API_KEY ?? env.ANTHROPIC_API_KEY ?? "";
  return {
    plugins: [react()],
    server: {
      proxy: {
        "/anthropic": {
          target: "https://api.anthropic.com",
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/anthropic/, ""),
          configure: (proxy) => {
            proxy.on("proxyReq", (proxyReq) => {
              if (key) proxyReq.setHeader("x-api-key", key);
              // server-to-server now; the browser-direct flag is unnecessary
              proxyReq.removeHeader("anthropic-dangerous-direct-browser-access");
            });
          },
        },
      },
    },
  };
});

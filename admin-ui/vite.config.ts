import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server proxies /api to the gateway so the SPA and API share an origin.
// Override the target per environment with the `VITE_GATEWAY_URL` loaded env (see loadEnv) if needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});

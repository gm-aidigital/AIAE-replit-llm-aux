import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// Vite config. Three things to know about Replit + this template:
//   1. Dev server binds 0.0.0.0:5173 so Replit can expose it.
//   2. /api/* is proxied to the backend on http://localhost:5000 in dev,
//      so the frontend never builds raw backend URLs.
//   3. Production builds go to ../backend/application/src/main/resources/static
//      (configurable below) so the Spring Boot JAR serves the SPA from the
//      same process — this is what allows Reserved VM deployment.

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), "");
    const backendPort = env.BACKEND_DEV_PORT ?? "5000";
    const backendContextPath = env.VITE_API_CONTEXT_PATH ?? "";   // e.g. "/sales-dashboard"

    return {
        plugins: [react()],
        server: {
            host: "0.0.0.0",
            port: 5173,
            strictPort: true,
            proxy: {
                "/api": {
                    target: `http://localhost:${backendPort}${backendContextPath}`,
                    changeOrigin: true,
                    secure: false,
                },
            },
        },
        build: {
            outDir: "../backend/application/src/main/resources/static",
            emptyOutDir: true,
        },
        preview: {
            host: "0.0.0.0",
            port: 5173,
        },
    };
});

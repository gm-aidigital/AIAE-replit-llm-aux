import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";
import { runtimeConfig } from "../config/runtime";

// Auth-aware typed client. The Authorization header is added by middleware
// using whatever token the auth provider supplies (Clerk getToken() in SSO
// mode, the mock JWT in mock mode).

const baseUrl = runtimeConfig.apiBaseUrl;   // e.g. "/api/v1" in dev (proxied by Vite)

export const apiClient = createClient<paths>({ baseUrl });

apiClient.use({
    async onRequest({ request }) {
        const token = await runtimeConfig.getAuthToken();
        if (token) request.headers.set("Authorization", `Bearer ${token}`);
        return request;
    },
    async onResponse({ response }) {
        if (response.status === 401) {
            await runtimeConfig.onUnauthorized();
        }
        return response;
    },
});

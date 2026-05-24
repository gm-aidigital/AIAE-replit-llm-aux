// runtime.ts — reads VITE_* env vars at boot, validates them, and exposes a
// single typed runtimeConfig object the rest of the app uses. NEVER read
// import.meta.env directly outside this file.
//
// Only VITE_* vars are exposed to the browser by Vite; anything else is
// either a build-time secret (forbidden) or a server-side env (forbidden in
// the SPA).

type AuthMode = "auto" | "sso" | "mock";

interface RuntimeConfig {
    apiBaseUrl: string;
    authMode: AuthMode;
    clerkPublishableKey: string;          // empty when mock or auto-without-keys
    validate(): void;
    getAuthToken(): Promise<string | null>;
    onUnauthorized(): Promise<void>;
}

const env = import.meta.env;

function readAuthMode(): AuthMode {
    const v = (env.VITE_AUTH_MODE ?? "auto").toString();
    if (v === "auto" || v === "sso" || v === "mock") return v;
    throw new Error(`VITE_AUTH_MODE must be auto|sso|mock, got "${v}"`);
}

const cfg = {
    apiBaseUrl: (env.VITE_API_BASE_URL ?? "/api/v1").toString(),
    authMode: readAuthMode(),
    clerkPublishableKey: (env.VITE_CLERK_PUBLISHABLE_KEY ?? "").toString(),
};

let mockToken: string | null = null;

export const runtimeConfig: RuntimeConfig = {
    ...cfg,

    validate() {
        if (cfg.authMode === "sso" && !cfg.clerkPublishableKey) {
            throw new Error(
                "VITE_AUTH_MODE=sso requires VITE_CLERK_PUBLISHABLE_KEY"
            );
        }
    },

    async getAuthToken() {
        // SSO mode: wire to Clerk's getToken() in the calling code (this stub
        // returns null so unauthenticated requests still happen).
        // Mock mode: return the mock JWT obtained via POST /auth/mock/login.
        return mockToken;
    },

    async onUnauthorized() {
        mockToken = null;
        // The router can read this and redirect to login.
    },
};

// Setters the auth pages call after a successful login.
export function setMockToken(jwt: string | null) {
    mockToken = jwt;
}

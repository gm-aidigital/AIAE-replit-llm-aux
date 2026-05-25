// runtime.ts — reads VITE_* env vars at boot, validates them, and exposes a
// single typed runtimeConfig object the rest of the app uses. NEVER read
// import.meta.env directly outside this file.
//
// Only VITE_* vars are exposed to the browser by Vite; anything else is
// either a build-time secret (forbidden) or a server-side env (forbidden in
// the SPA).

type AuthMode = "auto" | "sso" | "mock";

const MOCK_TOKEN_STORAGE_KEY = "replit-mvp.mockToken";

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
    // EMPTY default — OpenAPI paths in the spec carry the full `/api/v1/`
    // prefix (see openapi-rules → "/api/v1 prefix"). The generated schema
    // types already know each path's full key, so apiClient calls like
    // `apiClient.GET("/api/v1/auth/me")` resolve to the absolute URL.
    // VITE_API_BASE_URL is only for a different host or servlet context
    // prefix. It must NEVER be `/api/v1`.
    apiBaseUrl: resolveApiBaseUrl(
        (env.VITE_API_BASE_URL ?? "").toString(),
        (env.VITE_API_CONTEXT_PATH ?? "").toString()
    ),
    authMode: readAuthMode(),
    clerkPublishableKey: (env.VITE_CLERK_PUBLISHABLE_KEY ?? "").toString(),
};

let mockToken: string | null = readStoredMockToken();
let ssoTokenGetter: (() => Promise<string | null>) | null = null;

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
        // SSO branch — Clerk's useAuth().getToken() registered via
        // setSsoTokenGetter() inside AuthProvider's ClerkTokenBridge.
        if (ssoTokenGetter) {
            return await ssoTokenGetter();
        }
        // Mock branch — JWT stashed by the Login page after POST /auth/mock/login.
        return mockToken;
    },

    async onUnauthorized() {
        setMockToken(null);
        // The router can read this and redirect to login.
    },
};

/** Set/clear the mock JWT from the Login page. */
export function setMockToken(jwt: string | null) {
    mockToken = jwt;
    if (typeof window === "undefined") return;
    if (jwt) {
        window.sessionStorage.setItem(MOCK_TOKEN_STORAGE_KEY, jwt);
    } else {
        window.sessionStorage.removeItem(MOCK_TOKEN_STORAGE_KEY);
    }
}

/** Register Clerk's getToken function from AuthProvider's bridge. */
export function setSsoTokenGetter(g: (() => Promise<string | null>) | null) {
    ssoTokenGetter = g;
}

/** True when frontend should use Clerk instead of the mock-login form. */
export function usesClerkAuth(): boolean {
    return cfg.authMode === "sso" ||
        (cfg.authMode === "auto" && cfg.clerkPublishableKey !== "");
}

/**
 * Resolves the same-origin API base without duplicating OpenAPI path prefixes.
 *
 * @param explicitBaseUrl optional host/context prefix, never `/api/v1`.
 * @param contextPath optional Spring servlet context path.
 * @return normalized base URL used by openapi-fetch.
 */
export function resolveApiBaseUrl(
    explicitBaseUrl?: string,
    contextPath?: string
): string {
    const explicit = stripTrailingSlash(explicitBaseUrl?.trim() ?? "");
    if (explicit) return rejectApiVersionPrefix(explicit);

    const context = stripTrailingSlash(contextPath?.trim() ?? "");
    if (context) return rejectApiVersionPrefix(context);

    return "";
}

function rejectApiVersionPrefix(value: string): string {
    if (value === "/api/v1" || value.endsWith("/api/v1")) {
        throw new Error(
            "VITE_API_BASE_URL must not include /api/v1 because OpenAPI paths already include it"
        );
    }
    return value;
}

function stripTrailingSlash(value: string): string {
    return value.endsWith("/") ? value.slice(0, -1) : value;
}

function readStoredMockToken(): string | null {
    if (typeof window === "undefined") return null;
    return window.sessionStorage.getItem(MOCK_TOKEN_STORAGE_KEY);
}

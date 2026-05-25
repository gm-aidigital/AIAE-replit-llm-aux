import { ReactNode, useEffect } from "react";
import { ClerkProvider, useAuth } from "@clerk/clerk-react";
import { runtimeConfig, setSsoTokenGetter, usesClerkAuth } from "../config/runtime";

// AuthProvider — wraps the app in the right auth shell per AUTH_MODE.
//
// SSO mode  : ClerkProvider + token-getter bridge for openapi-fetch.
// Mock mode : no wrapper; the Login page stores the JWT via setMockToken().
//
// The token-getter bridge solves a hook-only problem: ClerkProvider's
// useAuth().getToken() can ONLY be called from a React component, but
// apiClient.onRequest runs from a plain function. AuthProvider mounts a
// tiny `ClerkTokenBridge` component that registers Clerk's getToken with
// runtime.ts via setSsoTokenGetter; runtime.ts then exposes it as
// getAuthToken() to the apiClient.

interface Props {
    children: ReactNode;
}

export function AuthProvider({ children }: Props) {
    if (!usesClerkAuth()) {
        return <>{children}</>;
    }

    return (
        <ClerkProvider publishableKey={runtimeConfig.clerkPublishableKey}>
            <ClerkTokenBridge />
            {children}
        </ClerkProvider>
    );
}

function ClerkTokenBridge() {
    const { getToken } = useAuth();
    useEffect(() => {
        setSsoTokenGetter(() => getToken());
        return () => setSsoTokenGetter(null);
    }, [getToken]);
    return null;
}

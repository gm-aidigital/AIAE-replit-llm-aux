import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { runtimeConfig } from "./shared/config/runtime";

// Base layer first (tokens + reset), then any global block styles imported
// by individual components themselves.
import "./shared/ui/base/tokens.css";
import "./shared/ui/base/reset.css";

// Validate runtime config at boot. If required Clerk keys are missing when
// AUTH_MODE=sso, fail fast with a readable error in the browser console.
runtimeConfig.validate();

const queryClient = new QueryClient({
    defaultOptions: {
        queries: { staleTime: 30_000, refetchOnWindowFocus: false },
    },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
        <QueryClientProvider client={queryClient}>
            <App />
        </QueryClientProvider>
    </React.StrictMode>,
);

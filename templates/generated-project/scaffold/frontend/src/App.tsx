import { useQuery } from "@tanstack/react-query";
import { apiClient } from "./shared/api/client";
import { cn } from "./shared/lib/cn";
import "./App.css";

// Minimal demo: calls GET /api/v1/auth/me and renders user/loading/error/empty
// states. Classes follow BEM (see bem-naming-rules.md):
//   .app                  — block
//   .app__title           — element
//   .app__status          — element
//   .app__status--error   — modifier

export default function App() {
    const { data, isLoading, isError, error } = useQuery({
        queryKey: ["auth", "me"],
        queryFn: async () => {
            const { data, error } = await apiClient.GET("/auth/me");
            if (error) throw error;
            return data;
        },
        retry: false,
    });

    return (
        <main className="app">
            <h1 className="app__title">Replit MVP scaffold</h1>

            {isLoading && <p className="app__status">Loading…</p>}

            {isError && (
                <p className={cn("app__status", "app__status--error")} role="alert">
                    Not signed in ({String(error)})
                </p>
            )}

            {data && (
                <p className="app__status app__status--ok">
                    Hello, <strong className="app__email">{data.email}</strong>.
                </p>
            )}

            <p className="app__hint">
                Replace this scaffold with your first feature block.
            </p>
        </main>
    );
}

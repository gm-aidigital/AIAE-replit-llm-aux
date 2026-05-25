import { FormEvent, useState } from "react";
import { SignInButton } from "@clerk/clerk-react";
import { useNavigate } from "react-router-dom";
import { apiClient } from "../shared/api/client";
import { setMockToken, usesClerkAuth } from "../shared/config/runtime";

// Login — minimal mock-mode form. SSO mode renders Clerk's hosted sign-in
// instead (wired in main.tsx via ClerkProvider — this page won't be reached
// when usesClerk=true because Clerk redirects to its own page).

export default function Login() {
    const navigate = useNavigate();
    const [email, setEmail] = useState(
        // pre-fill with the demo user so the form is one-click usable
        "demo-user@example.com"
    );
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    async function onSubmit(e: FormEvent) {
        e.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            const { data, error } = await apiClient.POST("/api/v1/auth/mock/login", {
                body: { email },
            });
            if (error || !data) {
                setError("Login failed — see network log");
                return;
            }
            setMockToken(data.accessToken);
            navigate("/", { replace: true });
        } finally {
            setSubmitting(false);
        }
    }

    if (usesClerkAuth()) {
        return (
            <main className="login">
                <h1 className="login__title">Sign in</h1>
                <p className="login__hint">SSO mode active — continue with Clerk.</p>
                <SignInButton mode="modal">
                    <button type="button" className="login__submit">
                        Sign in with SSO
                    </button>
                </SignInButton>
            </main>
        );
    }

    return (
        <main className="login">
            <h1 className="login__title">Sign in (mock)</h1>
            <form className="login__form" onSubmit={onSubmit}>
                <label className="login__label">
                    Email
                    <input
                        type="email"
                        className="login__input"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
                </label>
                <button
                    type="submit"
                    className="login__submit"
                    disabled={submitting}
                >
                    {submitting ? "Signing in…" : "Sign in"}
                </button>
                {error && <p className="login__error" role="alert">{error}</p>}
            </form>
        </main>
    );
}

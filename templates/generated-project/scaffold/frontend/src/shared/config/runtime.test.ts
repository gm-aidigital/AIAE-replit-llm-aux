import { beforeEach, describe, expect, it } from "vitest";
import { resolveApiBaseUrl, runtimeConfig, setMockToken, usesClerkAuth } from "./runtime";

beforeEach(() => {
    setMockToken(null);
});

describe("resolveApiBaseUrl", () => {
    it("should keep API calls same-origin in Vite dev mode test", () => {
        expect(resolveApiBaseUrl()).toBe("");
    });

    it("should use the Spring servlet context path when configured test", () => {
        expect(resolveApiBaseUrl("", "/employee-directory/")).toBe("/employee-directory");
    });

    it("should reject duplicated OpenAPI version prefixes test", () => {
        expect(() => resolveApiBaseUrl("/api/v1")).toThrow(/must not include/);
    });
});

describe("mock token runtime storage", () => {
    it("should persist mock token across route changes and reloads test", async () => {
        setMockToken("jwt-123");

        expect(await runtimeConfig.getAuthToken()).toBe("jwt-123");
        expect(window.sessionStorage.getItem("replit-mvp.mockToken")).toBe("jwt-123");
    });

    it("should clear mock token on unauthorized test", async () => {
        setMockToken("jwt-123");

        await runtimeConfig.onUnauthorized();

        expect(await runtimeConfig.getAuthToken()).toBeNull();
        expect(window.sessionStorage.getItem("replit-mvp.mockToken")).toBeNull();
    });
});

describe("auth mode selection", () => {
    it("should stay in mock-compatible mode when no Clerk publishable key is configured test", () => {
        expect(usesClerkAuth()).toBe(false);
    });
});

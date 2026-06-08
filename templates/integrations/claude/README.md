# Claude API adapter (opt-in)

Integrates the Anthropic Claude API as an optional external service.

## Prerequisites

- HTTP client foundation (installed automatically)
- A valid Anthropic API key

## Install

```bash
PROJECT_ROOT=/path/to/project bash templates/integrations/claude/install.sh
```

## Configuration

Set in `.env.local` (never commit secrets):

```env
CLAUDE_ENABLED=true
CLAUDE_API_KEY=sk-ant-...
CLAUDE_MODEL=claude-3-5-sonnet-20241022
CLAUDE_MAX_TOKENS=1024
```

For local development without a real key:

```env
CLAUDE_STUB_ENABLED=true
```

## Usage

Inject `ClaudeClient` into any Spring-managed service:

```java
@Service
public class MyService {
    private final ClaudeClient claudeClient;

    public MyService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public String summarize(String text) {
        return claudeClient.complete("Summarize the following text concisely.", text);
    }
}
```

## What is installed

| File | Description |
|------|-------------|
| `ClaudeClient` | Application-facing interface |
| `ClaudeClientImpl` | Production implementation (POST `/v1/messages`) |
| `ClaudeStubClient` | Deterministic stub for local/test use |
| `ClaudeProperties` | Typed `@ConfigurationProperties` |
| `ClaudeConfig` | Selects production or stub based on properties |
| `ClaudeExternalException` | Runtime exception for HTTP/timeout/parse errors |
| `model/*` | Internal request/response records |

## Security

- The API key is never logged.
- Request/response bodies are never logged by default.
- Add `CLAUDE_API_KEY` to `.gitignore` patterns and secret scanning rules.

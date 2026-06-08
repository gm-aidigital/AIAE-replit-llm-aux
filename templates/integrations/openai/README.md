# OpenAI adapter (opt-in)

Integrates the OpenAI Chat Completions API as an optional external service.

## Prerequisites

- HTTP client foundation (installed automatically)
- A valid OpenAI API key

## Install

```bash
PROJECT_ROOT=/path/to/project bash templates/integrations/openai/install.sh
```

## Configuration

Set in `.env.local` (never commit secrets):

```env
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o
OPENAI_MAX_TOKENS=1024
```

For local development without a real key:

```env
OPENAI_STUB_ENABLED=true
```

## Usage

```java
@Service
public class MyService {
    private final OpenAiClient openAiClient;

    public MyService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public String summarize(String text) {
        return openAiClient.complete("Summarize the following text.", text);
    }
}
```

## What is installed

| File | Description |
|------|-------------|
| `OpenAiClient` | Application-facing interface |
| `OpenAiClientImpl` | Production implementation (POST `/v1/chat/completions`) |
| `OpenAiStubClient` | Deterministic stub for local/test use |
| `OpenAiProperties` | Typed `@ConfigurationProperties` |
| `OpenAiConfig` | Selects production or stub based on properties |
| `OpenAiExternalException` | Runtime exception for HTTP/timeout/parse errors |
| `model/*` | Internal request/response records |

## Security

- The API key is sent only in the `Authorization: Bearer` header.
- The key is never logged.
- Add `OPENAI_API_KEY` to `.gitignore` patterns and secret scanning rules.

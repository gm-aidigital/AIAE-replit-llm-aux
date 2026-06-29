# Google Workspace adapter (opt-in)

Integrates Google Docs, Drive, Sheets, and Slides as individually selectable
external services. Enable only the APIs your project needs.

## Install

```bash
PROJECT_ROOT=/path/to/project bash templates/integrations/google-workspace/install.sh
```

## Configuration

Set the service-account JSON as an environment variable string:

```env
GOOGLE_WORKSPACE_CREDENTIALS_JSON={"type":"service_account","project_id":"..."}
GOOGLE_WORKSPACE_DOCS_ENABLED=true
GOOGLE_WORKSPACE_DRIVE_ENABLED=false
GOOGLE_WORKSPACE_SHEETS_ENABLED=false
GOOGLE_WORKSPACE_SLIDES_ENABLED=false
```

For local development without real credentials:

```env
GOOGLE_WORKSPACE_STUB_ENABLED=true
```

## Security

- Never commit service-account JSON.
- Store `GOOGLE_WORKSPACE_CREDENTIALS_JSON` in a secret manager / environment variable; do not mount a file.
- Credentials are never logged.
- Add `gsa.json` and similar patterns to `.gitignore` and secret scanning rules.

## What is installed

| Interface | Description |
|-----------|-------------|
| `GoogleDocsClient` | Read document content |
| `GoogleDriveClient` | List files in a folder |
| `GoogleSheetsClient` | Read a spreadsheet range |
| `GoogleSlidesClient` | Get a presentation title |

Each service has a corresponding stub implementation for local development.

## Adding production implementations

The config provides placeholder `buildXxxClient` methods. Replace each
`UnsupportedOperationException` with a real Google API Client Library
initialization using `properties.getCredentialsJson()`.

Add the required Google API dependencies to the `external-services/pom.xml`:

```xml
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-docs</artifactId>
    <version>v1-rev20260427-2.0.0</version>
</dependency>
```

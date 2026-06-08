# Google Workspace adapter (opt-in)

Integrates Google Docs, Drive, Sheets, and Slides as individually selectable
external services. Enable only the APIs your project needs.

## Install

```bash
PROJECT_ROOT=/path/to/project bash templates/integrations/google-workspace/install.sh
```

## Configuration

Mount a service-account JSON file and set its path:

```env
GOOGLE_WORKSPACE_CREDENTIALS_LOCATION=/run/secrets/gsa.json
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

- Never commit service-account JSON files.
- Mount credentials via Docker secrets or a volume — never bake them into the image.
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
initialization using `properties.getCredentialsLocation()`.

Add the required Google API dependencies to the `external-services/pom.xml`:

```xml
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-docs</artifactId>
    <version>v1-rev20260427-2.0.0</version>
</dependency>
```

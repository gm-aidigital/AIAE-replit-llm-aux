# OpenAPI Review Checklist

Use this checklist for every API change.

## Contract Completeness

- `openapi` version and `info.version` are present
- static spec file path follows project standard
- path and method are correct
- unique `operationId`
- tags are meaningful

## Request and Response

- request body schema defined when required
- path/query/header parameters documented with constraints
- response schema defined for all declared status codes
- content type is explicit (`application/json` unless otherwise required)

## Error Contract

- explicit 4xx/5xx responses for expected failures
- consistent error schema across operations
- error examples included

## Security

- security requirement documented per operation
- public endpoints explicitly documented as public
- bearer JWT security scheme exists in `components.securitySchemes`
- protected operations include explicit `401` and `403` responses
- `/api/v1/auth/me` contract exists for authenticated user context
- mock login endpoint is documented when mock auth mode is supported

## Generator

- project uses canonical OpenAPI generator Maven plugin snippet
- generator is `spring`
- generated interfaces are tag-grouped
- default interface bodies are disabled
- nullable helper generation is disabled unless explicitly approved

## Examples

- success example exists
- validation error example exists
- business error example exists when relevant

## Compatibility

- no accidental breaking path/method changes
- deprecated fields/operations marked explicitly
- breaking changes have migration/versioning note

---
name: openapi-contract-first
description: Contract-first OpenAPI workflow for Java backend features, including validation, compatibility checks, and generation handoff.
argument-hint: "[feature-or-endpoint]"
user-invocable: true
---

# OpenAPI Contract First

Use this skill whenever backend API behavior is added, changed, deprecated, or removed.

Canonical checklist file:
- `templates/generated-project/openapi/openapi-review-checklist.md`

Canonical rules file:
- `templates/generated-project/openapi/canonical-openapi-rules.md`

Canonical generator snippet:
- `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`

Canonical frontend API generation rules:
- `templates/generated-project/frontend/canonical-react-frontend-rules.md`

## Goal

Ensure OpenAPI contract is the source of truth and implementation follows it, not the opposite.

## Required Workflow

1. Identify affected operations/resources.
2. Update OpenAPI contract first.
3. Validate operation semantics and compatibility.
4. Regenerate/reuse DTOs/interfaces through the canonical OpenAPI generator setup.
5. Implement backend changes.
6. Add/update tests for contract compliance.

## Operation Checklist (Each Changed Endpoint)

- unique `operationId`
- clear summary/description
- correct HTTP method and path
- request schema + validation constraints
- response schemas for each status code
- error schema for failure cases
- security requirements
- examples for success and error responses

Static spec/layout rules:
- spec location: `src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml` relative to the backend application module
- top-level `servers`, `tags`, `components.securitySchemes`, `components.schemas` are mandatory
- use reusable shared error schemas

## Auth Contract Addendum (When Endpoint Is Protected)

- bearer JWT security scheme exists in `components.securitySchemes`
- protected operations explicitly reference security requirements
- `401` and `403` responses are documented with payload examples
- token expectations are documented (issuer/audience semantics at API level)
- auth bootstrap endpoint(s) are described (`/api/v1/auth/me`, plus mock login endpoint when mock mode is supported)

## Compatibility Rules

- No breaking path/method changes without explicit versioning/migration note.
- Keep response contracts backward-compatible unless breaking change is approved.
- Deprecations must be documented in the contract.
- Do not remove fields silently.

## Implementation Binding Rules

- Generated or contract-bound API interfaces should be implemented directly.
- Do not handcraft duplicate DTOs that mirror generated contract types.
- Mapping between API models and domain models must be explicit and testable.
- Use canonical Maven generator options:
  - `interfaceOnly=true`
  - `useSpringBoot4=true`
  - `openApiNullable=false`
  - `skipDefaultInterface=true`
  - `useTags=true`
  - `dateLibrary=java8-localdatetime`

## Validation and Tests

Add/adjust tests for:
- required request fields and validation errors
- status codes and response payload shape
- security/access constraints
- edge cases explicitly represented in OpenAPI examples

## Delivery

A backend feature is not done until:
- OpenAPI is updated
- implementation matches OpenAPI
- backend interfaces are regenerated from OpenAPI
- frontend API types are regenerated from the same OpenAPI YAML when frontend exists
- tests cover changed contract behavior

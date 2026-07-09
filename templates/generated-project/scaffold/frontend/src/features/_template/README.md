# Feature module template

Copy this folder to `features/<your-feature>/` when adding product UI.

```
features/<feature-name>/
  index.ts              # public exports
  ui/<FeaturePage>.tsx  # presentational components
  api/                  # TanStack Query hooks (optional subfolder)
  <feature>.test.tsx    # behavior test
```

**Working example in this folder:** `TemplateProfilePanel` + `useAuthMeQuery` +
`template.test.tsx` — copy and rename, do not import `_template` from production code.

Rules:
- Pages in `src/pages/` compose features; they stay thin.
- All API calls go through `shared/api/client.ts`.
- Use `LoadingBlock`, `ErrorAlert`, `EmptyState` for async states.
- BEM class names — see `templates/generated-project/frontend/bem-naming-rules.md`.
- CSS uses semantic tokens and `rem`, not raw `px`; flexbox for rows/stacks,
  grid for table-like/multi-column layouts.
- Forms must validate required OpenAPI fields and expose accessible errors
  before submit.
- Long user/API strings and wrapped button labels must not overflow mobile or
  desktop layouts.

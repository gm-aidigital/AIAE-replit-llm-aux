# Elevate Design Guidelines

Single source of truth for generated React product UI. This is adapted from
the Elevate brand/visual system document into this template's plain CSS + BEM
frontend stack.

## Personality

Clean, utilitarian, and data-dense by design. Generated apps are internal tools,
not marketing pages. The UI should feel quiet, precise, and ready for repeated
workflows.

## Layout Rules

- No left side menu, sidebar, side rail, or permanent left navigation.
- Use a top app header, page-level tabs, segmented controls, filters, and local
  toolbars instead.
- Keep content centered in a max-width content shell: `1265px` max width with
  `24px` horizontal page padding.
- Prefer dense tables, search bars, grouped rows, filters, and compact page
  headers over oversized hero sections.
- One primary CTA per view. Secondary actions use outline, ghost, menu, or icon
  buttons.

## Tokens

All colors live in `frontend/src/shared/ui/base/tokens.css` as CSS custom
properties. Components consume semantic tokens. Do not hard-code hex or HSL in
feature/component CSS files.

| Token | HSL | Use |
|---|---:|---|
| `--primary` | `239 100% 43%` | primary CTA, links, focus ring |
| `--background` | `0 0% 100%` | page background |
| `--foreground` | `0 0% 4%` | body text |
| `--secondary` | `240 78% 98%` | cards, search, table surfaces |
| `--accent` | `237 76% 94%` | table headers, chips, hover rows |
| `--muted-foreground` | `0 0% 54%` | secondary copy |
| `--label-muted` | `226 25% 68%` | section labels |
| `--header-text` | `230 17% 22%` | heavy headings |
| `--border` | `237 76% 94%` | dividers, strokes, gridlines |

Status colors are reserved only for state communication:

| State | HSL | Use |
|---|---:|---|
| `--status-success` | `134 100% 30%` | live/success |
| `--status-info` | `234 100% 43%` | paused/informational |
| `--status-warning` | `52 100% 37%` | moderation/warning |
| `--status-draft` | `0 0% 28%` | archived/draft |
| `--status-error` | `0 72% 45%` | rejected/destructive |

Never use status colors as decoration.

## Typography

- Primary typeface: Inter, weights 400, 500, 600, 700.
- Poppins 500 is reserved for rare display moments only.
- Product surfaces use compact type:
  - page title: `32px / 700`
  - section H2: `18px / 600`
  - subhead: `16px / 500`
  - body: `13px / 400`
  - table head: `14px / 500`
  - pill: `12px / 400`
  - badge: `11px / 600`, uppercase
- Body copy in product surfaces must stay between `12px` and `16px`.

## Shape and Spacing

- Base radius: `12px`.
- Controls: `10px`; compact elements: `8px`; pills: `999px`.
- Common spacing increments: `6`, `8`, `12`, `16`, `24`, `40`.
- Table/list rows use `16px` gaps and `24px` horizontal padding when space
  allows.

## Components

- Buttons: primary button is `40px` tall, `10px` radius, `14px / 500`.
- Rounded-full pill buttons are exceptions for floating card/chart actions,
  onboarding, empty states, and modal confirmations. Never use two pill buttons
  in one view.
- Inputs: `40px` tall, `10px` radius, semantic border/background tokens.
- Status pills: rounded-full, state token only, concise labels.
- Tables: lavender header via accent token, secondary surface for grouped rows,
  expanded child rows use accent for contrast.

## Charts

Charts live inside cards. Use a secondary header strip, white plot area, `24px`
internal padding, muted tick labels, and dotted border-token gridlines.

Categorical series use this order:

1. primary `239 100% 43%`
2. cyan `190 65% 54%`
3. lime `76 75% 60%`
4. purple `263 47% 58%`
5. pink `319 75% 77%`
6. grey `0 0% 72%`
7. sky `219 74% 52%`
8. magenta `346 69% 52%`

Assign the largest segment to primary.

## Do / Do Not

Do:
- use semantic tokens,
- keep one primary CTA per view,
- use secondary/accent surfaces for tables, filters, and row states,
- keep data dense but readable,
- use BEM classes and plain CSS.

Do not:
- create a left side menu/sidebar,
- hard-code colors in components,
- introduce new accent colors without extending tokens,
- use status colors for non-status UI,
- mix radius scales such as `4px` and `20px`,
- place multiple primary buttons in one row.

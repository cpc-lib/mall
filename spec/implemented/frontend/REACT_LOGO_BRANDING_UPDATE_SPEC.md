# React Logo Branding Update Spec

## 0. Metadata

- Status: implemented
- Domain: frontend
- Updated: 2026-06-12
- Owner: TBD
- Related work: replace React header logo text with `苏三的开发日记`
- Issue classification: Design change
- Impact scope: React frontend header logo image and accessible logo text

## 1. Background

The React frontend displayed the previous `logo.png` brand text in the shared app header. This change replaces the visible logo name with `苏三的开发日记` while keeping the existing logo asset path and header integration stable.

## 2. Contract

- `payment-demo-react/src/assets/img/logo.png` remains the logo image consumed by the React header.
- The logo image displays the exact text `苏三的开发日记`.
- The image keeps the existing transparent PNG format and fits the current header logo container.
- The React header link title and image alt text match the displayed logo name.

## 3. Acceptance Criteria

- [x] `logo.png` displays `苏三的开发日记`.
- [x] The existing `logo.png` path remains unchanged.
- [x] `AppHeader.jsx` logo title and alt text use `苏三的开发日记`.
- [x] React frontend verification passes or any failure is documented.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| React logo image | `payment-demo-react/src/assets/img/logo.png` |
| React header logo metadata | `payment-demo-react/src/components/AppHeader.jsx` |
| Spec ledger | `spec/README.md` |

## 5. Compatibility Impact

This intentionally changes the React frontend's visible and accessible logo name from the previous brand text to `苏三的开发日记`.

No backend HTTP route, request shape, response structure, response text, database schema, provider callback, event name, config key, or frontend API call changed.

## 6. Verification

```powershell
npm run build
```

Result on 2026-06-12: passed. Vite reported an existing large chunk warning after minification.

Runtime check on 2026-06-12: local React dev server at `http://localhost:3002/` returned HTTP 200, and the served `AppHeader.jsx` module contained `苏三的开发日记` when decoded as UTF-8.

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-06-12 | implemented | Replaced React logo brand text and matching header metadata. | User request |

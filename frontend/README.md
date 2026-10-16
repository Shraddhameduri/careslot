# CareSlot Frontend

React 18 + TypeScript + Vite web client for CareSlot, a multi-tenant
appointment-booking SaaS.

## Develop

```bash
npm install
npm run dev
```

The Vite dev server proxies `/api` to the backend at `http://localhost:8080`
(see `vite.config.ts`). Make sure the backend is running there.

## Production

Build a static bundle and serve it from any static web server (e.g. nginx):

```bash
npm run build        # type-checks (tsc -b) and emits dist/
npm run preview      # locally serve the production build
```

`dist/` contains plain static files — copy them to the web root served by
nginx or equivalent. Point the nginx upstream at the backend so `/api`
requests reach it (the app uses same-origin `/api/v1` paths).

## Structure

- `src/api.ts` — axios client (tenant + auth interceptors), API calls, types helpers
- `src/auth.tsx` — AuthContext (login/register/signup-tenant/logout, token storage)
- `src/pages/` — TenantPicker, SignupTenant, Login, Book, ProviderDashboard, AdminDashboard
- `src/components/ui.tsx` — shared bits (header, banners, formatters)
- `src/styles.css` — all styling (plain CSS, no framework)

## Tenant + auth

The current tenant (`{id, slug, name}`) is stored in `localStorage` after
picking or creating a clinic. Every request sends `X-Tenant-Id`; public
requests can alternatively use `?tenant=<slug>`. Auth tokens live in
`localStorage`; a 401 response clears them and redirects to `/login`.

## Demo tenant

Slug `sunrise-dental` (seed data, for demos only):

- Admin: `admin@careslot.demo` / `Password123!`
- Provider: `provider@careslot.demo` / `Password123!`
- Customer: `customer@careslot.demo` / `Password123!`

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

RiverDB is a River Science Data Management System — a full-stack web application for managing hydrological and water quality monitoring data (stations, site visits, field/lab samples, results). It runs two parallel server applications:

1. **Main SPA** (port 8989): Fulcro/ClojureScript frontend + Pedestal/GraphQL/Datomic backend
2. **HTML app** (port 9595): Server-side rendered app using SSE + Hiccup + Datastar hypermedia framework (active development on `datastar` branch)

## Architecture

### deps.edn Aliases
Key aliases to combine: `:dev` (adds `src/dev`), `:server` (adds `src/server` + all server deps), `:rad` (adds `src/rad` + Fulcro RAD), `:nrepl` (adds an nREPL for dev connection).

### Source Layout
```
src/main/     — Shared client code (ClojureScript + .cljc)
src/server/   — Backend-only Clojure code
src/dev/      — REPL utilities (user.clj entry point)
src/rad/      — Fulcro RAD forms/UI
src/test/     — Tests
resources/
  specs.edn   — Domain entity specs (source of truth for schema generation)
  public/     — Static assets
```

### Backend Stack
- **Pedestal** — HTTP server with interceptor chain
- **Lacinia** — GraphQL (schema auto-generated from `resources/specs.edn` via `thosmos/domain-spec`)
- **Pathom 2** — Data resolution layer between GraphQL and Datomic
- **Datomic Pro** — Primary database (immutable, time-travel queries)
- **Mount** — Component lifecycle (`riverdb.state` defines DB states)

Key server namespaces:
- `riverdb.server` — Pedestal routes, auth interceptors, service config (port 8989)
- `riverdb.state` — Mount states for Datomic connection
- `riverdb.graphql.schema` — GraphQL schema generated from specs
- `riverdb.api.resolvers` — Datomic read resolvers (Pathom)
- `riverdb.api.mutations` — Database writes
- `riverdb.api.import` — Bulk CSV import with sample/result reconciliation (~1400 lines)
- `riverdb.html.server` — Datastar SSR app (port 9595)
- `riverdb.html.handlers` — Hiccup + Datastar handlers

### Frontend Stack
- **Fulcro 3** — Full-stack React framework with normalized client state
- **Fulcro RAD** — Auto-generated CRUD forms from attribute specs
- **Semantic UI** — CSS/React component library
- **Shadow-cljs** — ClojureScript compiler with hot reload

Key client namespaces:
- `riverdb.client` — Entry point; initializes Fulcro app and RAD
- `riverdb.application` — Fulcro app config, network middleware, EQL transforms
- `riverdb.ui.root` — Root component; auth forms; main layout with routing
- `riverdb.ui.routes` — Route definitions
- `riverdb.ui.session` — Login/logout session mutations
- `riverdb.rad.ui.*` — RAD-generated forms (Person, User, Station, Device)
- `riverdb.ui.upload` — Bulk data import UI

### Data Model
All entity types are defined in `resources/specs.edn` using `thosmos/domain-spec`. This single file drives GraphQL schema generation, Datomic schema migration, and RAD form generation. Key entities: Project, Station, SiteVisit, Sample, FieldResult, LabResult, User, Account.

### Datastar Branch
The `datastar` branch adds a second server-side rendered web app (port 9595) using:
- Hiccup for HTML templating
- Datastar (`dev.data-star.clojure/sdk`) for hypermedia-driven interactivity (SSE-based)
- `org.tcrawley/datastar-pedestal-adapter` for Pedestal SSE integration
- Basecoat for UI components

New namespaces: `riverdb.html.server`, `riverdb.html.handlers`, `riverdb.html.layout`

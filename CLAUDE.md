# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

RiverDB is a River Science Data Management System — a full-stack web application for managing hydrological and water quality monitoring data (stations, site visits, field/lab samples, results). It runs two parallel server applications:

1. **Main SPA** (port 8989): Fulcro/ClojureScript frontend + Pedestal/GraphQL/Datomic backend
2. **HTML app** (port 9595): Server-side rendered app using http-kit + reitit + Datastar SSE

The HTML app is intended to eventually replace both the Fulcro admin SPA and the
separate Vue frontend. It shares this process with the Pedestal server only for
the Datomic connection and mount lifecycle — it has no Pedestal or Lacinia
dependency, so when the GraphQL/SPA stack is retired it becomes the whole app.

## Architecture

### deps.edn Aliases
Key aliases to combine: `:dev` (adds `src/dev`), `:server` (adds `src/server` + all server deps), `:rad` (adds `src/rad` + Fulcro RAD).

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

### HTML app (port 9595)
A second, server-rendered web app, independent of Pedestal:
- **http-kit** — HTTP server; its channel model suits long-lived SSE connections
- **reitit** — routing (`reitit-ring` + `reitit-malli`)
- **malli** — schema validation and decoding at the HTTP edge
- **Datastar** — hypermedia interactivity over SSE, via the first-party
  `dev.data-star.clojure/sdk` + `dev.data-star.clojure/http-kit` adapter
  (versions must match)
- **Pico CSS** (MIT) for styling — classless, no build step, no JS
- **Hiccup** for templating

Namespaces (`src/server/riverdb/html/`):
- `server.clj` — http-kit + mount defstate. Pinned against tools.namespace
  reload (reloading it would orphan the running server), so edits here need
  an explicit `(restart)`.
- `routes.clj` — reitit route data and the assembled ring handler
- `handlers.clj` — plain ring handlers
- `schema.clj` — malli schemas; wire values are decoded to real longs, Dates
  and nils here so nothing below parses strings
- `layout.clj` — Hiccup helpers and Datastar-bound form controls
- `resources/public/css/app.css` — the small amount Pico doesn't cover
  (clickable table rows, form action buttons). Keep it short.
- `sitevisit.clj` — Datomic reads/writes for the SiteVisit form

**Ports.** The HTML app defaults to 9595; set `HTML_PORT` to run a second
instance (test harness, side-by-side comparison) without fighting the REPL
already holding 9595.

**Hot reload.** From a `:dev:server:rad` REPL: `(watch)` reloads `riverdb.html.*`
on save, `(unwatch)` stops it, `(reload)` is a one-shot. In dev the server
resolves the route handler per request, so new routes go live without a
restart. See `src/dev/watch.clj`.

**Datastar gotcha.** A malformed `data-*` expression aborts Datastar's
initialization for every element *after* it in document order — a bad
expression is not a local failure. Check the browser console for
`GenerateExpression` errors.

**Licensed assets.** Datastar Pro and Stellar CSS must NEVER be committed to
this repo. Their license states that "making the software available in a public
repo is a form of redistribution, and is strictly prohibited" and that "adding
the software to an open-source project is a violation of the license" — and
`thosmos/riverdb` is public and AGPL-3.0. Every front-end asset slot in
`riverdb.html.layout` is env-overridable (`DATASTAR_JS`, `UI_CSS`, `UI_JS`,
`UTILITY_CSS`; `""` omits the tag). A licensee drops their files in
`resources/public/vendor/` (gitignored) and points the env vars at them; the
committed defaults stay on freely-redistributable CDN builds so the repo runs
for everyone.

**Datastar versions.** The Clojure SDK and the JS bundle version independently
— SDK `1.0.0-RC11` pairs with bundle `v1.0.2`. Never hardcode the bundle URL;
`layout/datastar-js` defaults to the SDK's own `d*/CDN-url` so a deps bump
moves the client too. Note `data-on:click` (colon) is correct; `data-on-click`
(hyphen) does nothing.

**Monitors type-ahead.** `riverdb.html.handlers` exposes
`GET /sitevisit/:id/monitors` (filter), `POST .../monitors` (add the
server's top match — what Enter does, so the client never tracks ids),
`POST .../monitors/:person` and `DELETE .../monitors/:person`. The selection
lives entirely in the `Visitors` signal that Datastar sends with every
request, so the handlers are stateless and nothing reaches Datomic until Save.
Only `#monitor-chips` and `#monitor-menu` are ever patched, which keeps focus
in the input while typing.

**Where form state lives (decided, not accidental).** The whole form's state
lives in Datastar signals in the browser; the server is stateless and every
handler recomputes from the signals sent with the request. Nothing reaches
Datomic until Save, which transacts one minimal diff.

Server-owned drafts were weighed and declined: they would make the chip-sync
class of bug unrepresentable, but cost session lifecycle (expiry, multi-tab)
and lose drafts on restart. Revisit if forms grow long enough that resumable
data entry matters — and if so, drafts belong in an **atom**, never in
Datomic. Measured: `d/pull` 0.01ms and an atom swap 0.0001ms, versus a
transaction that is ~1000x more expensive *and permanent* — per-keystroke
drafts would bloat history forever and serialise through the single
transactor, destroying the audit log that is the reason to use Datomic.

**Signals keep their Datomic namespace.** Datastar's expression syntax accepts
dots as a nesting separator but not slashes, so `:sitevisit/StationID` travels
as the nested signal `sitevisit.StationID`, and a namespace's own dots nest
further — `:org.riverdb.db.sitevisit/gid` becomes
`org.riverdb.db.sitevisit.gid`. Reversing is unambiguous by rule: the **last**
segment is the name, everything before it is the namespace. No attribute name
in specs.edn contains a dot. (Encoding `/` as `_` was the alternative, but
`:stationlookup/GIS_latlon` already has one.) Helpers live in
`riverdb.html.schema`: `signal-path`, `path->key`, `signal-name`,
`signal-get`, `signal-has?`, `signals-for`. Use `signal-has?` for save diffs —
it distinguishes "not sent" from "sent as nil", which is what makes partial
saves work.

Signals that are not entity attributes live under a `ui` namespace, so the
entity namespaces stay a faithful mirror of Datomic.

Row and replicate keys inside a signal are **keywords** server-side: JSON
encodes them as strings going out and `raw-signals` keywordizes them coming
back, so keywords are what both ends see. Using strings silently misses every
lookup.

**Field measurements** (`riverdb.html.fieldmeasure`). Rows are the project's
active parameters whose `:parameter/SampleType` is `FieldMeasure`, ordered by
`:parameter/Order`; each row shows `:parameter/ReplicatesEntry` replicate
inputs. Derived columns (#, Range, Mean, StdDev, Prec) are recomputed
server-side on every keystroke and patched into cells that carry their own
ids, so the inputs are never replaced and keep focus. Exceedance rules and the
statistics are ported from `riverdb.ui.edit.fieldmeasure` so the numbers match
what the Fulcro form has always shown; the one deliberate difference is that a
single reading renders blank StdDev/Prec instead of `NaN`.

Device and ID are a dependent pair: `:sample/DeviceType` (a
`samplingdevicelookup`, e.g. "ECTestr 11") and `:sample/DeviceID` (a
`samplingdevice` with a `CommonID`, e.g. "22"). Changing the Device posts to
`/fieldmeasure/:param/device`, which re-offers that type's instruments and
clears the selection if it no longer belongs. A row with no sample yet
inherits `:parameter/DeviceType` as its default, which is what that attribute
is for.

Saving folds the grid into the same single transaction as the rest of the
form: one `sample` per parameter with readings, one `fieldresult` per non-blank
replicate. Both are Datomic components, so clearing a reading emits
`:db/retractEntity` rather than leaving an orphan with a nil `Result`.

Per-row attributes honour **not sent vs sent blank**, via
`fieldmeasure/row-present?` — the grid's version of `schema/signal-has?`.
Without it a payload that merely omitted a column retracted it. Datastar always
sends every signal so the browser never triggers this, but any partial or
programmatic payload would, and it fails silently by deleting data.

**Cardinality-many ref fields** (`riverdb.html.ref-list`). Chips + type-ahead,
driven by config. One route set serves every field, dispatching on `:field`:

    GET    /sitevisit/:id/ref/:field           filter
    POST   /sitevisit/:id/ref/:field           add the server's top match (Enter)
    POST   /sitevisit/:id/ref/:field/:member   add that one
    DELETE /sitevisit/:id/ref/:field/:member   remove

Adding another many-ref is an entry in `handlers/ref-fields` — no new handler,
no new route. Element ids and signals are namespaced per field
(`#chips-<field>`, `#menu-<field>`, `<Signal>Query`), so several coexist on a
page. `make-handlers` takes `:base`, `:scope` and `:db` as functions of the
request, so the same widget serves `/sitevisit/:id` now and a generic
`/entity/:ns/:id` later.

What is abstracted is the *mechanism*: the endpoint set, the
patch-on-every-change discipline, and the Datastar gotchas below — that is
where every bug in the first implementation came from. What stays per-field is
the *semantics*: where candidates come from, ranking, labelling, scoping. Each
field's `:search` owns its own ranking (people rank prefix matches above
substring, so typing "thom" surfaces Thomas Spellman first).

Remember to declare each field's `<Signal>Query` in whatever malli schema
validates that page's save payload.

**Dirty tracking.** Save and Revert start disabled and enable on the first
edit, driven by the local-only `_dirty` signal. The leading underscore keeps
Datastar from sending it — it is browser state, and the closed save schema
would reject it. It is set by a single `data-on:input`/`data-on:change` pair on
the form container (events bubble, so one handler covers every control), with
the type-ahead's own search box excluded since searching is not editing. Chip
add/remove arrives as a round trip rather than an input event, so `ref-list`
takes a `:touch` map of signals to merge on every change — that is how the
page marks itself dirty without `ref-list` knowing what dirty means.

It is deliberately *not* a diff against pristine values: typing a value and
typing it back leaves the form dirty. The button is an affordance; the server
still diffs on save, so a no-op save reports "No changes to save".

Note `data-attr:disabled` works and `data-attr-disabled` does not — the same
colon-versus-hyphen rule as `data-on`.

**Signal-derived fragments must be re-patched by hand.** A `data-bind` input
updates itself when its signal is patched; a server-rendered fragment does
not. The chips are the only such fragment today, so every handler that patches
`:Visitors` — add, remove, revert, save — must also call `patch-chips!`.
Forgetting it in revert meant a removed chip never came back and an added one
never went away, even though the signal was correct.

Three further traps, all of which silently produce an *empty* selection and so
wipe the chips rather than erroring:

1. **DELETE carries signals in the query string, not the body.** Datastar's
   rule is `ot = e => !["GET","DELETE"].includes(e)`, and the SDK's own
   `get-signals` only special-cases GET. `raw-signals` uses the set
   `#{:get :delete}`. Test DELETE endpoints with the payload in the *query*,
   the way the browser sends it — a curl `-d` body will pass while the real
   client fails.
2. **`raw-signals` slurps the body**, so `monitor-state` must be called once
   per request and threaded. A second call reads a consumed stream.
3. **`MonitorQuery` must stay declared** in `SiteVisitSignals`: Datastar sends
   every signal on every request and the save schema is `:closed true`.

**Styling.** Pico is classless: it styles bare semantic elements, so markup
stays semantic and almost class-free. Its whole vocabulary is 15 classes plus a
few ARIA hooks — `container`/`container-fluid`, `grid`, `secondary`, `outline`,
`contrast`, `striped`, `overflow-auto`, plus `role="group"`, `role="switch"`,
`aria-current`, `aria-invalid`, `data-theme`. Prefer `<label>` wrapping its
input (Pico's idiom, no id bookkeeping) and `<article>` for panels.

Three traps found in practice. Inside a `<button>`, Pico rebinds
`--pico-color` to the button's own (near-white) foreground, so
`color: var(--pico-color)` on a button renders invisible — inherit from the
container instead. `role="group"` stretches buttons to fill their
container, which is right for a segmented control and wrong for form actions;
and `.container` caps at 950px, too narrow for the site visit table, so the
layout uses `.container-fluid`.

There is deliberately **no Tailwind**. The previous setup used Tailwind's Play
CDN, which their docs describe as "designed for development purposes only, and
is not intended for production" — it compiled CSS in the browser at runtime.
Pico plus semantic markup removes the need entirely, so there is no build step.

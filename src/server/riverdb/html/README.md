# RiverDB HTML Service

A purely server-side HTML web application built with Clojure, running on a separate port (9595) from the main GraphQL service.

## Stack

- **Clojure/Pedestal**: HTTP server and routing
- **Hiccup**: Server-side HTML generation
- **Basecoat UI**: Modern CSS component library
- **Datastar**: Hypermedia-driven interactivity without heavy JavaScript

## Architecture

The HTML service is organized into three main namespaces:

### `riverdb.html.layout`
Contains helper functions for generating HTML with Basecoat components:
- `base-layout`: Main HTML structure with CSS/JS includes
- UI components: `container`, `card`, `button`, `input`, `table`, `alert`, `badge`, `nav`

### `riverdb.html.handlers`
HTTP handlers for routes:
- `home-page`: Landing page
- `about-page`: About page
- `demo-page`: Interactive demo using Datastar
- `increment-handler`: Datastar endpoint for counter demo
- `search-handler`: Datastar endpoint for search demo

### `riverdb.html.server`
Pedestal server configuration:
- Routes definition
- Service map creation
- Mount lifecycle management

## Running the Service

The HTML service starts automatically when you start the main server and runs on port 9595.

To start manually in the REPL:

```clojure
(require '[mount.core :as mount])
(require '[riverdb.html.server])
(mount/start #'riverdb.html.server/html-server)
```

To stop:

```clojure
(mount/stop #'riverdb.html.server/html-server)
```

## Accessing the App

Once running, visit:
- Home: http://localhost:9595/
- About: http://localhost:9595/about
- Demo: http://localhost:9595/demo

## How Datastar Works

Datastar enables dynamic interactivity through hypermedia exchanges:

1. **Store**: Client-side reactive state managed via `data-store` attribute
2. **Actions**: Triggered via `data-on-click`, `data-on-input`, etc.
3. **Requests**: `$$get()` fetches HTML fragments from server
4. **Merging**: Server returns fragments with `data-merge-store` to update state
5. **Reactivity**: UI automatically updates when store changes

Example from the counter demo:
```clojure
[:div
  {:data-store "{count: 0}"}
  [:span {:data-text "$count"}]
  [:button {:data-on-click "$$get('/html/increment')"} "Increment"]]
```

Server handler returns:
```clojure
[:div {:data-merge-store "{count: 1}"}]
```

## Adding New Pages

1. Create handler function in `riverdb.html.handlers`
2. Add route in `riverdb.html.server/routes`
3. Use layout helpers from `riverdb.html.layout`
4. For interactivity, add Datastar attributes and server endpoints

## Basecoat Components

Basecoat provides a complete set of UI components. See helpers in `riverdb.html.layout`:

- Navigation: `nav`
- Layout: `container`, `card`
- Forms: `input`, `button`
- Data: `table`
- Feedback: `alert`, `badge`

## Further Reading

- [Basecoat UI Documentation](https://basecoatui.com/)
- [Datastar Documentation](https://data-star.dev/)
- [Datastar Clojure SDK](https://github.com/starfederation/datastar-clojure)

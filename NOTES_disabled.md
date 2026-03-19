## Development Commands

### Backend Server
```bash
# Start with REPL (recommended for development)
clj -A:dev:server:rad
# Then in REPL:
user=> (start)
user=> (stop)
user=> (restart)
```

### Frontend (ClojureScript)
```bash
yarn main         # Watch and compile :main build (hot reload)
yarn compile      # Single compile
yarn release      # Production build
```

Shadow-cljs nREPL port: 9000
Shadow-cljs dashboard: http://localhost:9630



### Testing
```bash
# Clojure backend tests
clj -A:dev:clj-tests --watch

# ClojureScript tests (browser)
npx shadow-cljs watch :test     # Terminal 1 — serves at http://localhost:8022
npx karma start                 # Terminal 2

# CI ClojureScript tests
npx shadow-cljs compile :ci-tests && npx karma start --single-run
```



### Full Dev Setup
1. Start Datomic transactor `bin/transactor config/dev-transactor-template.properties` (default URI: `datomic:dev://localhost:4334/riverdb`)
2. Terminal 1: `clj -A:dev:server:rad` → `(start)`
3. Terminal 2: `npm run main`
4. App: http://localhost:8989 | GraphiQL: http://localhost:8989/graphiql

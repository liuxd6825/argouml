# ArgoUML AI REST API — Specification

> Use-case (actor-goal) diagram API contract. The foundation
> (Phase 1) lands ModelKind + DiagramOperations support so empty
> UMLUseCaseDiagram instances can be created; the REST handlers
> (Phase 2+) are listed but **not implemented yet** — every
> `/d/{d}/usecasediagram/...` route below returns 404
> ROUTE_NOT_FOUND until Phase 2 lands.

## Status of this document

- **Phase 1 (foundational)**:  ✅ this revision
  - `ModelKind.USECASE("usecasediagram")` enum value
  - `DiagramOperations.create("name", USECASE)` returns a fresh
    `org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram`
  - `DiagramOperations.kindOf(d)` returns `ModelKind.USECASE` for the
    above type, `null` for unknown
  - 1 JUnit 3 test: `TestModelKindUSECASE`

- **Phase 2 (service skeleton)** — not in this revision
  - `application.usecasediagram.UseCaseDiagramService` implementing
    `org.argouml.ai.application.common.DiagramService` and returning
    `ModelKind.USECASE` from `kind()`. Will be the single facade that
    REST handlers depend on.

- **Phase 3 (domain operations)** — not in this revision
  - `domain.usecasediagram.ActorOperations`
    (`build`, `findByName`, `delete`, `setPosition`)
  - `domain.usecasediagram.UseCaseOperations`
    (`build`, `findByName`, `delete`, `setPosition`, `setDescription`)
  - `domain.usecasediagram.IncludeOperations`
    (`build`, `findBy`, `delete`) for `UMLUseCase` ↔ `UMLUseCase`
  - `domain.usecasediagram.ExtendOperations`
    (`build`, `findBy`, `delete`) including `ExtensionPoint`
  - All wrapped in `UndoScope` for atomicity / GUI undo

- **Phase 4 (REST handlers, 15 endpoints)** — not in this revision

## Phase 1 surface (what already works after this commit)

```
POST /project/diagrams
{ "name": "用例图", "kind": "usecasediagram" }
→ 201 {"ok": true, "data": {"name": "用例图", "kind": "usecasediagram"}}

GET /project/diagrams
→ 200 [ ... includes the entry above ... ]
```

Everything else below is **planned** for the next phases.

---

## Conformance to existing conventions

- Wire shape: standard envelope
  ```json
  success: { "ok": true, "data": <payload> }
  failure: { "ok": false, "error": { "code": "UPPER_SNAKE", "message": "..." } }
  ```
- Path style: `/d/{d}/<kind>/<thing>/<id>`
  - matches existing `/d/{d}/classdiagram/classes/...` shape verbatim
- ID encoding: UTF-8 names, no URL encoding on the server side; the
  client is responsible for URL-percent-encoding Chinese characters
  when needed (existing behavior since the class-diagram endpoints)
- Coordinates: `{ "x": int, "y": int }`, 0-origin, no negative
- Description on UseCase: optional string, defaults to `""`
- Visibility modifier: not modeled on UseCase/Actor (UML omits it)

## Future endpoints (Phase 2-4 contract)

All paths under `/d/{diagramName}/usecasediagram/...`.
All methods are case-sensitive (existing convention).

### 2.1 Actor CRUD (5)

```
POST   /actors                       201
GET    /actors                       200
GET    /actors/{a}                  200
PUT    /actors/{a}                  200
DELETE /actors/{a}                  204
```

Wire shapes:

```json
// POST body
{ "name": "用户", "x": 80, "y": 60 }
→ 201 { "ok": true, "data": { "name": "用户" } }

// PUT body (all fields optional, only-rename supported here)
{ "newName": "管理员", "x": 100, "y": 100 }

// GET /actors → 200
{ "ok": true, "data": [
  { "name": "用户", "x": 80, "y": 60 },
  { "name": "管理员", "x": 100, "y": 100 }
] }
```

Error codes:
- 400 INVALID_NAME — empty / whitespace name
- 400 DUPLICATE_ACTOR — name already exists in this diagram
- 404 ACTOR_NOT_FOUND
- 404 DIAGRAM_NOT_FOUND

### 2.2 UseCase CRUD (5)

```
POST   /usecases                       201
GET    /usecases                       200
GET    /usecases/{u}                  200
PUT    /usecases/{u}                  200
DELETE /usecases/{u}                  204
```

Wire shapes (note `description` and optional `extensionPoints`):

```json
// POST body
{
  "name": "登录",
  "description": "用户输入账号密码后进入系统",
  "x": 200, "y": 60,
  "extensionPoints": ["用户名输入"]   // optional, default []
}
→ 201 { "ok": true, "data": { "name": "登录" } }

// PUT body (all optional)
{
  "newName": "登录账号",
  "description": "...",
  "x": 220, "y": 80
}
```

Error codes: same pattern as actors.

### 2.3 Actor ↔ UseCase associations (3)

```
POST   /associations                       201
GET    /associations                       200
DELETE /associations/{id}                 204
```

Wire shapes (binary association between one actor and one usecase):

```json
// POST body
{ "actor": "用户", "usecase": "登录" }
// 201 { "ok": true, "data": { "id": "用户|登录", "actor": "用户", "usecase": "登录" } }
```

ID convention: `actor|usecase` (pipe-separated, matching the existing
`relationships/{id}` pattern in class diagram).

### 2.4 Include relationships (3)

UseCase A includes UseCase B (base A depends on B):

```
POST   /includes                        201
GET    /includes                        200
DELETE /includes/{id}                  204
```

```json
// POST body
{ "base": "下单", "inclusion": "登录" }
// 201 { "ok": true, "data": { "id": "下单|登录", "base": "下单", "inclusion": "登录" } }
```

### 2.5 Extend relationships (3)

UseCase A extends UseCase B (A adds behavior to B at some extension
point):

```
POST   /extends                         201
GET    /extends                         200
DELETE /extends/{id}                   204
```

```json
// POST body
{ "base": "下单", "extension": "VIP下单", "extensionPoint": "支付后" }
// 201 { "ok": true, "data": { "id": "下单|VIP下单", "base": "下单", "extension": "VIP下单", "extensionPoint": "支付后" } }
```

If `extensionPoint` is omitted, a default `""` is created.

### 2.6 Diagram-kind agnostic endpoints (reuse, no change)

The following existing endpoints already work for use-case diagrams once
they're created via Phase 1:

- `GET /project/diagrams/{d}` — diagram metadata
- `GET /project/diagrams/{d}/snapshot` — returns empty `actors` /
  `usecases` / `associations` arrays until Phase 3+ populates them
- `DELETE /project/diagrams/{d}` — removes the diagram (handle
  implementation gap, see open question below)
- `POST /project/cleanup-datatypes` — no effect on use-case diagram

## Layout endpoints (Phase 2+, reuse existing handlers)

```
GET  /layout   →  list actors + usecases with (x, y)
POST /layout   →  invoke ArgoUML's useCaseDiagram layouter
```

Reuses the existing `GetLayoutHandler` / `PostLayoutHandler` from
`org.argouml.ai.inbound.rest.classdiagram.handlers.layout` once we
wire the kind through. See AGENTS.md for the per-diagram-type rule.

## Open issues to resolve before Phase 2

1. **Layout dispatcher**: today's `PostLayoutHandler` calls
   `ClassdiagramLayouter.layout()` hard-coded. Phase 2 needs to
   switch on `ModelKind` and call either
   `ClassdiagramLayouter` or `UseCaseDiagramLayouter`
   (does that exist? — **verify in argouml-app**).
2. **Snapshot shape**: today's snapshot returns `{classes,
   associations, generalizations, dependencies}`. We need to
   branch on `kind` and return `{actors, usecases, associations,
   includes, extends}`. Multi-key JSON builders go in
   `infrastructure.json` (probably a new
   `UsCadeDiagramSnapshotBuilder.java`).
3. **DiagramService registration**: `DiagramServices.java:38`
   currently registers `ClassDiagramService`. Phase 2 adds
   `new UseCaseDiagramService()` — but the service is currently a
   no-op stub. Real service logic lands in Phase 2 once we have
   domain operations to compose.
4. **`extends/{id}` and `includes/{id}` naming**: using
   `base|extension` and `base|inclusion` as IDs mirrors the existing
   class-diagram `relationships/{id}` style. Verify there are no
   duplicate-association collisions if the same pair is added twice
   (UNIQUE constraint via `UMLInclude`/`UMLExtend`).

## File layout the work will introduce (Phase 2+)

Per the AGENTS.md "per-diagram-type" rule, this is the planned final
shape:

```
src/main/java/org/argouml/ai/
├── application/
│   └── usecasediagram/                 ← NEW
│       └── UseCaseDiagramService.java
├── domain/
│   └── usecasediagram/                 ← NEW
│       ├── ActorOperations.java
│       ├── UseCaseOperations.java
│       ├── IncludeOperations.java
│       └── ExtendOperations.java
└── inbound/rest/
    └── usecasediagram/                 ← NEW
        └── handlers/
            ├── actor/
            │   ├── CreateActorHandler.java
            │   ├── ListActorsHandler.java
            │   ├── GetActorHandler.java
            │   ├── UpdateActorHandler.java
            │   └── DeleteActorHandler.java
            ├── usecase/                 (mirrors actor/)
            └── relationship/
                ├── AssociationHandler.java
                ├── IncludeHandler.java
                └── ExtendHandler.java
```

Plus tests in `tests/.../usecasediagram/...` mirroring the same paths.

## Build & run commands

```bash
# Compile + run only the new test
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd /Users/lxd/Projects/ai/uml-project/argouml
mvn -f src/argouml-ai/pom.xml -am test -Dtest=TestModelKindUSECASE -o

# End-to-end smoke (assumes GUI HTTP server restarted with new modelkind)
curl -X POST http://127.0.0.1:18766/project/diagrams \
     -H "Content-Type: application/json" \
     -d '{"name":"用例图","kind":"usecasediagram"}'
```

## Coding conventions honored

- JUnit 3 (`import junit.framework.TestCase`) — see AGENTS.md
- Java source encoding ISO-8859-1; Unicode escapes for any CJK in
  source. Test class names stay ASCII.
- No new third-party deps (uses ArgoUML `Facade` / `CoreFactory`
  / existing `Model.getFacade()` etc.)
- i18n via `org.argouml.i18n.Translator` if/when UI strings are
  added (no UI strings currently planned for Phase 2)
- No commits — user manages git (AGENTS.md)

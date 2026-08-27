# IDP Service — Agent Guide

## Identity

Java microservice (Dropwizard + Guice). Owns the IDP catalog: entity CRUD, RBAC, connector config, schema validation, event emission. Does NOT own execution runtime — that belongs to po-server (Go).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Dropwizard 4.x + Guice DI |
| Persistence | MongoDB (primary), Redis (cache + pub/sub) |
| Inter-service | gRPC (Access Control, Pipeline), REST (Connectors, Secrets) |
| Build | Bazel via Make (`make build t=idp-service`) |
| Testing | JUnit 5 + Mockito, in-memory Mongo for integration |

## Package Structure

```
idp-service/
├── src/main/java/io/harness/idp/
│   ├── catalog/          # Entity models, repositories, services
│   │   ├── entities/     # MongoDB document classes
│   │   ├── repositories/ # Spring Data Mongo repositories
│   │   └── services/     # Business logic
│   ├── events/           # Domain event definitions and publishers
│   ├── rbac/             # Permission constants and check helpers
│   ├── connector/        # Connector resolution logic
│   └── ...
├── src/test/java/        # Mirror structure for tests
└── docs/                 # Architecture and design docs
```

## Entity Model Rules

- **Identity triple:** (parentUniqueId, kind, identifier) — always unique, always immutable after creation
- **Kind naming:** PascalCase, singular (e.g., `Action`, `Workflow`, `Component`)
- **Identifier format:** lowercase kebab-case, max 128 chars, URL-safe
- **Polymorphism:** Base fields shared; `spec` subdocument varies by kind; `type` field discriminates within a kind
- **Versioning:** Integer version field, incremented on every write, used for optimistic concurrency
- **No soft-delete:** Entities are hard-deleted from MongoDB
- **No cross-entity joins:** References use the identity triple stored as field values

## Scope Rules

- Every entity belongs to exactly one scope level: Account, Org, or Project
- `parentUniqueId` encodes the full scope path
- Scope is set at creation and never changes
- Visibility cascades downward (Account entities visible at Org and Project levels)

## RBAC Rules

- Three IDP resource types: `IDP_CATALOG`, `IDP_WORKFLOW`, `IDP_ENVIRONMENT`
- All mutating operations MUST check permissions via Access Control Service before proceeding
- Permission check is a gRPC call — never inline the decision logic
- Scope inheritance: Account permission cascades unless restricted

## Connector Rules

- Connectors are platform objects, not IDP entities — reference by identifier only
- GenericHTTP Connector is the primary type for IDP use cases
- Never store secret material in the IDP database — always use Harness Secret Manager references
- Connector resolution happens at execution-trigger time, not at entity creation time

## Execution Boundary

IDP Service responsibility ends at:
1. Validating the entity schema
2. Checking RBAC
3. Emitting the lifecycle event
4. Resolving connector references for the orchestrator

Everything after that (step orchestration, HTTP execution, state management) belongs to po-server and runner infrastructure. Do NOT add execution logic to idp-service.

## Event Patterns

- Domain events: `{Kind}{Action}Event` (e.g., `WorkflowCreateEvent`, `ActionUpdateEvent`)
- Published via Redis pub/sub
- Events are fire-and-forget from the publisher's perspective — no transactional outbox
- Consumers are idempotent

## Naming Conventions

| What | Convention | Example |
|------|-----------|---------|
| Entity class | `{Kind}.java` in `catalog/entities/` | `Action.java`, `Workflow.java` |
| Repository | `{Kind}Repository.java` | `ActionRepository.java` |
| Service interface | `{Kind}Service.java` | `ActionService.java` |
| Service impl | `{Kind}ServiceImpl.java` | `ActionServiceImpl.java` |
| Resource (API) | `{Kind}Resource.java` | `ActionResource.java` |
| Event | `{Kind}{Verb}Event.java` | `WorkflowCreateEvent.java` |
| DTO | `{Kind}DTO.java` or `{Kind}RequestDTO.java` | `ActionDTO.java` |

## Backstage Compatibility

Legacy: IDP originally built on Backstage catalog model. Active migration away from it.

- New entity kinds (Action, Workflow, Environment) use Harness-native schema — no Backstage coupling
- Old kinds still accept `backstage.io/v1alpha1` on ingest but normalize internally
- When adding new kinds, never introduce Backstage dependencies

## DOs

- Use the identity triple for all entity references
- Check RBAC before any mutation
- Emit domain events after successful writes
- Use optimistic concurrency (version field) for updates
- Follow existing package structure (entities → repositories → services → resources)
- Add Guice bindings in the appropriate module class
- Write unit tests with mocked dependencies AND integration tests with in-memory Mongo
- Use `parentUniqueId` for scope — never reconstruct scope from separate account/org/project fields

## DON'Ts

- Don't store secrets or credential values — reference them via Secret Manager
- Don't create cross-collection MongoDB transactions — design for eventual consistency
- Don't use Backstage schemas for new entity kinds
- Don't hardcode scope levels — always derive from parentUniqueId
- Don't bypass RBAC checks, even for internal callers
- Don't introduce REST calls in hot paths — use Redis cache
- Don't add new collections without a corresponding repository and service layer

## Build and Test

```bash
# Build
make build t=idp-service

# Test specific file
make test f=idp-service/src/test/java/io/harness/idp/catalog/services/ActionServiceImplTest.java

# Test folder
make test f=idp-service/src/test/java/io/harness/idp/catalog/
```

## Related Documentation

- `docs/ARCHITECTURE.md` — Human-readable architectural overview
- `docs/WORKFLOWS_V2.md` — Current Workflows V2 initiative (temporary)
- `docs/IDP_DATA_AND_RBAC_DEEP_DIVE.md` — Detailed data and RBAC walkthrough
- `docs/IDP_CONCEPTUAL_GUIDE.md` — Beginner-friendly concept guide

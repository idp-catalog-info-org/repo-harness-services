# IDP Service — Architectural Overview

This document describes the stable architectural invariants of the IDP (Internal Developer Portal) service. It is intended for humans who need to understand system boundaries, data-model guarantees, and design decisions that are unlikely to change between releases.

For agent-oriented rules and conventions, see the root-level `AGENTS.md`. For deep dives into specific subsystems, see the companion docs referenced at the bottom.

---

## Service Identity

IDP Service is the control-plane backend for Harness Internal Developer Portal. It owns the catalog of developer-facing entities (services, APIs, resources, workflows, actions, environments, etc.), the metadata and lifecycle around those entities, and the configuration that drives developer self-service experiences.

It does **not** own execution. Execution of workflows and actions is delegated to a separate orchestration service (po-server) which has its own persistence and runtime.

---

## Terminology

| Term | Meaning |
|------|---------|
| **Entity** | Any first-class object in the catalog — identified by the triple (parentUniqueId, kind, identifier). |
| **Kind** | The type discriminator for an entity. Kinds are either built-in (Component, API, Resource, System, Domain, User, Group, Workflow, Action, Environment) or custom-defined per account. |
| **Scope** | The hierarchy level at which an entity lives: Account → Org → Project. Every entity has exactly one scope. |
| **parentUniqueId** | The opaque identifier that encodes an entity's scope placement. |
| **Connector** | A reusable, account-scoped credential bundle that entities reference to reach external systems. |
| **Catalog** | The unified registry of all entities across all kinds and scopes within an account. |
| **Self-Service Flow** | An end-user interaction that creates, modifies, or provisions resources — modeled as Workflow execution. |

---

## System Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│                     IDP Service (Java)                       │
│                                                             │
│  Owns: Entity CRUD, RBAC policy, Connector config,          │
│        Schema validation, Event emission, Catalog indexing   │
│                                                             │
│  Does NOT own: Execution runtime, Step orchestration,        │
│                Secret storage, Pipeline scheduling           │
└────────────────────┬──────────────────┬─────────────────────┘
                     │                  │
          Entity lifecycle        Execution trigger
          events (Redis)          (gRPC / internal API)
                     │                  │
                     ▼                  ▼
            ┌────────────────┐   ┌──────────────┐
            │  Consumers     │   │  po-server   │
            │  (async)       │   │  (Go)        │
            └────────────────┘   └──────────────┘
```

**Key boundary rule:** IDP Service is the source of truth for _what_ can be executed and _who_ is allowed to trigger it. The orchestration service is the source of truth for _how_ it executes and the state of in-flight runs.

---

## Data Model Invariants

### Entity Identity

Every entity is uniquely identified by the composite key:

- **parentUniqueId** — encodes account + optional org + optional project
- **kind** — the entity type (case-sensitive, PascalCase)
- **identifier** — unique within the (parentUniqueId, kind) namespace

This triple is immutable after creation. Renaming an entity means delete + recreate.

### Storage Model

- **Primary store:** MongoDB (one collection per logical entity group)
- **Cache layer:** Redis (read-through for hot paths, pub/sub for cross-instance invalidation)
- **No relational joins.** References between entities use the (parentUniqueId, kind, identifier) triple stored as a field value.

### Polymorphic Documents

Catalog entities use a polymorphic document pattern:

- A base set of fields is shared across all entity kinds (metadata, scope fields, audit timestamps, ownership)
- Kind-specific fields live in a `spec` subdocument whose schema varies by kind
- The `type` field within a kind provides a second level of discrimination (e.g., Action kind has types: HTTP, Builtin, Composite)

### Versioning

Entities carry a monotonically increasing version number. Optimistic concurrency control via version-match on write. No soft-delete — entities are hard-deleted from the collection.

---

## Auth and RBAC Model

### Principals

- **User** — human identity via Harness platform SSO
- **Service Account** — machine identity with API key
- **API Key + Token** — scoped credential pair attached to a user or service account

### Resource Types

IDP registers its own resource types with the platform Access Control Service:

| Resource Type | Governs |
|---------------|---------|
| IDP_CATALOG | General catalog entities (Component, API, System, etc.) |
| IDP_WORKFLOW | Workflow and Action entities |
| IDP_ENVIRONMENT | Environment entities used as execution targets |

### Permission Check Flow

1. Request arrives with an auth token (JWT or API key)
2. IDP Service extracts the principal and requested operation
3. A gRPC call to Access Control Service checks whether the principal holds the required permission on the target resource at the appropriate scope
4. If denied, the request fails before any data mutation

### Scope Inheritance

Permissions granted at Account scope cascade to Org and Project levels unless explicitly restricted. Entity visibility follows the same downward-cascade rule.

For a detailed walkthrough with examples, see `IDP_DATA_AND_RBAC_DEEP_DIVE.md`.

---

## Connector Model

Connectors are the mechanism by which entities reference external credentials without embedding secrets.

### Design Principles

- Connectors are **account-scoped platform objects** — they live outside the IDP entity model and are managed by the platform connector service.
- An entity's spec references a connector by its identifier. The IDP Service resolves the connector at execution time but never stores the secret material itself.
- The **GenericHTTP Connector** is the primary connector type for IDP. It models a base URL plus one of several auth strategies.

### Supported Auth Strategies (GenericHTTP)

1. No Authentication
2. Custom Header (arbitrary header name + secret value)
3. API Token (bearer token)
4. Basic Auth (username + password secret)
5. OAuth2 Client Credentials (client ID + client secret + token URL)
6. OAuth2 Authorization Code (interactive grant, refresh-token stored as secret)

### Invariant

The IDP Service never holds plaintext secrets in its own database. All secret references are Harness Secret Manager references resolved at point of use.

---

## Execution Model

### Separation of Concerns

| Responsibility | Owner |
|---------------|-------|
| Validate entity schema | IDP Service |
| Authorize trigger | IDP Service |
| Emit lifecycle event | IDP Service |
| Resolve connector + secrets | IDP Service (at trigger time) |
| Orchestrate steps/nodes | po-server |
| Run HTTP calls / builtins | Runner infrastructure |
| Persist execution state | po-server |

### Event-Driven Lifecycle

Entity mutations emit domain events (create, update, delete) via Redis pub/sub. Downstream consumers (including the orchestration layer) subscribe to these events for cache invalidation, indexing, and trigger evaluation.

### Runner Infrastructure

Execution of individual steps happens on a hosted runner fleet managed by the platform. IDP does not manage runner instances directly — it passes execution requests to the orchestration service which schedules work on the appropriate runner.

---

## API Contract Principles

- All external APIs are REST over HTTP, versioned via URL path prefix
- Internal service-to-service calls use gRPC where latency matters, REST otherwise
- Request/response bodies are JSON; entity specs accept YAML on ingest and normalize to internal representation
- Pagination uses cursor-based tokens for list endpoints
- All mutating endpoints are idempotent when called with the same identifier and version

---

## Backstage Compatibility (Legacy)

IDP was originally built on top of Backstage's catalog model. The following legacy properties remain:

- Entity YAML files can still reference Backstage `apiVersion: backstage.io/v1alpha1` — these are normalized on ingest
- Some internal field names retain Backstage-era naming (e.g., `metadata.annotations`)
- The catalog indexing pipeline still handles Backstage-format descriptor files from Git sources

**Active migration:** The service is progressively removing Backstage dependencies. New entity kinds (Action, Workflow, Environment) use a Harness-native schema with no Backstage coupling. The goal is full independence from Backstage conventions across all entity kinds.

---

## Integration Points

| System | Protocol | Purpose |
|--------|----------|---------|
| Access Control Service | gRPC | Permission checks |
| Platform Connector Service | REST | Connector resolution |
| Secret Manager | REST | Secret value retrieval at execution time |
| Pipeline Service | gRPC | Legacy workflow execution (V1, being phased out) |
| po-server | gRPC / REST | V2 workflow orchestration |
| Redis | Pub/Sub + Cache | Event distribution, entity caching |
| MongoDB | Driver | Primary entity persistence |

---

## Further Reading

| Document | Purpose |
|----------|---------|
| `IDP_DATA_AND_RBAC_DEEP_DIVE.md` | Detailed data model examples and RBAC walkthrough |
| `IDP_CONCEPTUAL_GUIDE.md` | Beginner-friendly explanation of IDP concepts |
| `WORKFLOWS_V2.md` | Current initiative: Workflow and Action entity implementation |
| `../AGENTS.md` | Agent-consumable rules and conventions |

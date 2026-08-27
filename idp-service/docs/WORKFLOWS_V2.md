# Workflows V2 — Initiative Reference

> **Status:** In progress. This document is temporary and will be removed once the Workflows V2 implementation is complete.

---

## Initiative Summary

Workflows V2 replaces the legacy pipeline-backed self-service flows with a native, lightweight execution model. Two new entity kinds — **Action** and **Workflow** — are introduced to the IDP catalog. Actions represent atomic operations (primarily HTTP calls). Workflows compose Actions into a directed acyclic graph (DAG) with input forms and output mappings.

The execution runtime is handled by po-server (Go). This document focuses on the **idp-service side**: entity modeling, schema validation, CRUD, RBAC, and event emission.

---

## Two New Entity Kinds

### Action

An Action is an atomic, reusable operation definition. It declares what to call, how to authenticate, and what shape the inputs/outputs take.

**Kind:** `Action`
**Resource type:** `IDP_WORKFLOW`

#### Action Types

| Type | Description | Auth |
|------|-------------|------|
| **HTTP** | Calls an external URL via GenericHTTP connector. ~95% of all Actions. | GenericHTTP connector (6 auth strategies) |
| **Builtin** | Opaque server-side operation (e.g., `harness.secret.create`). No connector — handler lives in po-server. | N/A (internal) |
| **Composite** | Server-side fan-out over multiple targets. | Inherited per target |

#### Action Schema Structure

```yaml
kind: Action
identifier: create-jira-ticket
type: http
spec:
  connection:
    connectorRef: jira-connector
    baseUrl: https://org.atlassian.net  # optional override
  method: POST
  path: /rest/api/3/issue
  headers:
    Content-Type: application/json
  body:
    fields:
      project:
        key: ${{ inputs.projectKey }}
      summary: ${{ inputs.summary }}
  inputs:
    type: object
    properties:
      projectKey:
        type: string
      summary:
        type: string
    required: [projectKey, summary]
  outputs:
    ticketId:
      value: ${{ response.body.id }}
    ticketKey:
      value: ${{ response.body.key }}
```

#### Builtin Action Schema

```yaml
kind: Action
identifier: create-secret
type: builtin
spec:
  handler: harness.secret.create
  inputs:
    type: object
    properties:
      name:
        type: string
      value:
        type: string
        sensitive: true
    required: [name, value]
  outputs:
    secretRef:
      value: ${{ result.secretIdentifier }}
```

### Workflow

A Workflow composes Actions into an executable DAG with a user-facing input form.

**Kind:** `Workflow`
**Resource type:** `IDP_WORKFLOW`

#### Workflow Schema Structure

```yaml
kind: Workflow
identifier: onboard-service
spec:
  inputs:
    type: object
    properties:
      serviceName:
        type: string
        title: Service Name
        description: Name of the new service
      language:
        type: string
        enum: [java, go, python]
        title: Language
    required: [serviceName, language]

  nodes:
    - id: create-repo
      name: Create Repository
      actionRef: create-github-repo
      inputs:
        repoName: ${{ inputs.serviceName }}
        template: ${{ inputs.language }}-starter

    - id: register-catalog
      name: Register in Catalog
      actionRef: register-component
      dependsOn: [create-repo]
      inputs:
        repoUrl: ${{ nodes.create-repo.output.repoUrl }}
        componentName: ${{ inputs.serviceName }}

  outputs:
    repoUrl:
      value: ${{ nodes.create-repo.output.repoUrl }}
    catalogLink:
      value: ${{ nodes.register-catalog.output.entityUrl }}
```

#### Four-Part Document

1. **Metadata** — kind, identifier, name, description, owner, tags
2. **spec.inputs** — JSON Schema defining the user-facing form
3. **spec.nodes** — Ordered list of Action invocations forming a DAG (via `dependsOn`)
4. **spec.outputs** — Key-value map surfaced to the user after execution completes

---

## Expression Syntax

Workflows use a mustache-style expression language:

| Pattern | Resolves To |
|---------|-------------|
| `${{ inputs.X }}` | Value from the workflow input form |
| `${{ nodes.<id>.output.<key> }}` | Output from a previously executed node |
| `${{ response.body.X }}` | Within Action: response body field |
| `${{ response.header.X }}` | Within Action: response header value |
| `${{ response.statusCode }}` | Within Action: HTTP status code |

Expressions are resolved at execution time by po-server. IDP Service validates that expression references are structurally sound (referenced node IDs exist, input names match schema) but does not evaluate them.

---

## What Exists vs What Needs Building

### Already Exists (in idp-service)

| Component | Location / Evidence |
|-----------|-------------------|
| `GenericEntity` CRUD (predecessor to Action) | ~14 files across entities, repositories, services, resources |
| Workflow entity skeleton | Event classes exist (WorkflowCreateEvent, etc.) |
| GenericHTTP Connector definition | ADR-75 implemented, connector type registered |
| RBAC resource type `IDP_WORKFLOW` | Registered with Access Control Service |
| Event infrastructure for lifecycle events | Redis pub/sub pattern established |
| `Action.java` entity class (empty) | `catalog/entities/Action.java` on current branch |

### Needs Building (in idp-service)

| Component | Description |
|-----------|-------------|
| **Action entity model** | Full MongoDB document class with spec subtypes (HTTP, Builtin, Composite) |
| **Action repository** | Spring Data Mongo repository with custom queries |
| **Action service** | CRUD business logic, schema validation, event emission |
| **Action resource (API)** | REST endpoints for Action CRUD |
| **Workflow entity model** | MongoDB document with inputs/nodes/outputs spec |
| **Workflow repository** | Spring Data Mongo repository |
| **Workflow service** | CRUD + DAG validation + expression reference validation |
| **Workflow resource (API)** | REST endpoints for Workflow CRUD |
| **Schema validation** | JSON Schema validation for inputs definitions |
| **DAG validation** | Cycle detection, dangling dependsOn references |
| **Expression validation** | Structural check that referenced nodes/inputs exist |
| **GenericEntity → Action migration** | Rename/refactor existing GenericEntity code to Action |
| **Guice bindings** | Wire new services into the DI container |

---

## Design Constraints

1. **Action is the unit of reuse.** Workflows never contain inline HTTP call definitions — they always reference an Action by identifier.

2. **Connectors are resolved late.** At entity creation, only validate that the connectorRef field is present for HTTP actions. Actual connector resolution (fetching credentials) happens at execution time via po-server.

3. **Inputs are JSON Schema.** The `spec.inputs` field in both Actions and Workflows must be valid JSON Schema (draft 2020-12). This schema drives UI form generation.

4. **DAG, not sequence.** Workflow nodes declare dependencies via `dependsOn`. Nodes without dependencies can execute in parallel. The DAG must be acyclic — reject on create/update if cycles are detected.

5. **No execution state in idp-service.** Execution status, logs, and step results live in po-server's PostgreSQL. IDP Service never queries or caches execution state.

6. **Immutable identity, mutable spec.** An Action/Workflow's (parentUniqueId, kind, identifier) never changes. Everything in `spec` can be updated (with version increment).

7. **GenericHTTP is the default.** When type is omitted on an Action, default to `http`. The HTTP path is the overwhelmingly common case.

8. **Account-scoped connectors, any-scope entities.** Actions can live at Project scope but reference Account-scoped connectors. Resolution respects scope hierarchy.

---

## Deferred Scope

The following are explicitly out of scope for the current phase:

| Item | Reason |
|------|--------|
| **Composite Action type** | Server-side fan-out adds complexity. HTTP and Builtin cover initial needs. |
| **Conditional nodes** | `if` / `when` clauses on workflow nodes. Deferred to V2.1. |
| **Looping** | `forEach` over a list in workflow nodes. Deferred to V2.1. |
| **Approval gates** | Human-in-the-loop nodes. Deferred to V2.1. |
| **Webhook triggers** | Auto-triggering workflows from external events. Separate initiative. |
| **Workflow versioning (multiple live versions)** | Single active version per identifier for now. |
| **Execution dry-run** | Validate a workflow run without side effects. Future enhancement. |

---

## Interaction with po-server (Brief)

IDP Service communicates with po-server at two points:

1. **Trigger:** When a user executes a workflow, IDP Service validates RBAC, resolves connector references, and sends an execution request to po-server with the fully resolved workflow definition and input values.

2. **Entity sync:** On Action/Workflow create/update/delete, IDP Service emits events. po-server subscribes to these events to keep its internal target/target_revision tables in sync.

po-server owns: execution orchestration, step scheduling on runners, state persistence, output collection. See po-server's own documentation for details.

---

## Naming Migration: GenericEntity → Action

The codebase currently has ~14 files implementing `GenericEntity` which is the predecessor name for what is now called `Action`. The migration strategy:

1. **Phase 1 (current):** Introduce `Action` as the new entity kind alongside GenericEntity
2. **Phase 2:** Migrate GenericEntity CRUD logic into Action service, adapting the schema
3. **Phase 3:** Deprecate and remove GenericEntity code paths
4. **Phase 4:** Data migration for existing GenericEntity documents in MongoDB

During the transition, both code paths may coexist. New features should be built on Action only.

---

## Quick Reference: Key Identifiers

| Concept | Value |
|---------|-------|
| Action kind string | `Action` |
| Workflow kind string | `Workflow` |
| RBAC resource type | `IDP_WORKFLOW` |
| GenericHTTP connector type | `GenericHTTP` |
| Event channel | Redis pub/sub on entity lifecycle topics |
| Global platform account | `__GLOBAL_HOSTED_PLATFORM_ACCOUNT_ID__` |
| Expression prefix | `${{` ... `}}` |

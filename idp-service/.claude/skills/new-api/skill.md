---
name: new-api
description: >
  Guide for adding a new API endpoint to the IDP service.
  Covers URL conventions, authentication, RBAC enforcement, scope handling,
  entity lookup, and the OpenAPI-first implementation approach.
  Use when introducing any new REST endpoint that touches catalog entities.
user-invocable: true
---

# Adding a New API to IDP Service

## URL Convention

All catalog entity APIs follow this pattern:

```
/v1/entities/{scope}/{kind}/{identifier}[/your-sub-resource]
```

- `scope` — Harness scope string: `account`, `account.orgId`, or `account.orgId.projectId`
- `kind` — catalog entity kind (e.g., `component`, `aiasset`, `workflow`, `environment`)
- `identifier` — entity identifier (metadata.name in Backstage terms)

The `Harness-Account` header carries the account identifier and is required for all authenticated calls.

## Authentication

Every API implementation class must be annotated with:

```java
@NextGenManagerAuth
```

This enforces that requests carry a valid auth token (API key via `x-api-key` header, or session-based for UI calls).

## RBAC Enforcement

### The Pattern

Every endpoint that reads or mutates a catalog entity MUST call RBAC before doing any work:

```java
catalogServiceHelper.checkCrudRbac(
    harnessAccount,      // from Harness-Account header
    orgIdentifier,       // extracted from scope (may be null)
    projectIdentifier,   // extracted from scope (may be null)
    kind,                // entity kind from path
    entityRef,           // "kind:scope/identifier"
    "view"               // permission: "view", "edit", "create", "delete"
);
```

### How checkCrudRbac Works

Location: `CatalogServiceHelper.java` (~line 329)

1. **Service-to-service bypass**: If the caller is a pure service-to-service call (no user context), RBAC is skipped via `RbacUtils.isPureServiceToServiceCall()`.

2. **Resource type selection** based on `kind`:

   | Kind | Resource Type | Permission Format |
   |------|--------------|-------------------|
   | `workflow` | `IDP_WORKFLOW` | `idp_workflow_{permission}` |
   | `environment` | `IDP_ENVIRONMENT` | `idp_idpenvironment_{permission}` |
   | `environment_blueprint` | `IDP_ENVIRONMENT_BLUEPRINT` | `idp_environmentblueprint_{permission}` |
   | Everything else | `IDP_CATALOG` | `idp_catalog_{permission}` |

3. **Access check**: Calls `accessControlClient.checkForAccessOrThrow(...)` with the principal type, resource scope, resource, and permission string. Throws on denial.

### Scope Parsing

The `orgIdentifier` and `projectIdentifier` come from OpenAPI query parameters (auto-generated from `OrgIdentifierQueryParam` and `ProjectIdentifierQueryParam` in the spec). No manual parsing needed.

### Entity Ref Construction

The `entityRef` passed to RBAC follows the format: `kind:scope/identifier`

```java
String entityRef = kind + ":" + scope + "/" + identifier;
```

## Entity Lookup

After RBAC passes, look up the entity scoped to the account:

```java
// Get the scope's unique ID (parentUniqueId for the entity)
ScopeInfo scopeInfo = getResponse(
    scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
String parentUniqueId = scopeInfo.getUniqueId();

// Look up entity
Optional<CatalogEntity> entity = catalogEntityRepository
    .findByParentUniqueIdAndKindAndIdentifier(parentUniqueId, kind, identifier);
```

This naturally enforces tenant isolation — an entity can only be found within its own account/org/project scope.

## Implementation: OpenAPI-First

All new endpoints MUST be defined in the OpenAPI contract first, then implement the generated interface.

**Step 1**: Add the endpoint to `idp-service/contracts/openapi/v1/openapi.yaml`

```yaml
/v1/entities/{scope}/{kind}/{identifier}/your-resource:
  parameters:
    - $ref: '#/components/parameters/OrgIdentifierQueryParam'
    - $ref: '#/components/parameters/ProjectIdentifierQueryParam'
    - $ref: '#/components/parameters/ScopeParam'
    - $ref: '#/components/parameters/KindParam'
    - $ref: '#/components/parameters/IdentifierParam'
  get:
    operationId: get-your-resource
    summary: Get Your Resource
    tags:
      - Entities
    parameters:
      - $ref: '#/components/parameters/AccountHeader'
    security:
      - x-api-key: []
    responses:
      '200':
        description: Success
      '404':
        description: Not found
```

The `tags` field determines which generated interface the method lands in. Tag with `Entities` to add to the existing `EntitiesApi` interface. A different tag creates a separate interface (which would need its own impl class and registration).

**Step 2**: Build to generate the interface:
```bash
bazel build //idp-service/...
```

The generated interface lands at `io.harness.spec.server.idp.v1.EntitiesApi`. The `operationId` `get-your-resource` becomes method `getYourResource(...)`. Parameter order follows: path params, query params, header params.

**Step 3**: Implement the method in `EntitiesApiImpl.java`:

```java
@Override
public Response getYourResource(String scope, String kind, String identifier,
    String orgIdentifier, String projectIdentifier,
    @AccountIdentifier String harnessAccount) {

  // 1. RBAC check
  String entityRef = kind + ":" + scope + "/" + identifier;
  catalogServiceHelper.checkCrudRbac(
      harnessAccount, orgIdentifier, projectIdentifier,
      kind, entityRef, "view");

  // 2. Look up entity within scope
  ScopeInfo scopeInfo = getResponse(
      scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
  Optional<CatalogEntity> entity = catalogEntityRepository
      .findByParentUniqueIdAndKindAndIdentifier(
          scopeInfo.getUniqueId(), kind, identifier);

  // 3. Your logic here
}
```

No additional registration is needed — `EntitiesApiImpl` is already registered via `HARNESS_RESOURCE_CLASSES`.

## Key Files Reference

| File | Purpose |
|------|---------|
| `idp-service/contracts/openapi/v1/openapi.yaml` | OpenAPI contract (source of truth) |
| `EntitiesApiImpl.java` | Implementation class for all entity endpoints |
| `CatalogServiceHelper.java` | `checkCrudRbac()` — RBAC enforcement |
| `CatalogEntityRepository.java` | Entity lookup queries |
| `CatalogServiceImpl.java` | `getEntity()` — reference implementation for entity detail |
| `IdpModule.java` | Guice bindings and config providers |
| `RbacUtils.java` | Helpers: `isPureServiceToServiceCall()`, `fromSecurityPrincipalType()` |
| `RbacConstants.java` | Resource type constants: `IDP_CATALOG`, `IDP_CATALOG_VIEW`, etc. |

## Checklist

When adding a new entity API:

- [ ] Endpoint defined in `openapi.yaml` with correct `operationId` and `Entities` tag
- [ ] Path-level parameters include `OrgIdentifierQueryParam`, `ProjectIdentifierQueryParam`, `ScopeParam`, `KindParam`, `IdentifierParam`
- [ ] `bazel build //idp-service/...` run to regenerate interface
- [ ] Method implemented in `EntitiesApiImpl.java`
- [ ] `Harness-Account` header is accepted and used
- [ ] `checkCrudRbac()` is called with correct permission before any logic
- [ ] Entity lookup uses `parentUniqueId` from scope (tenant isolation)
- [ ] Error responses use standard HTTP status codes (400, 403, 404)

# IDP Service - Data Modeling & RBAC Deep Dive

**Level:** Intermediate - One step deeper than concepts
**Focus:** Catalog data structure and how RBAC validates requests
**Prerequisites:** Read `IDP_CONCEPTUAL_GUIDE.md` first

---

## Table of Contents

1. [Database Overview](#database-overview)
2. [The Catalog Collection - Real Structure](#the-catalog-collection---real-structure)
3. [Understanding the Document Fields](#understanding-the-document-fields)
4. [Entity Identification & References](#entity-identification--references)
5. [How RBAC Connects to Catalog Data](#how-rbac-connects-to-catalog-data)
6. [RBAC Validation Flow](#rbac-validation-flow)
7. [Service Layer Overview](#service-layer-overview)
8. [Complete Request Example](#complete-request-example)
9. [Querying Catalog Data](#querying-catalog-data)

---

## Database Overview

### MongoDB Setup

IDP Service uses **MongoDB** as its primary database:

```
MongoDB Server
    │
    └── Database: idp-harness
            │
            ├── catalogEntities          ← Main collection (focus of this doc)
            ├── backstageCatalog         ← Synced version for Backstage UI
            └── (20+ other collections for configurations, stats, etc.)
```

### Focus of This Document

We'll focus on **`catalogEntities`** - this is where all your Components, APIs, Resources, Systems, Groups, and Users are stored.

**Why this collection matters:**
- It's the **heart of IDP** - everything revolves around catalog entities
- All CRUD operations (Create, Read, Update, Delete) work with this collection
- All RBAC checks validate access to entities in this collection
- When you see something in Backstage UI, it's reading from this collection

---

## The Catalog Collection - Real Structure

### Actual Document from catalogEntities

Here's a real document for a Component called "BackendYesPlease":

```javascript
{
  // MongoDB internal ID
  "_id": {
    "$oid": "68ee46bdf5d9414ddfb71f0a"
  },

  // ============================================
  // HARNESS SCOPE - Determines permission scope
  // ============================================
  "accountIdentifier": "IyrWsOn4RhGDDIDtYxz7YA",    // Required: Which Harness account
  "orgIdentifier": "default",                       // Optional: Which organization
  "projectIdentifier": "test",                      // Optional: Which project

  // ============================================
  // ENTITY IDENTIFICATION
  // ============================================
  "identifier": "BackendYesPlease",                 // Unique ID within scope
  "name": "BackendYesPlease",                       // Display name
  "uniqueId": "bTD0rJ3-T6-vjpKj_W-M2g",            // Global unique ID across system
  "parentUniqueId": "ul1hO1BJSDCjwvvh7cpRjA",      // Parent entity ID (if nested)

  // ============================================
  // ENTITY TYPE & CLASSIFICATION
  // ============================================
  "kind": "component",                              // Entity type: component, api, resource, etc.
  "type": "service",                                // Subtype: service, library, website, etc.
  "apiVersion": "harness.io/v1",                    // Schema version

  // ============================================
  // STORAGE TYPE
  // ============================================
  "referenceType": "INLINE",                        // INLINE or GIT_REFERENCED
  "_class": "io.harness.idp.catalog.entities.InlineCatalogEntity",  // Java class type

  // ============================================
  // ENTITY DETAILS
  // ============================================
  "description": "",                                // Human-readable description
  "owner": "group:account/_account_all_users",     // Who owns this entity
  "tags": [],                                       // Array of tags for categorization

  // ============================================
  // SPECIFICATION (Entity-specific details)
  // ============================================
  "spec": {
    "lifecycle": "prod"                             // production, experimental, deprecated
    // Can contain: dependencies, apis, resources, system, domain, etc.
  },

  // ============================================
  // METADATA (Additional information)
  // ============================================
  "metadata": {},                                   // Custom annotations, labels

  // ============================================
  // RELATIONSHIPS (Pre-computed for performance)
  // ============================================
  "relations": {
    "ownedBy": [
      "group:account/_account_all_users"
    ]
    // Can include: partOf, providesApi, consumesApi, dependsOn, etc.
  },

  // ============================================
  // YAML REPRESENTATION (Full entity as YAML)
  // ============================================
  "yaml": "apiVersion: harness.io/v1\nkind: Component\ntype: service\nidentifier: BackendYesPlease\n...",

  // ============================================
  // AUDIT FIELDS
  // ============================================
  "createdAt": {
    "$numberLong": "1760446141384"                  // Unix timestamp (milliseconds)
  },
  "createdBy": {
    "uuid": "39zgaBSCQZaW3NlIg6iLXQ",
    "name": "admin user",
    "email": "admin@harness.io"
  },
  "lastUpdatedAt": {
    "$numberLong": "1760446141384"
  },
  "lastUpdatedBy": {
    "uuid": "39zgaBSCQZaW3NlIg6iLXQ",
    "name": "admin user",
    "email": "admin@harness.io"
  }
}
```

---

## Understanding the Document Fields

### 1. Scope Fields (Critical for RBAC)

These three fields determine the **permission scope**:

| Field | Value | What It Means |
|-------|-------|---------------|
| `accountIdentifier` | `"IyrWsOn4RhGDDIDtYxz7YA"` | This entity belongs to account "IyrWsOn4..." |
| `orgIdentifier` | `"default"` | Within the "default" organization |
| `projectIdentifier` | `"test"` | Within the "test" project |

**Scope Hierarchy:**
```
Account: IyrWsOn4RhGDDIDtYxz7YA
    └── Organization: default
            └── Project: test
                    └── Component: BackendYesPlease
```

**For RBAC:** When checking permissions, IDP uses this scope:
- "Does user have permission on `BackendYesPlease` in scope `IyrWsOn4.../default/test`?"

**Scope Variations:**
```javascript
// Account-level entity
{
  "accountIdentifier": "IyrWsOn4...",
  "orgIdentifier": null,
  "projectIdentifier": null
}

// Org-level entity
{
  "accountIdentifier": "IyrWsOn4...",
  "orgIdentifier": "engineering",
  "projectIdentifier": null
}

// Project-level entity (like our example)
{
  "accountIdentifier": "IyrWsOn4...",
  "orgIdentifier": "default",
  "projectIdentifier": "test"
}
```

---

### 2. Entity Identification Fields

| Field | Value | Purpose |
|-------|-------|---------|
| `identifier` | `"BackendYesPlease"` | Human-readable ID, unique within scope |
| `name` | `"BackendYesPlease"` | Display name (usually same as identifier) |
| `uniqueId` | `"bTD0rJ3-T6-vjpKj_W-M2g"` | Globally unique ID across entire IDP |
| `parentUniqueId` | `"ul1hO1BJSDCjwvvh7cpRjA"` | Parent entity (if this entity is nested) |

**Key Distinction:**
- `identifier`: Unique within the **scope** (account/org/project)
  - Two projects can have components with same identifier
  - `test/BackendYesPlease` and `prod/BackendYesPlease` are different entities

- `uniqueId`: Unique **globally**
  - Guaranteed unique across all accounts, orgs, projects
  - Used for internal references and relationships

---

### 3. Entity Type Fields

| Field | Value | Purpose |
|-------|-------|---------|
| `kind` | `"component"` | Entity type (component, api, resource, system, etc.) |
| `type` | `"service"` | Subtype (for components: service, library, website, etc.) |
| `apiVersion` | `"harness.io/v1"` | Schema version |

**Kind Values (CORE_KINDS):**
- `component` - Software units (services, libraries, apps)
- `api` - Interfaces that components expose
- `resource` - Infrastructure (databases, queues, caches)
- `system` - Collection of related components
- `environment` - Deployment environments
- `environmentblueprint` - Environment templates

**Non-CORE_KINDS:**
- `user` - Individual people (read-only)
- `group` - Teams/departments (read-only or custom)
- `workflow` - Self-service templates

**Type Values (for components):**
- `service` - Microservice
- `library` - Shared library
- `website` - Web application
- `documentation` - Documentation site

---

### 4. Storage Type Fields

| Field | Value | Meaning |
|-------|-------|---------|
| `referenceType` | `"INLINE"` | Entity data stored directly in MongoDB |
| `_class` | `"io.harness.idp.catalog.entities.InlineCatalogEntity"` | Java class type |

**Storage Types:**

**INLINE** (like our example):
- Entity data stored **directly in MongoDB**
- Faster to read/write
- Managed entirely through IDP API
- Good for: Entities created through UI or API

**GIT_REFERENCED** (alternative):
```javascript
{
  "referenceType": "GIT_REFERENCED",
  "_class": "io.harness.idp.catalog.entities.GitReferencedCatalogEntity",
  "gitRepoUrl": "https://github.com/mycompany/idp-catalog",
  "gitBranch": "main",
  "gitFilePath": "components/payment-service.yaml"
}
```
- Entity YAML stored **in Git repository**
- IDP reads from Git (GitOps approach)
- Updates go through Git workflow (PR, review, merge)
- Good for: Production entities, version control, audit trail

---

### 5. Entity Details Fields

| Field | Value | Purpose |
|-------|-------|---------|
| `description` | `""` | Human-readable description |
| `owner` | `"group:account/_account_all_users"` | Reference to owning group or user |
| `tags` | `[]` | Array of tags for categorization and search |

**Owner Format:**
```
{type}:{scope}/{identifier}
```

Examples:
- `group:account/_account_all_users` - Account-level group (all users)
- `group:default/team-backend` - Group in default namespace
- `user:default/alice@company.com` - Individual user

**Tags Examples:**
```javascript
"tags": ["java", "spring-boot", "production", "payment"]
```

Used for:
- Search and filtering
- Grouping related entities
- Applying scorecards/policies

---

### 6. Specification (spec)

The `spec` object contains entity-specific details:

```javascript
"spec": {
  "lifecycle": "prod",                    // Required: Stage of the entity
  "system": "system:default/ecommerce",   // Optional: Which system it belongs to
  "providesApis": [                       // Optional: APIs this component exposes
    "api:default/payment-api"
  ],
  "consumesApis": [                       // Optional: APIs this component uses
    "api:default/user-api",
    "api:default/notification-api"
  ],
  "dependsOn": [                          // Optional: Dependencies
    "resource:default/payment-database",
    "resource:default/redis-cache"
  ]
}
```

**Common spec fields:**
- `lifecycle`: experimental, production (prod), deprecated
- `system`: Reference to parent system
- `domain`: Reference to business domain
- `providesApis`: APIs exposed by this component
- `consumesApis`: APIs consumed by this component
- `dependsOn`: Resources or other components this depends on

---

### 7. Relationships (relations)

Pre-computed relationships for fast queries:

```javascript
"relations": {
  "ownedBy": [
    "group:account/_account_all_users"
  ],
  "partOf": [
    "system:default/ecommerce"
  ],
  "providesApi": [
    "api:default/payment-api"
  ],
  "consumesApi": [
    "api:default/user-api"
  ],
  "dependsOn": [
    "resource:default/payment-database"
  ]
}
```

**Why relations exist:**
- **Performance**: Indexed for fast queries
- **Reverse lookup**: Can find "what depends on X" without scanning all entities
- **Pre-computed**: Updated when entity is saved, no runtime computation

**Relationship Types:**
- `ownedBy` / `ownerOf` - Ownership
- `partOf` / `hasPart` - Hierarchy (component → system → domain)
- `providesApi` / `apiProvidedBy` - API provision
- `consumesApi` / `apiConsumedBy` - API consumption
- `dependsOn` / `dependencyOf` - Dependencies

---

### 8. YAML Representation

The `yaml` field stores the entire entity as YAML:

```yaml
apiVersion: harness.io/v1
kind: Component
type: service
identifier: BackendYesPlease
name: BackendYesPlease
owner: group:account/_account_all_users
orgIdentifier: default
projectIdentifier: test
spec:
  lifecycle: prod
```

**Why YAML field exists:**
- **Export**: Easy to export entity to file
- **Git sync**: For GIT_REFERENCED entities, this is what gets committed
- **Backstage compatibility**: Backstage expects YAML format
- **Human-readable**: Easy to view full entity structure

---

### 9. Audit Fields

Track who created/updated and when:

```javascript
"createdAt": {
  "$numberLong": "1760446141384"        // Jan 13, 2026 (example timestamp)
},
"createdBy": {
  "uuid": "39zgaBSCQZaW3NlIg6iLXQ",
  "name": "admin user",
  "email": "admin@harness.io"
},
"lastUpdatedAt": {
  "$numberLong": "1760446141384"
},
"lastUpdatedBy": {
  "uuid": "39zgaBSCQZaW3NlIg6iLXQ",
  "name": "admin user",
  "email": "admin@harness.io"
}
```

**Used for:**
- Audit trail
- Finding who created/modified entities
- Tracking change history
- Compliance requirements

---

## Entity Identification & References

### Entity Reference Format

Every entity can be referenced using this format:

```
{kind}:{namespace}/{identifier}
```

**For our example:**
```
component:default/BackendYesPlease
```

**Breakdown:**
- `component` - The kind
- `default` - The namespace (usually "default" for Harness entities)
- `BackendYesPlease` - The identifier

**More examples:**
```
api:default/payment-api
resource:default/payment-database
group:default/team-backend
user:default/alice@company.com
system:default/ecommerce
```

**Why this format?**
- **Backstage standard** - IDP follows Backstage conventions
- **Human-readable** - Easy to understand
- **Unique** - Globally identifies an entity
- **Parseable** - Easy to extract kind and identifier

### Scope + Entity Reference = Full Identifier

In IDP, you often need **both** scope and entity reference:

```
Scope: accountId/orgId/projectId
Entity Reference: kind:namespace/identifier

Example: IyrWsOn4.../default/test:component:default/BackendYesPlease
```

**This full identifier uniquely identifies:**
- Which account
- Which organization
- Which project
- Which entity

---

## How RBAC Connects to Catalog Data

### The Critical Connection

**Key Insight:** Catalog documents in MongoDB do **NOT** store permission data!

```javascript
// catalogEntities document
{
  "identifier": "BackendYesPlease",
  "kind": "component",
  "accountIdentifier": "IyrWsOn4...",
  "orgIdentifier": "default",
  "projectIdentifier": "test"
  // NO permission data here!
}
```

**Permissions are stored in:** External **Access Control Service** (separate microservice)

### How They Connect

The connection happens through **extraction and mapping**:

**Step 1: Extract Scope from Entity**
```javascript
// From catalog document
accountId = entity.accountIdentifier     // "IyrWsOn4..."
orgId = entity.orgIdentifier             // "default"
projectId = entity.projectIdentifier     // "test"

// This becomes the ResourceScope for permission check
scope = "IyrWsOn4.../default/test"
```

**Step 2: Map Kind to Resource Type**

IDP has a mapping function that determines resource type based on entity kind:

```javascript
function getResourceType(kind) {
  switch (kind) {
    case "component":
    case "api":
    case "resource":
    case "system":
      return "IDP_CATALOG";           // Generic catalog resource type

    case "workflow":
      return "IDP_WORKFLOW";          // Workflow-specific resource type

    case "environment":
      return "IDP_ENVIRONMENT";       // Environment-specific resource type

    case "environmentblueprint":
      return "IDP_ENVIRONMENT_BLUEPRINT";

    default:
      return "IDP_CATALOG";
  }
}
```

**For our example:**
```javascript
kind = "component"
resourceType = "IDP_CATALOG"
```

**Step 3: Build Resource Identifier**
```javascript
resourceIdentifier = kind + ":" + identifier
// "component:BackendYesPlease"
```

**Step 4: Determine Permission**

Based on the operation:
- Creating/Updating → `idp_catalog_edit`
- Viewing/Reading → `idp_catalog_view`
- Deleting → `idp_catalog_delete`

### Permission Check Structure

IDP sends this to Access Control Service:

```javascript
{
  "principal": {
    "type": "USER",                           // USER, SERVICE, API_KEY
    "identifier": "alice@company.com"         // Who is making the request
  },
  "resourceScope": {
    "accountIdentifier": "IyrWsOn4...",      // From entity.accountIdentifier
    "orgIdentifier": "default",               // From entity.orgIdentifier
    "projectIdentifier": "test"               // From entity.projectIdentifier
  },
  "resource": {
    "type": "IDP_CATALOG",                    // Mapped from entity.kind
    "identifier": "component:BackendYesPlease" // From entity.kind + entity.identifier
  },
  "permission": "idp_catalog_edit"            // Based on operation (view/edit/delete)
}
```

**Access Control Service evaluates:**
1. Find user: `alice@company.com`
2. Find user's roles in scope `IyrWsOn4.../default/test`
3. Check if those roles have permission `idp_catalog_edit` on resource type `IDP_CATALOG`
4. Return: `{ "permitted": true }` or `{ "permitted": false }`

### Visualization

```
┌─────────────────────────────────────────────────────────────┐
│           MongoDB catalogEntities Document                   │
├─────────────────────────────────────────────────────────────┤
│ accountIdentifier: "IyrWsOn4..."        ───────┐            │
│ orgIdentifier: "default"                       │            │
│ projectIdentifier: "test"                      │            │
│ kind: "component"                   ───────┐   │            │
│ identifier: "BackendYesPlease"             │   │            │
└────────────────────────────────────────────┼───┼────────────┘
                                             │   │
                        ┌────────────────────┘   │
                        │  Map to                │ Extract scope
                        │  resource type         │
                        │                        │
                        ▼                        ▼
┌─────────────────────────────────────────────────────────────┐
│              Permission Check to Access Control             │
├─────────────────────────────────────────────────────────────┤
│ resource: {                                                 │
│   type: "IDP_CATALOG"  ◄── Mapped from "component"        │
│   identifier: "component:BackendYesPlease"                 │
│ }                                                           │
│ resourceScope: {                                            │
│   accountIdentifier: "IyrWsOn4..."  ◄── From catalog doc  │
│   orgIdentifier: "default"          ◄── From catalog doc  │
│   projectIdentifier: "test"         ◄── From catalog doc  │
│ }                                                           │
│ permission: "idp_catalog_edit"                             │
└─────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│           Access Control Service Response                    │
├─────────────────────────────────────────────────────────────┤
│ { "permitted": true }  ✅  ALLOW operation                  │
│ OR                                                          │
│ { "permitted": false } ❌  DENY operation (403 Forbidden)   │
└─────────────────────────────────────────────────────────────┘
```

---

## RBAC Validation Flow

### High-Level Flow

When a user tries to perform any operation on a catalog entity:

```
1. User Request (Create/Read/Update/Delete)
       ↓
2. IDP Service receives request
       ↓
3. Authenticate user (extract from JWT token)
       ↓
4. Query MongoDB to get/verify entity
       ↓
5. Extract scope info from entity (accountId, orgId, projectId)
       ↓
6. Map entity kind to resource type
       ↓
7. Build permission check request
       ↓
8. Call Access Control Service (gRPC)
       ↓
9. Access Control Service evaluates:
   - User's roles in the scope
   - Permissions granted to those roles
       ↓
10. Returns: ALLOW or DENY
       ↓
11. IDP Service:
    - If ALLOW: Proceed with operation
    - If DENY: Throw 403 Forbidden error
```

### Detailed Example: Update Component

Let's trace what happens when **Alice updates BackendYesPlease**:

**Step 1: API Request**
```http
PUT /v1/entities/IyrWsOn4RhGDDIDtYxz7YA?org=default&project=test
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "kind": "component",
  "identifier": "BackendYesPlease",
  "description": "Updated description for backend service"
}
```

**Step 2: Authentication**
- IDP extracts JWT token from `Authorization` header
- Validates token with NG Manager service
- Extracts user identity: `alice@company.com`
- Sets in security context

**Step 3: Query MongoDB**

IDP queries to verify entity exists:

```javascript
db.catalogEntities.findOne({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  orgIdentifier: "default",
  projectIdentifier: "test",
  kind: "component",
  identifier: "BackendYesPlease"
})
```

Returns:
```javascript
{
  "_id": ObjectId("68ee46bdf5d9414ddfb71f0a"),
  "accountIdentifier": "IyrWsOn4RhGDDIDtYxz7YA",
  "orgIdentifier": "default",
  "projectIdentifier": "test",
  "kind": "component",
  "identifier": "BackendYesPlease",
  // ... rest of document
}
```

**Step 4: Extract RBAC Context**

From the entity document:
```javascript
accountId = "IyrWsOn4RhGDDIDtYxz7YA"
orgId = "default"
projectId = "test"
kind = "component"
identifier = "BackendYesPlease"
```

**Step 5: Build Permission Check**

Map and construct:
```javascript
// Map kind to resource type
resourceType = "IDP_CATALOG"  // because kind = "component"

// Build resource identifier
resourceIdentifier = "component:BackendYesPlease"

// Determine permission (updating = edit)
permission = "idp_catalog_edit"

// Get current user
user = "alice@company.com"  // from security context
```

**Step 6: Call Access Control Service**

Send permission check request (gRPC):

```javascript
{
  "principal": {
    "type": "USER",
    "identifier": "alice@company.com"
  },
  "checks": [{
    "resourceScope": {
      "accountIdentifier": "IyrWsOn4RhGDDIDtYxz7YA",
      "orgIdentifier": "default",
      "projectIdentifier": "test"
    },
    "resourceType": "IDP_CATALOG",
    "resourceIdentifier": "component:BackendYesPlease",
    "permission": "idp_catalog_edit"
  }]
}
```

**Step 7: Access Control Service Evaluates**

Internal logic in Access Control Service:
```
1. Find user alice@company.com in account IyrWsOn4...

2. Find user's role assignments in scope IyrWsOn4.../default/test:
   - Result: alice has "Developer" role in project "test"

3. Check role "Developer" permissions:
   - Does "Developer" role have "idp_catalog_edit" permission on "IDP_CATALOG"?
   - Check role bindings and permission policies
   - Result: ✅ YES - Developer role includes idp_catalog_edit

4. Return response
```

Response:
```javascript
{
  "accessControlList": [{
    "permitted": true,
    "resourceScope": {
      "accountIdentifier": "IyrWsOn4RhGDDIDtYxz7YA",
      "orgIdentifier": "default",
      "projectIdentifier": "test"
    },
    "resourceType": "IDP_CATALOG",
    "resourceIdentifier": "component:BackendYesPlease",
    "permission": "idp_catalog_edit"
  }]
}
```

**Step 8: IDP Service Continues**

Since permission check returned `permitted: true`:

1. **Update entity in MongoDB:**
   ```javascript
   db.catalogEntities.updateOne(
     {
       accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
       kind: "component",
       identifier: "BackendYesPlease"
     },
     {
       $set: {
         description: "Updated description for backend service",
         lastUpdatedAt: NumberLong("1760550000000"),
         lastUpdatedBy: {
           email: "alice@company.com",
           name: "Alice",
           uuid: "alice-uuid"
         }
       }
     }
   )
   ```

2. **Sync to Backstage catalog** (if needed)

3. **Return success response**

**Step 9: API Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "SUCCESS",
  "data": {
    "identifier": "BackendYesPlease",
    "kind": "component",
    "description": "Updated description for backend service",
    "message": "Entity updated successfully"
  }
}
```

### What If Permission Denied?

If Alice didn't have permission (e.g., only has "Viewer" role):

**Step 7 (Access Control Service):**
```
1. Find user alice@company.com

2. Find roles in scope IyrWsOn4.../default/test:
   - Result: alice has "Viewer" role in project "test"

3. Check "Viewer" role permissions:
   - Does "Viewer" have "idp_catalog_edit"?
   - Result: ❌ NO - Viewer only has idp_catalog_view

4. Return response: { "permitted": false }
```

**Step 8 (IDP Service):**
```javascript
// Access Control returned permitted: false
// Throw exception
throw new AccessDeniedException("Missing Catalog edit Permission");
```

**Step 9 (API Response):**
```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "status": "FAILURE",
  "code": "ACCESS_DENIED",
  "message": "Missing Catalog edit Permission",
  "correlationId": "abc-123-xyz"
}
```

**Entity is NOT updated** - MongoDB remains unchanged.

---

## Service Layer Overview

### Architecture Layers

IDP follows a classic layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    REST API Layer                            │
│              (EntitiesApiImpl.java)                          │
│  - Handles HTTP requests/responses                           │
│  - Input validation                                          │
│  - Routes to service layer                                   │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   Service Layer                              │
│            (CatalogServiceImpl.java)                        │
│  - Business logic                                            │
│  - RBAC validation (calls helper)                           │
│  - Entity validation                                         │
│  - Orchestration (save + sync + version)                    │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼──────┐  ┌─────▼─────┐  ┌──────▼────────┐
│Helper Layer  │  │Repository │  │ External      │
│              │  │Layer      │  │ Services      │
│CatalogService│  │           │  │               │
│Helper.java   │  │Catalog    │  │AccessControl  │
│              │  │Entity     │  │Client         │
│- checkCrudRbac│  │Repository │  │               │
│- build refs  │  │           │  │- Permission   │
│- validate    │  │Spring Data│  │  checks       │
└──────┬───────┘  └─────┬─────┘  └──────┬────────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────┐
│              MongoDB (catalogEntities)                      │
└────────────────────────────────────────────────────────────┘
```

### Key Components

**1. REST API Layer**
- **File:** `EntitiesApiImpl.java`
- **Purpose:** Handle HTTP requests
- **Responsibilities:**
  - Parse request parameters
  - Call service layer methods
  - Format responses

**2. Service Layer**
- **File:** `CatalogServiceImpl.java`
- **Purpose:** Core business logic
- **Responsibilities:**
  - Validate entity data
  - **Call RBAC helper to check permissions**
  - Update MongoDB
  - Sync to Backstage
  - Orchestrate multiple operations

**3. Helper Layer**
- **File:** `CatalogServiceHelper.java`
- **Purpose:** RBAC and utilities
- **Key Methods:**
  - `checkCrudRbac()` - Check single entity permission
  - `checkEntityRefsPermission()` - Check bulk permissions
  - `validateEntity()` - Validate entity structure

**4. Repository Layer**
- **File:** `CatalogEntityRepository.java`
- **Purpose:** Database access
- **Type:** Spring Data MongoDB repository
- **Methods:** `findOne()`, `save()`, `delete()`, custom queries

**5. External Services**
- **AccessControlClient:** Permission checks (gRPC)
- **BackstageSyncService:** Sync to backstageCatalog collection

---

## Complete Request Example

Let's trace a complete flow: **Alice views BackendYesPlease**

### Request
```http
GET /v1/entities/IyrWsOn4RhGDDIDtYxz7YA/component/BackendYesPlease?org=default&project=test
Authorization: Bearer <alice-jwt-token>
```

### Flow

```
1. HTTP Request → Netty/Jetty server
       ↓
2. Authentication Filter
   ├─ Extract JWT from Authorization header
   ├─ Validate token
   ├─ Extract user: alice@company.com
   └─ Set SecurityContext
       ↓
3. EntitiesApiImpl.getEntity()
   ├─ accountId = "IyrWsOn4RhGDDIDtYxz7YA"
   ├─ orgId = "default"
   ├─ projectId = "test"
   ├─ kind = "component"
   ├─ identifier = "BackendYesPlease"
   └─ Call service layer
       ↓
4. CatalogServiceImpl.getEntity()
   │
   ├─ Step 4.1: Query MongoDB
   │   │
   │   └─ db.catalogEntities.findOne({
   │        accountIdentifier: "IyrWsOn4...",
   │        orgIdentifier: "default",
   │        projectIdentifier: "test",
   │        kind: "component",
   │        identifier: "BackendYesPlease"
   │      })
   │   │
   │   Returns: Entity document
   │
   ├─ Step 4.2: RBAC Check
   │   │
   │   └─ CatalogServiceHelper.checkCrudRbac(
   │        accountId: "IyrWsOn4...",
   │        orgId: "default",
   │        projectId: "test",
   │        kind: "component",
   │        resourceIdentifier: "component:BackendYesPlease",
   │        permission: "view"
   │      )
   │      │
   │      ├─ Map kind → resourceType: "IDP_CATALOG"
   │      ├─ Build permission: "idp_catalog_view"
   │      ├─ Get user from SecurityContext: alice@company.com
   │      │
   │      └─ AccessControlClient.checkForAccessOrThrow({
   │           principal: { type: USER, id: "alice@company.com" },
   │           scope: { account: "IyrWsOn4...", org: "default", project: "test" },
   │           resource: { type: "IDP_CATALOG", id: "component:BackendYesPlease" },
   │           permission: "idp_catalog_view"
   │         })
   │         │
   │         ↓ gRPC call
   │         │
   │      Access Control Service
   │      ├─ Find user alice@company.com
   │      ├─ Find roles in IyrWsOn4.../default/test
   │      │  Result: "Developer" role
   │      ├─ Check if Developer has idp_catalog_view
   │      │  Result: ✅ YES
   │      └─ Return: { permitted: true }
   │         │
   │         ↓
   │      Permission check passed (no exception thrown)
   │
   └─ Step 4.3: Return entity
       └─ Convert to response DTO
       └─ Return to API layer
       ↓
5. EntitiesApiImpl returns HTTP response
       ↓
6. HTTP Response
   Status: 200 OK
   Body: {
     "status": "SUCCESS",
     "data": {
       "identifier": "BackendYesPlease",
       "kind": "component",
       "name": "BackendYesPlease",
       "owner": "group:account/_account_all_users",
       "description": "",
       "spec": { "lifecycle": "prod" }
     }
   }
```

### Database Query Executed

```javascript
db.catalogEntities.findOne({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  orgIdentifier: "default",
  projectIdentifier: "test",
  kind: "component",
  identifier: "BackendYesPlease"
})
```

### RBAC gRPC Call

```javascript
// Request to Access Control Service
{
  principal: {
    type: "USER",
    identifier: "alice@company.com"
  },
  checks: [{
    resourceScope: {
      accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
      orgIdentifier: "default",
      projectIdentifier: "test"
    },
    resourceType: "IDP_CATALOG",
    resourceIdentifier: "component:BackendYesPlease",
    permission: "idp_catalog_view"
  }]
}

// Response from Access Control Service
{
  accessControlList: [{
    permitted: true
  }]
}
```

---

## Querying Catalog Data

### Common Query Patterns

**1. Find Entity by Identifier**
```javascript
db.catalogEntities.findOne({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  kind: "component",
  identifier: "BackendYesPlease"
})
```

**2. Find All Components in a Project**
```javascript
db.catalogEntities.find({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  orgIdentifier: "default",
  projectIdentifier: "test",
  kind: "component"
})
```

**3. Find Entities by Owner**
```javascript
db.catalogEntities.find({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  owner: "group:account/_account_all_users"
})
```

**4. Find Entities by Tag**
```javascript
db.catalogEntities.find({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  tags: "production"  // Find entities with "production" tag
})
```

**5. Find Production Components**
```javascript
db.catalogEntities.find({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  kind: "component",
  "spec.lifecycle": "prod"
})
```

**6. Find Components Owned by Specific Group**
```javascript
db.catalogEntities.find({
  accountIdentifier: "IyrWsOn4RhGDDIDtYxz7YA",
  kind: "component",
  "relations.ownedBy": "group:default/team-backend"
})
```

### Indexes for Performance

The collection has indexes on:
```javascript
// Unique index
{ accountIdentifier: 1, kind: 1, identifier: 1 }

// Scope index
{ accountIdentifier: 1, orgIdentifier: 1, projectIdentifier: 1 }

// Owner index
{ owner: 1 }

// Tags index
{ tags: 1 }

// Relations index (for fast relationship queries)
{ "relations.ownedBy": 1 }
{ "relations.partOf": 1 }
```

---

## Summary

### Key Takeaways

**1. Catalog Document Structure**
- Stored in MongoDB collection: `catalogEntities`
- Key fields: accountIdentifier, orgIdentifier, projectIdentifier (scope)
- Identification: kind, identifier, name, uniqueId
- Details: description, owner, tags, spec, relations

**2. RBAC Connection**
- Entities **don't store permissions** in MongoDB
- Scope extracted from entity (accountIdentifier/orgIdentifier/projectIdentifier)
- Kind mapped to resource type ("component" → "IDP_CATALOG")
- Permission check sent to external Access Control Service

**3. Permission Check Flow**
```
Entity in MongoDB → Extract scope → Map kind to resource type →
Build permission check → Call Access Control Service →
ALLOW or DENY → Proceed or throw 403
```

**4. Service Architecture**
```
API Layer → Service Layer → Helper (RBAC) → Repository → MongoDB
                    ↓
            Access Control Service (gRPC)
```

**5. Entity References**
- Format: `{kind}:{namespace}/{identifier}`
- Example: `component:default/BackendYesPlease`
- Used in relationships, queries, and RBAC checks

### What You Now Understand

✅ How catalog entities are stored in MongoDB
✅ What each field in the document means
✅ How RBAC extracts context from entity data
✅ How permission checks work (without storing permissions in entities)
✅ The service layer flow from API to database
✅ How to query catalog data

### Ready for Next Steps

You can now:
- Navigate the codebase with understanding of data structure
- Understand RBAC implementation decisions
- Make changes to CORE_KINDS handling
- Query catalog data effectively
- Debug permission issues

For code-level implementation details, see:
- `IDP_RBAC_IMPLEMENTATION.md` - Full RBAC code
- `IDP_SERVICE_ARCHITECTURE.md` - Complete architecture

---

**You're now at "1 level deep" - understanding data and RBAC without getting lost in code!** 🚀

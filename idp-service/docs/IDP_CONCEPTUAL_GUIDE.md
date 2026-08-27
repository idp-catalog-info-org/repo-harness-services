# IDP Service - Conceptual Guide for Beginners

**For:** New contributors to the IDP codebase
**Focus:** Business logic, entity relationships, and theoretical understanding
**Level:** Beginner-friendly, minimal code

---

## Table of Contents

1. [What is IDP? (The Big Picture)](#what-is-idp-the-big-picture)
2. [Understanding Components - A Real Example](#understanding-components---a-real-example)
3. [Entity Relationships](#entity-relationships)
4. [Database Structure (Conceptual)](#database-structure-conceptual)
5. [Access Control Explained](#access-control-explained)
6. [Real-World Scenarios](#real-world-scenarios)
7. [Putting It All Together](#putting-it-all-together)

---

## What is IDP? (The Big Picture)

### The Problem

Imagine you're a developer at a large company with:
- 500 microservices
- 50 teams
- Multiple cloud providers
- Dozens of tools (Jira, GitHub, Jenkins, Datadog, etc.)

**Questions you face daily:**
- "Where is the documentation for the Payment API?"
- "Who owns the Authentication Service?"
- "What APIs does my service depend on?"
- "How do I create a new microservice?"
- "Is this service production-ready?"
- "What's the status of our security scans?"

**IDP solves this** by creating a **central developer portal** where all this information lives in one place.

### What is Backstage?

**Backstage** (by Spotify) is an open-source platform for building developer portals. Think of it as:
- A **website** where developers can see all services, APIs, and resources
- A **software catalog** (like a phone book for your infrastructure)
- A **self-service platform** (templates to create new services quickly)

### What is Harness IDP Service?

**Harness IDP Service** is the **backend** that powers Backstage for Harness customers. It:
- Stores all the catalog data (services, APIs, teams, etc.)
- Manages permissions (who can see/edit what)
- Integrates with Harness platform (CI/CD, security, etc.)
- Syncs data between Backstage (frontend) and Harness

```
┌─────────────────────────────────────────────────────┐
│              Developer's Browser                     │
│                                                      │
│  ┌────────────────────────────────────────────┐   │
│  │        Backstage UI (Frontend)             │   │
│  │  - Service catalog                         │   │
│  │  - API documentation                       │   │
│  │  - Create new service                      │   │
│  │  - Team information                        │   │
│  └────────────────┬───────────────────────────┘   │
└────────────────────┼──────────────────────────────┘
                     │ HTTP/REST API
┌────────────────────▼──────────────────────────────┐
│         IDP Service (Backend)                      │
│  - Stores catalog data                            │
│  - Enforces permissions                           │
│  - Integrates with Harness                        │
└────────────────────┬──────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
┌───────▼─────┐ ┌───▼──────┐ ┌──▼────────┐
│  MongoDB    │ │ Harness  │ │  External │
│  (Catalog)  │ │ Platform │ │  Tools    │
│             │ │          │ │ (GitHub,  │
│             │ │          │ │  Jira)    │
└─────────────┘ └──────────┘ └───────────┘
```

---

## Understanding Components - A Real Example

### What is a Component?

A **Component** is the most common entity in IDP. It represents a **software unit** like:
- A microservice (e.g., "payment-service")
- A library (e.g., "auth-library")
- A website (e.g., "customer-portal")
- A mobile app (e.g., "ios-app")

### Real Example: Payment Service

Let's say your company has a **Payment Service**. Here's what its catalog entry looks like:

```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: payment-service
  title: Payment Processing Service
  description: Handles all payment processing for customer orders
  tags:
    - java
    - spring-boot
    - payments
  annotations:
    github.com/project-slug: mycompany/payment-service
    jira/project-key: PAY
spec:
  type: service
  lifecycle: production
  owner: team-payments
  system: e-commerce-platform
  providesApis:
    - payment-api
  consumesApis:
    - user-api
    - notification-api
  dependsOn:
    - resource:payment-database
    - resource:redis-cache
```

### Breaking It Down

| Field | What It Means | Example |
|-------|---------------|---------|
| **kind** | Type of entity | Component (a service) |
| **metadata.name** | Unique identifier | payment-service |
| **metadata.title** | Human-readable name | "Payment Processing Service" |
| **metadata.description** | What it does | "Handles all payment..." |
| **metadata.tags** | Categories/labels | java, spring-boot, payments |
| **spec.type** | Subtype | service, library, website |
| **spec.lifecycle** | Stage | experimental, production, deprecated |
| **spec.owner** | Who owns it | team-payments (a Group) |
| **spec.system** | What system it belongs to | e-commerce-platform (a System) |
| **spec.providesApis** | APIs it exposes | payment-api (an API entity) |
| **spec.consumesApis** | APIs it uses | user-api, notification-api |
| **spec.dependsOn** | Dependencies | payment-database, redis-cache |

### In the Backstage UI

When a developer looks at "payment-service" in Backstage, they see:

```
┌─────────────────────────────────────────────────────────┐
│ 💳 Payment Processing Service                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ Overview                                                 │
│ ├─ Description: Handles all payment processing...       │
│ ├─ Owner: Team Payments                                 │
│ ├─ Lifecycle: Production                                │
│ └─ System: E-Commerce Platform                          │
│                                                          │
│ Relations                                                │
│ ├─ Provides APIs: Payment API                           │
│ ├─ Consumes APIs: User API, Notification API            │
│ └─ Depends On: Payment Database, Redis Cache            │
│                                                          │
│ Integrations                                             │
│ ├─ 📦 GitHub: mycompany/payment-service                 │
│ ├─ 🐛 Jira: PAY project                                 │
│ ├─ 🔍 SonarQube: Code quality score: A                  │
│ ├─ 📊 Datadog: CPU: 45%, Memory: 2.1GB                  │
│ └─ ✅ CI/CD: Last deploy: 2 hours ago (Success)         │
│                                                          │
│ Documentation                                            │
│ └─ README, API Docs, Architecture Diagrams              │
└─────────────────────────────────────────────────────────┘
```

---

## Entity Relationships

### The 7 Core Entity Types

IDP has different types of entities that relate to each other:

| Entity Type | What It Represents | Example |
|-------------|-------------------|---------|
| **Component** | A software unit (service, library, app) | payment-service |
| **API** | An interface that components expose | payment-api (REST API) |
| **Resource** | Infrastructure (database, queue, etc.) | payment-database |
| **System** | A collection of related components | e-commerce-platform |
| **Domain** | Business domain grouping | payments-domain |
| **User** | Individual person | john.doe@company.com |
| **Group** | Team or department | team-payments |

### How They Connect - Visual Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    DOMAIN: Payments                         │
│  (Business area: Everything related to payments)            │
│                                                              │
│  ┌────────────────────────────────────────────────────┐   │
│  │          SYSTEM: E-Commerce Platform               │   │
│  │  (Technical grouping of related services)           │   │
│  │                                                      │   │
│  │  ┌──────────────┐         ┌──────────────┐        │   │
│  │  │  COMPONENT   │         │  COMPONENT   │        │   │
│  │  │              │         │              │        │   │
│  │  │  Payment     │─────────│  Order       │        │   │
│  │  │  Service     │ uses    │  Service     │        │   │
│  │  │              │         │              │        │   │
│  │  └──────┬───────┘         └──────────────┘        │   │
│  │         │                                           │   │
│  │         │ provides                                  │   │
│  │         │                                           │   │
│  │  ┌──────▼───────┐                                  │   │
│  │  │     API      │                                  │   │
│  │  │              │                                  │   │
│  │  │  Payment API │                                  │   │
│  │  │  (REST)      │                                  │   │
│  │  └──────┬───────┘                                  │   │
│  │         │                                           │   │
│  │         │ uses                                      │   │
│  │         │                                           │   │
│  │  ┌──────▼────────┐                                 │   │
│  │  │   RESOURCE    │                                 │   │
│  │  │               │                                 │   │
│  │  │  Payment DB   │                                 │   │
│  │  │  (PostgreSQL) │                                 │   │
│  │  └───────────────┘                                 │   │
│  └────────────────────────────────────────────────────┘   │
│                                                              │
│  Owned by:                                                  │
│  ┌──────────────┐                                          │
│  │    GROUP     │    Has members:                          │
│  │              │    ┌──────────┐  ┌──────────┐           │
│  │ Team Payments│───▶│   USER   │  │   USER   │           │
│  │              │    │  Alice   │  │   Bob    │           │
│  └──────────────┘    └──────────┘  └──────────┘           │
└─────────────────────────────────────────────────────────────┘
```

### Relationship Types Explained

**1. Owner Relationships**
- Every Component, API, Resource has an **owner** (a Group or User)
- Example: `payment-service` is owned by `team-payments`
- This determines who is responsible for maintaining it

**2. Provides/Consumes APIs**
- Components can **provide** APIs (they expose endpoints)
- Components can **consume** APIs (they call other services)
- Example: `payment-service` provides `payment-api` and consumes `user-api`

**3. Depends On**
- Components depend on Resources (databases, caches, etc.)
- Example: `payment-service` depends on `payment-database`

**4. Part Of**
- Components are part of Systems
- Systems are part of Domains
- Example: `payment-service` → part of → `e-commerce-platform` → part of → `payments-domain`

**5. Member Of**
- Users are members of Groups
- Example: Alice and Bob are members of `team-payments`

### Real-World Example: Payment Flow

Let's trace a payment through the catalog:

```
Customer makes payment
       ↓
1. ORDER-SERVICE (Component)
   - Type: service
   - Owner: team-orders
   - Consumes: payment-api
       ↓
2. PAYMENT-API (API)
   - Type: REST API
   - Provided by: payment-service
       ↓
3. PAYMENT-SERVICE (Component)
   - Type: service
   - Owner: team-payments
   - Depends on: payment-database
   - Consumes: notification-api
       ↓
4. PAYMENT-DATABASE (Resource)
   - Type: PostgreSQL database
   - Owner: team-payments
       ↓
Payment processed!
       ↓
5. NOTIFICATION-API (API)
   - Sends confirmation email
   - Provided by: notification-service
```

In IDP, you can **visualize this entire flow** and see:
- Who owns each part
- What the dependencies are
- Status of each service (healthy, down, etc.)
- Recent deployments
- Security scan results

---

## Database Structure (Conceptual)

### MongoDB Collections

IDP stores everything in MongoDB. Think of it like tables in a database:

```
MongoDB Database: idp-harness
│
├── catalogEntities           (Your catalog entries)
│   ├── component:payment-service
│   ├── component:order-service
│   ├── api:payment-api
│   ├── resource:payment-database
│   ├── system:e-commerce-platform
│   ├── group:team-payments
│   └── user:alice@company.com
│
├── backstageCatalog          (Synced to Backstage)
│   └── (mirrors catalogEntities)
│
├── scorecards                (Quality standards)
│   └── scorecard:security-standards
│
├── checks                    (Individual quality checks)
│   ├── check:has-readme
│   ├── check:has-tests
│   └── check:no-critical-vulnerabilities
│
├── scorecardStats            (Computed scores)
│   └── payment-service → score: 85/100
│
├── homePageLayouts           (User's homepage config)
│   └── alice@company.com → layout preferences
│
├── pluginInfo                (Plugin configurations)
│   ├── github-plugin
│   ├── jira-plugin
│   └── datadog-plugin
│
└── backstageEnvVariables     (Configuration)
    ├── GITHUB_TOKEN
    └── JIRA_API_KEY
```

### How a Component is Stored

Let's see how `payment-service` actually looks in the database:

**Collection:** `catalogEntities`

**Document:**
```json
{
  "_id": "abc123xyz",
  "accountId": "mycompany",
  "orgIdentifier": "engineering",
  "projectIdentifier": "backend-services",
  "kind": "component",
  "metadata": {
    "name": "payment-service",
    "title": "Payment Processing Service",
    "description": "Handles all payment processing",
    "namespace": "default",
    "uid": "abc123xyz",
    "tags": ["java", "spring-boot", "payments"],
    "annotations": {
      "github.com/project-slug": "mycompany/payment-service",
      "jira/project-key": "PAY",
      "backstage.io/source-location": "url:https://github.com/..."
    }
  },
  "spec": {
    "type": "service",
    "lifecycle": "production",
    "owner": "group:default/team-payments",
    "system": "system:default/e-commerce-platform",
    "providesApis": ["api:default/payment-api"],
    "consumesApis": [
      "api:default/user-api",
      "api:default/notification-api"
    ],
    "dependsOn": [
      "resource:default/payment-database",
      "resource:default/redis-cache"
    ]
  },
  "relations": [
    {
      "type": "ownedBy",
      "targetRef": "group:default/team-payments"
    },
    {
      "type": "partOf",
      "targetRef": "system:default/e-commerce-platform"
    },
    {
      "type": "providesApi",
      "targetRef": "api:default/payment-api"
    },
    {
      "type": "consumesApi",
      "targetRef": "api:default/user-api"
    },
    {
      "type": "dependsOn",
      "targetRef": "resource:default/payment-database"
    }
  ],
  "entityType": "INLINE",
  "createdAt": 1699123456789,
  "lastModifiedAt": 1699987654321,
  "createdBy": "alice@company.com"
}
```

### Key Fields Explained

| Field | What It Does |
|-------|--------------|
| **accountId** | Which Harness account owns this |
| **orgIdentifier** | Which organization (optional) |
| **projectIdentifier** | Which project (optional) |
| **kind** | Type of entity (component, api, resource, etc.) |
| **metadata** | Descriptive information (name, description, tags) |
| **spec** | Technical details (owner, lifecycle, relationships) |
| **relations** | Computed relationships for quick lookup |
| **entityType** | INLINE (stored in DB) or GIT_REFERENCED (in Git) |

### Entity Reference Format

Entities reference each other using this format:
```
{kind}:{namespace}/{name}
```

Examples:
- `component:default/payment-service`
- `api:default/payment-api`
- `group:default/team-payments`
- `resource:default/payment-database`

This is like a **URL** for entities - a unique way to identify them.

---

## Access Control Explained

### The Problem

Not everyone should see or edit everything:
- Team A shouldn't be able to delete Team B's services
- External contractors shouldn't see production secrets
- Managers should see all services but only edit their team's services

### How Access Control Works

Think of it like Google Drive permissions:
- **Account Level**: Company-wide access
- **Organization Level**: Department access (Engineering, Sales, etc.)
- **Project Level**: Team access (Backend Team, Frontend Team, etc.)

### Hierarchy

```
🏢 Company (Account: mycompany)
    │
    ├── 🏛️ Engineering (Organization)
    │   │
    │   ├── 📁 Backend Services (Project)
    │   │   ├── payment-service ← Alice can EDIT
    │   │   └── order-service   ← Alice can EDIT
    │   │
    │   └── 📁 Frontend Services (Project)
    │       ├── customer-portal ← Alice can VIEW only
    │       └── admin-dashboard ← Alice can VIEW only
    │
    └── 🏛️ Sales (Organization)
        └── 📁 CRM Tools (Project)
            └── salesforce-integration ← Alice CANNOT see
```

### Permission Model

**For CORE_KINDS (Components, APIs, Resources, Systems):**

| Permission | What You Can Do |
|------------|-----------------|
| **idp_catalog_view** | See the entity, read documentation |
| **idp_catalog_edit** | Create new entities, update existing ones |
| **idp_catalog_delete** | Delete entities |

**Example Roles:**

**Developer (Alice):**
- `idp_catalog_view` on **Account** level → Can see everything
- `idp_catalog_edit` on **Project: Backend Services** → Can edit backend services
- `idp_catalog_delete` on **nothing** → Cannot delete anything

**Team Lead (Bob):**
- `idp_catalog_view` on **Account** level
- `idp_catalog_edit` on **Organization: Engineering** → Can edit all engineering services
- `idp_catalog_delete` on **Organization: Engineering** → Can delete engineering services

**Admin (Charlie):**
- `idp_catalog_view` on **Account** level
- `idp_catalog_edit` on **Account** level → Can edit everything
- `idp_catalog_delete` on **Account** level → Can delete everything

### Real-World Scenario

**Scenario:** Alice wants to update the `payment-service` description

```
1. Alice clicks "Edit" on payment-service
       ↓
2. IDP Service receives request:
   - Who: Alice (alice@company.com)
   - What: Update component
   - Which: payment-service
   - Where: Account/Engineering/Backend Services
       ↓
3. IDP checks with Access Control Service:
   "Does Alice have 'idp_catalog_edit' permission on
    'component:payment-service' in scope
    'mycompany/engineering/backend-services'?"
       ↓
4. Access Control Service responds:
   ✅ YES - Alice has "Developer" role in Backend Services
   which grants idp_catalog_edit permission
       ↓
5. IDP allows the update
       ↓
6. Description updated in database
       ↓
7. Backstage UI refreshes and shows new description
```

**Scenario:** Alice tries to delete `customer-portal` (Frontend project)

```
1. Alice clicks "Delete" on customer-portal
       ↓
2. IDP Service receives request:
   - Who: Alice
   - What: Delete component
   - Which: customer-portal
   - Where: Account/Engineering/Frontend Services
       ↓
3. IDP checks with Access Control Service:
   "Does Alice have 'idp_catalog_delete' permission on
    'component:customer-portal' in scope
    'mycompany/engineering/frontend-services'?"
       ↓
4. Access Control Service responds:
   ❌ NO - Alice doesn't have delete permission
       ↓
5. IDP rejects the request
       ↓
6. Alice sees: "Error: You don't have permission to delete
   this component. Contact your administrator."
```

### Why Different Resource Types?

Remember from earlier - some entities use different resource types:

| Entity Kind | Resource Type | Why? |
|-------------|---------------|------|
| component, api, resource, system | `IDP_CATALOG` | General catalog entities - shared permissions |
| workflow | `IDP_WORKFLOW` | Self-service templates need separate permissions (creating new services is sensitive!) |
| environment | `IDP_ENVIRONMENT` | Environment configs need separate control |

**Example:**
- Alice has `idp_catalog_edit` → Can create/edit components
- Alice does NOT have `idp_workflow_edit` → Cannot create self-service templates
- This separation allows fine-grained control

---

## Real-World Scenarios

### Scenario 1: New Developer Onboarding

**Problem:** New developer Bob joins Team Payments. He needs to understand the payment service.

**Solution via IDP:**

1. Bob logs into Backstage
2. Searches for "payment"
3. Finds `payment-service` component
4. Sees:
   - 📖 **Documentation**: README, architecture diagrams
   - 👥 **Owner**: Team Payments (his team!)
   - 🔗 **Relations**: What APIs it provides/consumes
   - 📦 **GitHub**: Link to source code
   - 🐛 **Jira**: Related tickets
   - 📊 **Metrics**: Current CPU, memory, error rate
   - ✅ **Quality Score**: 85/100 (good!)
   - 🚀 **Recent Deploys**: Last deploy 2 hours ago

**Access Control:**
- Bob has `idp_catalog_view` at Account level → Can see everything
- Bob has `idp_catalog_edit` in Backend Services project → Can update docs
- Bob does NOT have `idp_catalog_delete` → Cannot accidentally delete anything

---

### Scenario 2: Creating a New Service

**Problem:** Alice needs to create a new "fraud-detection-service"

**Solution via IDP:**

1. Alice goes to "Create" section in Backstage
2. Selects "Spring Boot Service" template (a workflow)
3. Fills form:
   - Name: fraud-detection-service
   - Description: Detects fraudulent transactions
   - Owner: team-payments
   - Language: Java
   - Database: PostgreSQL
4. Clicks "Create"
5. IDP workflow runs:
   - Creates GitHub repository
   - Generates boilerplate code
   - Creates database
   - Sets up CI/CD pipeline
   - **Registers component in catalog**
6. Done! New service is live and visible in IDP

**Access Control:**
- Alice needs `idp_workflow_edit` permission to use templates
- Once created, the component inherits permissions from the project
- Team Payments can edit/delete it

---

### Scenario 3: Quality Governance with Scorecards

**Problem:** Company wants to ensure all production services meet security standards

**Solution via IDP:**

1. Admin creates a **Scorecard** called "Security Standards"
2. Defines **Checks**:
   - ✅ Has README documentation
   - ✅ Has automated tests (coverage > 80%)
   - ✅ No critical vulnerabilities (from SonarQube)
   - ✅ Has valid owner
   - ✅ Deployed in last 30 days
3. Assigns scorecard to all `component` entities with `lifecycle: production`
4. IDP automatically computes scores for all components

**In the UI:**

```
┌──────────────────────────────────────────────────┐
│ 🏆 Security Standards Scorecard                 │
├──────────────────────────────────────────────────┤
│                                                   │
│ payment-service         ⭐ 90/100                │
│ ├─ ✅ Has README                                 │
│ ├─ ✅ Test coverage: 85%                         │
│ ├─ ✅ No critical vulnerabilities                │
│ ├─ ✅ Owner: team-payments                       │
│ └─ ✅ Last deploy: 2 hours ago                   │
│                                                   │
│ legacy-service          ⚠️  60/100               │
│ ├─ ✅ Has README                                 │
│ ├─ ❌ Test coverage: 45% (below 80%)             │
│ ├─ ⚠️  2 critical vulnerabilities found          │
│ ├─ ✅ Owner: team-legacy                         │
│ └─ ⚠️  Last deploy: 45 days ago                  │
└──────────────────────────────────────────────────┘
```

**Access Control:**
- Creating scorecards requires `idp_scorecard_edit` permission (admins only)
- Viewing scores requires `idp_catalog_view` (all developers)
- This ensures governance without blocking developers

---

### Scenario 4: Finding Dependencies

**Problem:** Payment database needs maintenance. Who will be affected?

**Solution via IDP:**

1. Search for `payment-database` (a Resource)
2. Click on it
3. See **"Dependency Graph"**:

```
payment-database (Resource)
    ↑ dependsOn
    │
    ├── payment-service (Component)
    │       ↑ consumesApi
    │       │
    │       ├── order-service
    │       ├── subscription-service
    │       └── refund-service
    │
    └── analytics-service (Component)
            ↑ consumesApi
            │
            └── reporting-dashboard
```

4. Now Alice knows:
   - **5 services** depend on this database
   - Need to notify **3 teams**: Payments, Orders, Analytics
   - Should schedule maintenance during low-traffic hours

**Access Control:**
- Alice can see this because she has `idp_catalog_view`
- If she only had project-level permissions, she'd only see services in her project

---

## Putting It All Together

### The Developer Experience

**Morning:** Alice logs into IDP
- **Homepage** shows: Recently viewed services, starred items, team's services, Jira tickets
- **Search** for anything: services, APIs, people, documentation
- **Browse** by tags: `#payments`, `#production`, `#java`

**Task:** Update payment-service documentation
1. Search "payment-service"
2. Click component
3. See current README
4. Click "Edit" → IDP checks permissions (✅ has edit permission)
5. Update docs
6. Save → Backstage refreshes

**Task:** Check service health
1. On payment-service page
2. See **integrated metrics**:
   - Datadog: CPU, Memory, Request rate
   - GitHub: Recent commits, PRs
   - Jira: Open bugs
   - CI/CD: Last deploy status
   - SonarQube: Code quality
   - Scorecard: 90/100 (excellent!)

**Task:** Find who owns user-service (it's down!)
1. Search "user-service"
2. See owner: `team-identity`
3. Click team
4. See members: Dave, Emma, Frank
5. Slack them!

### Behind the Scenes (What IDP Service Does)

When Alice searches "payment-service":

```
1. Backstage UI sends request to IDP Service API
       ↓
2. IDP Service:
   - Authenticates Alice (JWT token)
   - Extracts her account/org/project
   - Queries MongoDB for matching components
       ↓
3. Permission Filtering:
   - Checks: Does Alice have "idp_catalog_view" for each result?
   - Filters out: Services she doesn't have access to
       ↓
4. Enrichment:
   - Fetches related entities (owner, system, APIs)
   - Fetches scorecard scores
   - Fetches plugin data (GitHub, Jira, etc.)
       ↓
5. Returns filtered, enriched data to UI
       ↓
6. Backstage renders the results
```

### The Data Flow

```
Developer Action (Backstage UI)
        ↓
IDP Service API
        ↓
    ┌───┴────┐
    │        │
MongoDB  Access Control Service
    │        │
    └───┬────┘
        ↓
External Integrations
(GitHub, Jira, Datadog, etc.)
        ↓
Enriched Data
        ↓
Back to Backstage UI
        ↓
Developer sees result
```

---

## Key Takeaways

### 1. IDP is a Developer Portal
- **Single source of truth** for all services, APIs, teams
- **Integrates** with all your tools (GitHub, Jira, CI/CD, monitoring)
- **Self-service** platform for creating new services

### 2. Components are Software Units
- Represent services, libraries, apps
- Have **metadata** (name, description, tags)
- Have **relationships** (owner, dependencies, APIs)
- Have **integrations** (GitHub, Jira, monitoring)

### 3. Entities Form a Graph
- Components → provide APIs
- Components → consume APIs
- Components → depend on Resources
- Components → part of Systems → part of Domains
- Components → owned by Groups → contain Users

### 4. Everything is in MongoDB
- `catalogEntities` collection stores all entities
- Each entity has: kind, metadata, spec, relations
- Entity references: `{kind}:{namespace}/{name}`

### 5. Access Control is Hierarchical
- **Account** > **Organization** > **Project**
- Permissions: view, edit, delete
- Different resource types: IDP_CATALOG, IDP_WORKFLOW, etc.
- Checked on every API call

### 6. CORE_KINDS are Fully Manageable
- `api`, `component`, `resource`, `system`, `environment*`
- Can be created, updated, deleted via API
- Have full RBAC support
- Unlike `user`/`group` which are read-only

---

## Next Steps

Now that you understand IDP conceptually:

1. **Explore the UI**: Log into your Backstage instance and click around
2. **Look at entities**: Find a component and see its relationships
3. **Check permissions**: Try editing something - see where you have access
4. **Read the code**: Now that you know what's happening, the code will make more sense!

For code-level details, refer to:
- `IDP_SERVICE_ARCHITECTURE.md` - Full technical documentation
- `IDP_RBAC_IMPLEMENTATION.md` - RBAC implementation details

---

**Questions to explore:**
- What components exist in your IDP?
- Who owns them?
- What are their quality scores?
- What dependencies do they have?
- What permissions do you have?

Happy exploring! 🚀

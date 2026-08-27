# IDP Service - Architecture & Developer Guide

**Version:** 1.18.0
**Last Updated:** November 5, 2025
**Type:** Microservice
**Primary Framework:** Dropwizard + Guice

---

## Table of Contents

1. [Overview](#overview)
2. [What is IDP?](#what-is-idp)
3. [Architecture](#architecture)
4. [Technology Stack](#technology-stack)
5. [Code Structure](#code-structure)
6. [Major Components](#major-components)
7. [Data Layer](#data-layer)
8. [API Layer](#api-layer)
9. [Business Logic & Workflows](#business-logic--workflows)
10. [Integration Points](#integration-points)
11. [Configuration](#configuration)
12. [Deployment](#deployment)
13. [Development Guide](#development-guide)
14. [Testing](#testing)
15. [Key Design Patterns](#key-design-patterns)
16. [Contributing](#contributing)

---

## Overview

The **IDP Service** is Harness's Internal Developer Portal built on top of [Backstage](https://backstage.io/) (Spotify's open-source developer portal platform). It serves as the central hub for developer experience within the Harness ecosystem, providing a unified interface for service catalog, quality governance, self-service provisioning, and developer tools integration.

### Key Metrics
- **36** major packages
- **10,409** lines in OpenAPI specification
- **80+** pre-built plugin integrations
- **229** test files
- **20+** background job iterators
- **76** database migration classes

---

## What is IDP?

**IDP = Internal Developer Portal**

### Problems It Solves

1. **Developer Experience Fragmentation**
   - Centralizes all developer tools, documentation, services, and resources in one place
   - Provides a single pane of glass for developers

2. **Service Discovery**
   - Helps developers find and understand services, APIs, and components across the organization
   - Maintains relationships between entities

3. **Software Quality Governance**
   - Implements scorecards to measure and track software quality standards
   - Provides visibility into technical debt and compliance

4. **Self-Service Provisioning**
   - Enables developers to create new services, resources, and projects through templates
   - Reduces friction in getting started with new projects

5. **Catalog Management**
   - Maintains a comprehensive software catalog with metadata
   - Tracks components, APIs, resources, systems, domains, templates, users, and groups

6. **Onboarding**
   - Streamlines the process of onboarding services into the portal
   - Automated YAML generation and Git integration

7. **Plugin Ecosystem**
   - Integrates with 40+ third-party tools (GitHub, Jira, Jenkins, Datadog, etc.)
   - Extensible plugin architecture

---

## Architecture

### Architecture Pattern

**Microservice Architecture** with:
- RESTful API layer (Jersey/JAX-RS)
- Event-driven architecture (Redis/Kafka)
- Integration with multiple Harness services
- Backstage as the frontend platform

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Backstage Frontend                       │
│              (React/TypeScript - Separate)                  │
└─────────────────┬───────────────────────────────────────────┘
                  │ HTTP/REST
┌─────────────────▼───────────────────────────────────────────┐
│                    IDP Service (Backend)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Catalog    │  │  Scorecards  │  │  Onboarding  │     │
│  │  Management  │  │  & Checks    │  │   Workflows  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Plugins    │  │   Homepage   │  │  Integrations│     │
│  │   & Config   │  │   Layouts    │  │   (40+ tools)│     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────┬───────────────────────────────────────────┘
                  │
      ┌───────────┼───────────┐
      │           │           │
┌─────▼─────┐ ┌──▼──────┐ ┌─▼─────────┐
│  MongoDB  │ │  Redis  │ │TimeScaleDB│
│ (Primary) │ │(Cache/  │ │ (Metrics) │
│           │ │Events)  │ │ (Optional)│
└───────────┘ └─────────┘ └───────────┘
```

### Service Integrations

```
IDP Service
    ├── NG Manager (Auth, Accounts, Secrets)
    ├── Pipeline Service (Workflow execution)
    ├── CI/CD Services (Build/Deploy info)
    ├── Git Services (SCM operations)
    ├── Access Control Service (Permissions)
    ├── Notification Service (Alerts)
    ├── Audit Service (Audit trails)
    ├── License Service (Usage tracking)
    ├── STO Service (Security scans)
    ├── SSCA Service (Supply chain)
    └── External Tools (GitHub, Jira, etc.)
```

---

## Technology Stack

### Core Frameworks

| Technology | Purpose | Version Notes |
|------------|---------|---------------|
| **Dropwizard** | Application framework | Main web framework |
| **Guice** | Dependency injection | DI container |
| **Spring Data MongoDB** | Data persistence | MongoDB integration |
| **Jersey/JAX-RS** | REST API | API implementation |
| **gRPC** | Inter-service communication | RPC framework |
| **Backstage** | Frontend platform | External (Node.js) |

### Databases

| Database | Purpose | Connection Details |
|----------|---------|-------------------|
| **MongoDB** | Primary data store | Collection: `idp-harness`, Pool: 300 connections |
| **Redis** | Cache & event streaming | Redisson client, Sentinel support |
| **PostgreSQL (TimeScaleDB)** | Analytics & metrics | Optional, JooQ for queries |

### Infrastructure

- **Kubernetes**: Container orchestration
- **Helm**: Package management
- **Istio**: Service mesh (optional)
- **Prometheus**: Metrics collection
- **Kafka**: Event streaming (alternative to Redis)
- **Debezium**: Change Data Capture (CDC)

### Build Tools

- **Maven**: Primary build tool
- **Bazel**: Build system (supported)
- **Docker**: Containerization

---

## Code Structure

### Directory Layout

```
idp-service/
├── build/                    # Build scripts and configurations
├── chart/                    # Kubernetes Helm charts
├── config/                   # Configuration files
│   ├── config.yml           # Main configuration (1063 lines)
│   └── manifest.yaml        # Deployment manifest
├── contracts/
│   └── openapi/v1/         # OpenAPI 3.0 spec (10,409 lines)
├── docs/                    # Documentation
│   ├── INDEX.md
│   └── scorecards/
├── src/
│   ├── main/
│   │   ├── java/io/harness/idp/  # Source code (36 packages)
│   │   └── resources/             # Configuration resources
│   │       ├── configs/           # Config templates
│   │       ├── integrations/      # Integration configs
│   │       ├── metadata/          # Plugin metadata (80+ files)
│   │       └── baseappconfig*.yaml
│   └── test/
│       ├── java/                  # Test code (229 test files)
│       └── resources/             # Test resources
├── build.properties         # Version: 1.18.0
├── pom.xml                  # Maven build file
└── README.md
```

### Package Structure (36 Packages)

```
io.harness.idp/
├── app/                     # Application bootstrap
│   ├── IdpApplication       # Main entry point
│   ├── IdpModule           # DI configuration (1497 lines)
│   └── IdpConfiguration    # Config model
├── catalog/                 # Service catalog management
├── scorecard/               # Quality governance
├── backstage/               # Backstage integration
├── onboarding/              # Service onboarding
├── configmanager/           # Plugin configuration
├── homepage/                # Homepage customization
├── gitintegration/          # Git provider integrations
├── integrations/            # Third-party tools
├── groups/                  # User group management
├── user/                    # User management
├── license/                 # License tracking
├── pipeline/                # Pipeline integration
├── steps/                   # Self-service workflow steps
├── provision/               # Resource provisioning
├── proxy/                   # Service proxies
├── settings/                # Settings management
├── envvariable/             # Environment variables
├── allowlist/               # Security allowlist
├── events/                  # Event-driven architecture
├── iterators/               # Background jobs
├── metrics/                 # Observability
├── migration/               # Database migrations (76 classes)
├── audittrails/             # Audit logging
├── k8s/                     # Kubernetes integration
├── governance/              # Governance policies
├── ccp/                     # Catalog Custom Properties
├── icons/                   # Icon management
├── health/                  # Health checks
├── version/                 # Version management
├── serializer/              # Serialization
├── common/                  # Common utilities
├── plugin/                  # Plugin system
├── namespace/               # Multi-tenancy
├── status/                  # Status monitoring
└── annotations/             # Custom annotations
```

### Layered Package Pattern

Each domain package typically follows this structure:

```
{domain}/
├── beans/              # DTOs and request/response models
├── entities/           # Database entities (@Document)
├── repositories/       # Data access (Spring Data)
├── service/            # Business logic
├── resources/          # API endpoints (JAX-RS)
├── mappers/            # Entity-DTO mapping
├── events/             # Event definitions
├── iteratorhandler/    # Background jobs
└── utils/              # Domain utilities
```

---

## Major Components

### 1. Catalog Management (`io.harness.idp.catalog`)

**Purpose:** Manages the software catalog - the heart of IDP

**Key Features:**
- Entity types: Components, APIs, Resources, Systems, Domains, Templates, Users, Groups
- Version control for catalog entities
- Git-backed storage or inline storage
- Custom properties support
- Entity relationships
- Integration with Harness entities

**Key Classes:**
- `CatalogEntity`: Base entity with Git or inline storage
- `CatalogEntityService`: CRUD operations
- `CatalogEntitiesResource`: REST API endpoints
- `CatalogEntityRepository`: Data access

**File:** `src/main/java/io/harness/idp/catalog/`

---

### 2. Scorecards (`io.harness.idp.scorecard`)

**Purpose:** Software quality governance through scorecards

**Key Features:**
- Define quality standards (scorecards)
- Individual quality checks
- Data sources for metrics
- Score computation (async/sync)
- Statistics and aggregations
- JEXL expression evaluation

**Components:**
- **Scorecards**: Quality standard definitions
- **Checks**: Individual validation rules
- **DataSources**: External data providers
- **DataPoints**: Specific metrics
- **Scores**: Computed quality scores

**Key Classes:**
- `ScorecardService`: Scorecard management
- `ScoreComputerService`: Score computation
- `ScoreService`: Score retrieval
- `CheckService`: Check management

**Workflow:**
```
1. Scorecard assigned to entities
2. Iterator triggers computation (12h interval)
3. Data sources queried
4. Checks evaluated (JEXL expressions)
5. Scores computed and cached
6. Statistics aggregated
```

**File:** `src/main/java/io/harness/idp/scorecard/`

---

### 3. Backstage Integration (`io.harness.idp.backstage`)

**Purpose:** Sync and integrate with Backstage frontend

**Key Features:**
- Entity synchronization
- Scaffolder task management
- Periodic sync (iterators)
- Event-driven updates

**Key Classes:**
- `BackstageService`: Main integration service
- `BackstageCatalogEntity`: Synced entities
- `BackstageScaffolderTask`: Workflow tasks
- `BackstageIteratorHandler`: Periodic sync

**File:** `src/main/java/io/harness/idp/backstage/`

---

### 4. Onboarding (`io.harness.idp.onboarding`)

**Purpose:** Streamline service onboarding into IDP

**Features:**
- Import Harness services
- Generate `catalog-info.yaml` files
- Async import processing
- Two versions (v1 and v2)

**Workflow:**
```
User selects services → Generate YAML → Push to Git →
Register in catalog → Backstage sync → Scorecards applied
```

**Key Classes:**
- `OnboardingService`: v1 onboarding
- `OnboardingServiceImplV2`: v2 onboarding
- `OnboardingResource`: API endpoints

**File:** `src/main/java/io/harness/idp/onboarding/`

---

### 5. Configuration Manager (`io.harness.idp.configmanager`)

**Purpose:** Manage plugins and configurations

**Features:**
- 80+ plugin configurations
- Custom plugin support
- Merged plugin configs
- Auth configurations
- Environment variables

**Key Classes:**
- `ConfigManagerService`: Configuration management
- `PluginInfoService`: Plugin metadata
- `ConfigEnvVariablesService`: Environment variables

**File:** `src/main/java/io/harness/idp/configmanager/`

---

### 6. Homepage (`io.harness.idp.homepage`)

**Purpose:** Customizable developer homepage

**Card Types:**
- Learn More
- Top Visited
- Recently Visited
- Starred Entities
- Markdown
- Video
- Custom Link
- Self Service
- GitHub
- Jira
- Harness Code

**Key Classes:**
- `HomePageLayoutService`: Layout management
- `CardEntity`: Base card entity
- Various card type entities

**File:** `src/main/java/io/harness/idp/homepage/`

---

### 7. Git Integration (`io.harness.idp.gitintegration`)

**Purpose:** Multi-provider Git integration

**Supported Providers:**
- GitHub (Cloud & Enterprise)
- GitLab (Cloud & Self-hosted)
- Bitbucket (Cloud & Server)
- Azure Repos
- Harness Code

**Features:**
- OAuth flows
- Connector management
- Repository operations
- File operations

**File:** `src/main/java/io/harness/idp/gitintegration/`

---

### 8. Integrations (`io.harness.idp.integrations`)

**Purpose:** Third-party tool integrations

**Categories:**
- **CI/CD**: Jenkins, CircleCI, GitHub Actions, GitLab CI
- **Monitoring**: Datadog, Grafana, NewRelic, Dynatrace
- **Issue Tracking**: Jira, Confluence
- **Incident Management**: PagerDuty, OpsGenie
- **Security**: SonarQube, Snyk, Wiz
- **Infrastructure**: Kubernetes, ArgoCD, Rafay

**Total:** 40+ pre-configured integrations

**File:** `src/main/resources/integrations/` (metadata)

---

### 9. Self-Service Steps (`io.harness.idp.steps`)

**Purpose:** Workflow steps for self-service provisioning

**Available Steps:**
- `CookieCutter`: Template-based code generation
- `CreateRepo`: Repository creation
- `DirectPush`: Direct code push to Git
- `RegisterCatalog`: Register entities
- `CreateCatalog`: Create catalog entries
- `SlackNotify`: Slack notifications
- `CreateOrganisation/Project/Resource`: Resource provisioning
- `UpdateCatalogProperty`: Modify catalog

**File:** `src/main/java/io/harness/idp/steps/`

---

### 10. Events & Iterators (`io.harness.idp.events`, `io.harness.idp.iterators`)

**Purpose:** Event-driven architecture and background jobs

**Event System:**
- Debezium CDC for database changes
- Redis/Kafka for event streaming
- Event consumers and producers
- Outbox pattern for audit trails

**Background Jobs (Iterators):**
- Scorecard score computation (12h interval)
- License usage counting (24h interval)
- User sync (60s interval)
- Scaffolder tasks sync (5min interval)
- Stats computation (24h interval)
- 15+ more iterators

**Files:**
- `src/main/java/io/harness/idp/events/`
- `src/main/java/io/harness/idp/iterators/`

---

## Data Layer

### Database Schema

#### MongoDB Collections (30+ collections)

**Core Collections:**

| Collection | Purpose | Key Fields |
|------------|---------|------------|
| `backstageCatalog` | Backstage entities | accountId, kind, metadata |
| `backstageScaffolderTasks` | Workflow tasks | taskId, status, steps |
| `scorecards` | Quality scorecards | name, checks, filters |
| `checks` | Quality checks | expression, dataSource |
| `appConfigs` | App configurations | configId, configName, configs |
| `backstageEnvVariables` | Environment variables | envName, type (config/secret) |
| `backstagePermissions` | Permissions | permissionName, policy |
| `homePageLayouts` | Homepage layouts | accountId, layoutConfig |
| `cards` | Homepage cards | type, config |
| `pluginInfo` | Plugin metadata | packageName, config |
| `catalogEntities` | IDP catalog entities | kind, metadata, spec |
| `catalogEntityVersions` | Entity versions | entityId, version, yaml |
| `scorecardStats` | Scorecard statistics | scorecardId, stats |
| `activeDevelopers` | Active developers | email, lastActive |
| `onboardingFlows` | Onboarding workflows | status, entities |

**Additional Collections:**
- `gitIntegrations`, `allowList`, `layouts`, `layoutInfo`
- `dataSourceLocations`, `checkStatus`, `scorecardFilters`
- `customProperties`, `icons`, `namespaceInfo`
- And more...

### Entity Models

**Catalog Entities:**
```java
@Document(collection = "catalogEntities")
public class CatalogEntity {
    @Id private String id;
    private String accountId;
    private String kind; // Component, API, Resource, etc.
    private Object metadata;
    private Object spec;
    private EntityType entityType; // GIT_REFERENCED or INLINE
    // ... more fields
}
```

**Scorecard Entities:**
```java
@Document(collection = "scorecards")
public class ScorecardEntity {
    @Id private String identifier;
    private String accountId;
    private String name;
    private String description;
    private List<CheckDetails> checks;
    private Filter filter;
    private boolean published;
    // ... more fields
}
```

**Homepage Card Entities:**
```java
@Document(collection = "cards")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @Type(value = LearnMoreCardEntity.class, name = "LEARN_MORE"),
    @Type(value = TopVisitedCardEntity.class, name = "TOP_VISITED"),
    // ... more card types
})
public abstract class CardEntity {
    @Id protected String id;
    protected String accountId;
    protected CardType type;
    // ... more fields
}
```

### Persistence Patterns

1. **Repository Pattern**: Spring Data repositories for all entities
2. **Outbox Pattern**: For audit trails and event sourcing
3. **Version Control**: Entity versioning support
4. **Soft Deletes**: Logical deletion tracking
5. **Optimistic Locking**: Concurrent update handling
6. **Transaction Management**: MongoDB transactions where supported
7. **Change Data Capture**: Debezium integration
8. **Retry Policies**: Failsafe retry for conflicts

---

## API Layer

### REST API Overview

**Base Path:** `/v1`
**Format:** JSON
**Spec:** OpenAPI 3.0 (10,409 lines)
**File:** `contracts/openapi/v1/idp.yaml`

### Major API Groups

#### 1. Backstage Environment Variables
**Endpoints:** `/v1/backstage-env-variables`

- `GET /{account-id}` - List all env variables
- `POST /{account-id}` - Create env variable
- `PUT /{account-id}/{env-id}` - Update env variable
- `DELETE /{account-id}/{env-id}` - Delete env variable
- `POST /{account-id}/batch` - Batch operations
- `POST /{account-id}/reload` - Sync with Backstage
- `POST /{account-id}/resolve` - Resolve with decryption

#### 2. Catalog Entities
**Endpoints:** `/v1/entities`

- `GET /{account-id}` - List entities (with filters)
- `POST /{account-id}` - Create entity
- `PUT /{account-id}/{entity-id}` - Update entity
- `DELETE /{account-id}/{entity-id}` - Delete entity
- `GET /{account-id}/{entity-id}/versions` - Get versions

#### 3. Scorecards
**Endpoints:** `/v1/scorecards`

- `GET /{account-id}` - List scorecards
- `POST /{account-id}` - Create scorecard
- `PUT /{account-id}/{scorecard-id}` - Update scorecard
- `DELETE /{account-id}/{scorecard-id}` - Delete scorecard
- `GET /{account-id}/{scorecard-id}/stats` - Get statistics

#### 4. Scores
**Endpoints:** `/v1/scores`

- `GET /{account-id}/summary` - Score summary
- `GET /{account-id}/aggregate` - Aggregate scores
- `GET /{account-id}/graphs` - Score graphs
- `POST /{account-id}/recalibrate` - Trigger recalculation

#### 5. Onboarding
**Endpoints:** `/v1/onboarding`

- `POST /{account-id}/import-entities` - Import Harness entities
- `GET /{account-id}/entity-count` - Get importable count
- `POST /{account-id}/generate-yaml` - Generate catalog YAML

#### 6. Plugins
**Endpoints:** `/v1/plugins-info`, `/v1/backstage-plugins-info`

- `GET /{account-id}` - List plugins
- `POST /{account-id}` - Save plugin config
- `GET /{account-id}/merged-plugins-config` - Get merged config
- `POST /{account-id}/upload` - Upload custom plugin

#### 7. Homepage
**Endpoints:** `/v1/homepage`

- `GET /{account-id}/layout` - Get homepage layout
- `POST /{account-id}/layout` - Save layout
- `POST /{account-id}/cards` - Create card
- `PUT /{account-id}/cards/{card-id}` - Update card
- `POST /{account-id}/icons` - Upload icon

### Authentication & Authorization

**Authentication Methods:**
1. **JWT Bearer Tokens** - User authentication
2. **API Keys** - Service authentication
3. **Service Secrets** - Inter-service auth

**Headers:**
```
Authorization: Bearer <jwt-token>
X-API-KEY: <api-key>
accountIdentifier: <account-id>
```

**Service Secret Mapping:**
```java
BEARER → jwtAuthSecret
IDENTITY_SERVICE → jwtIdentityServiceSecret
IDP_SERVICE → idpServiceSecret
IDP_UI → idpServiceSecret
```

**Authorization:**
- Resource-based permissions via Access Control Service
- Scope filtering (Account/Org/Project)
- RBAC integration

**Annotations:**
```java
@IdpServiceAuth // Requires authentication
@IdpServiceAuthIfHasApiKey // Conditional auth
@PublicApi // No auth required
@InternalApi // Service-to-service only
```

### Request/Response Format

**Standard Response:**
```json
{
  "status": "SUCCESS",
  "data": { /* payload */ },
  "metaData": {},
  "correlationId": "uuid"
}
```

**Pagination:**
```json
{
  "status": "SUCCESS",
  "data": {
    "content": [ /* items */ ],
    "totalItems": 100,
    "totalPages": 10,
    "pageIndex": 0,
    "pageSize": 10
  }
}
```

**Error Response:**
```json
{
  "status": "FAILURE",
  "code": "ERROR_CODE",
  "message": "Error description",
  "correlationId": "uuid"
}
```

---

## Business Logic & Workflows

### Core Workflows

#### 1. Service Onboarding Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                  Service Onboarding Flow                    │
└─────────────────────────────────────────────────────────────┘

User Action → Select Harness Services
    ↓
System → Generate catalog-info.yaml
    ↓
System → Push YAML to Git Repository
    ↓
System → Register Entities in Catalog
    ↓
Iterator → Backstage Syncs Entities
    ↓
System → Apply Scorecards to Entities
    ↓
Complete → Service Visible in IDP
```

**Files:**
- `OnboardingServiceImplV2.java`
- `OnboardingResource.java`

---

#### 2. Scorecard Evaluation Workflow

```
┌─────────────────────────────────────────────────────────────┐
│               Scorecard Evaluation Workflow                 │
└─────────────────────────────────────────────────────────────┘

Assignment → Scorecard Assigned to Entities
    ↓
Iterator → Score Computation Iterator (12h interval)
    ↓
Fetch → Query Data Sources for Metrics
    ↓
Evaluate → Execute Checks (JEXL expressions)
    ↓
Compute → Calculate Scores
    ↓
Store → Persist Scores (cached)
    ↓
Aggregate → Update Statistics
    ↓
Display → Show in UI
```

**Files:**
- `ScoreComputerService.java`
- `ScoreIteratorHandler.java`
- `DataSourceService.java`

---

#### 3. Self-Service Template Workflow

```
┌─────────────────────────────────────────────────────────────┐
│              Self-Service Template Workflow                 │
└─────────────────────────────────────────────────────────────┘

User → Select Template from Catalog
    ↓
User → Fill Form Parameters
    ↓
System → Trigger Pipeline with IDP Steps
    ↓
Step 1 → CookieCutter (Generate Code)
    ↓
Step 2 → CreateRepo (Create Git Repository)
    ↓
Step 3 → DirectPush (Push Code to Repo)
    ↓
Step 4 → RegisterCatalog (Register in IDP)
    ↓
Optional → SlackNotify (Send Notification)
    ↓
Complete → New Service in Catalog
```

**Files:**
- `CookieCutterStep.java`
- `CreateRepoStep.java`
- `DirectPushStep.java`
- `RegisterCatalogStep.java`

---

#### 4. Entity Sync Workflow (Event-Driven)

```
┌─────────────────────────────────────────────────────────────┐
│                  Entity Sync Workflow                       │
└─────────────────────────────────────────────────────────────┘

Database → Entity Modified
    ↓
Debezium CDC → Captures Change
    ↓
Event → Published to Redis/Kafka
    ↓
Consumer → Processes Event
    ↓
Backstage → Notified of Change
    ↓
Frontend → UI Updated
    ↓
Audit → Audit Trail Created (Outbox)
```

**Files:**
- `EventsFrameworkModule.java`
- Event consumer classes in `events/consumer/`

---

#### 5. Plugin Configuration Workflow

```
┌─────────────────────────────────────────────────────────────┐
│              Plugin Configuration Workflow                  │
└─────────────────────────────────────────────────────────────┘

System → Load Plugin Metadata (YAML)
    ↓
User → Configure Plugin Settings
    ↓
System → Store Configuration
    ↓
System → Generate Merged Config
    ↓
Backstage → Reload Configuration
    ↓
Complete → Plugin Active
```

**Files:**
- `PluginInfoService.java`
- `ConfigManagerService.java`
- Metadata in `src/main/resources/metadata/`

---

### Key Services

#### CatalogService
**File:** `catalog/service/CatalogEntitiesServiceImpl.java`

**Responsibilities:**
- CRUD operations for catalog entities
- Version management
- Git integration
- Custom properties
- Entity relationships

**Key Methods:**
```java
createEntity(accountId, entityRequest)
updateEntity(accountId, entityId, entityRequest)
deleteEntity(accountId, entityId)
getEntity(accountId, entityId)
listEntities(accountId, filters, pageRequest)
getEntityVersions(accountId, entityId)
```

---

#### ScorecardService
**File:** `scorecard/service/ScorecardServiceImpl.java`

**Responsibilities:**
- Scorecard CRUD
- Check definitions
- Score computation
- Statistics

**Key Methods:**
```java
createScorecard(accountId, scorecardRequest)
updateScorecard(accountId, scorecardId, scorecardRequest)
getScorecardStats(accountId, scorecardId)
publishScorecard(accountId, scorecardId)
```

---

#### ScoreComputerService
**File:** `scorecard/service/ScoreComputerServiceImpl.java`

**Responsibilities:**
- Async score computation
- Data source integration
- JEXL expression evaluation
- Score caching

**Key Methods:**
```java
computeScoresAsync(accountId, entityId, scorecardId)
computeScoresSync(accountId, entityId, scorecardId)
recalibrateScores(accountId)
```

---

#### BackstageService
**File:** `backstage/service/BackstageServiceImpl.java`

**Responsibilities:**
- Entity sync with Backstage
- Scaffolder task management
- Lifecycle management

---

## Integration Points

### Internal Harness Services

| Service | Purpose | Integration Type |
|---------|---------|-----------------|
| **NG Manager** | Auth, accounts, secrets | REST + gRPC |
| **Pipeline Service** | Workflow execution | gRPC |
| **CI Service** | Build information | REST |
| **CD Service** | Deployment status | REST |
| **Git Service** | SCM operations | REST |
| **Access Control** | Permissions | gRPC |
| **Notification** | Alerts | REST |
| **Audit Service** | Audit trails | gRPC |
| **License Service** | Usage tracking | REST |
| **STO Service** | Security scans | REST |
| **SSCA Service** | Supply chain | REST |

### External Integrations (40+)

**CI/CD:**
- Jenkins, CircleCI, GitHub Actions, GitLab CI, Harness CI/CD

**Monitoring:**
- Datadog, Grafana, NewRelic, Dynatrace, Prometheus

**Issue Tracking:**
- Jira, Confluence, ServiceNow

**Incident Management:**
- PagerDuty, OpsGenie

**Security:**
- SonarQube, Snyk, Wiz, Aqua Security

**Infrastructure:**
- Kubernetes, ArgoCD, Rafay, Terraform

**Git Providers:**
- GitHub, GitLab, Bitbucket, Azure Repos, Harness Code

**Configuration:** `src/main/resources/integrations/`

---

## Configuration

### Main Configuration File

**File:** `config/config.yml` (1063 lines)

**Key Sections:**

```yaml
# Server Configuration
server:
  applicationConnectors:
    - type: http
      port: 12003
  adminConnectors:
    - type: http
      port: 12004

# MongoDB
mongodb:
  uri: ${MONGODB_URI}
  connectTimeout: 30000
  serverSelectionTimeout: 90000
  maxConnectionPoolSize: 300

# Redis
redis:
  redisUrl: ${REDIS_URL}
  sentinelUrls: ${REDIS_SENTINEL_URLS}
  nettyThreads: 16
  useSSL: true

# TimeScaleDB (Optional)
timescaledb:
  timescaledbUrl: ${TIMESCALEDB_URL}
  timescaledbUsername: ${TIMESCALEDB_USERNAME}
  timescaledbPassword: ${TIMESCALEDB_PASSWORD}

# Service Integrations
ngManagerServiceHttpClientConfig:
  baseUrl: ${MANAGER_BASE_URL}

pipelineServiceClientConfig:
  baseUrl: ${PIPELINE_SERVICE_BASE_URL}

# Event Framework
eventsFramework:
  redis:
    enabled: ${EVENTS_FRAMEWORK_REDIS_ENABLED:-true}
    sentinel: true

# Iterators
iteratorConfig:
  - name: "ScoreIteratorHandler"
    targetIntervalInSeconds: 43200  # 12 hours
    threadPoolSize: 5
  - name: "LicenseUsageIteratorHandler"
    targetIntervalInSeconds: 86400  # 24 hours
  # ... 20+ more iterators

# Feature Flags
featureFlagsEnabled: true
```

### Environment Variables

**Key Variables:**
```bash
# Database
MONGODB_URI="mongodb://localhost:27017/idp-harness"
REDIS_URL="redis://localhost:6379"
TIMESCALEDB_URL="jdbc:postgresql://localhost:5432/harness"

# Service Discovery
MANAGER_BASE_URL="http://localhost:3000"
PIPELINE_SERVICE_BASE_URL="http://localhost:12001"

# Authentication
JWT_AUTH_SECRET="<secret>"
JWT_IDENTITY_SERVICE_SECRET="<secret>"
IDP_SERVICE_SECRET="<secret>"

# Features
ENABLE_QUEUE=true
PMS_SDK_EVENTS_USE_KAFKA=false

# Logging
LOG_FILENAME="/opt/harness/logs/pod.log"
LOG_LEVEL="INFO"
```

### Plugin Metadata

**Location:** `src/main/resources/metadata/`

**Example:** `github-actions.yaml`
```yaml
packageName: "@backstage/plugin-github-actions"
description: "View GitHub Actions workflow runs"
category: "CI_CD"
config:
  - key: "github.token"
    type: "string"
    required: true
  - key: "github.org"
    type: "string"
    required: false
```

---

## Deployment

### Container Deployment

**Docker Image:**
```dockerfile
FROM openjdk:11-jre-slim
COPY idp-service.jar /app/
EXPOSE 12003 12004
CMD ["java", "-jar", "/app/idp-service.jar", "server", "/app/config.yml"]
```

### Kubernetes Deployment

**Helm Chart:** `chart/`

**Key Resources:**
- `deployment.yaml` - Main deployment
- `service.yaml` - ClusterIP service
- `configmap.yaml` - Configuration
- `secrets.yaml` - Sensitive data
- `hpa.yaml` - Horizontal Pod Autoscaler
- `pdb.yaml` - Pod Disruption Budget
- `ingress.yaml` - External access

**Deployment Configuration:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
  template:
    spec:
      containers:
      - name: idp-service
        image: harness/idp-service:1.18.0
        ports:
        - containerPort: 12003  # Application
        - containerPort: 12004  # Admin
        - containerPort: 9889   # gRPC
        env:
        - name: MONGODB_URI
          valueFrom:
            secretKeyRef:
              name: idp-secrets
              key: mongodb-uri
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 12004
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 12004
          initialDelaySeconds: 30
          periodSeconds: 5
```

### High Availability

**Features:**
- **Horizontal Scaling**: HPA based on CPU/memory
- **Multiple Replicas**: 3+ replicas for production
- **Pod Disruption Budget**: Ensure minimum availability
- **Health Checks**: Liveness and readiness probes
- **Rolling Updates**: Zero-downtime deployments

### Monitoring

**Prometheus Metrics:**
- Endpoint: `/metrics` (admin port 12004)
- Dropwizard metrics
- Custom IDP metrics
- JVM metrics

**PodMonitor:**
```yaml
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: idp-service
spec:
  selector:
    matchLabels:
      app: idp-service
  podMetricsEndpoints:
  - port: admin
    path: /metrics
```

### Migration

**Database Migrations:**
- 76 migration classes in `migration/`
- Executed on startup
- Managed by NGMigrationSdkModule
- Idempotent and versioned

---

## Development Guide

### Getting Started

**Prerequisites:**
- Java 11+
- Maven 3.6+
- MongoDB 4.4+
- Redis 6+
- Docker (optional)

**Clone Repository:**
```bash
cd /Users/deep_sea/Documents/harness/harness-core/idp-service
```

**Build:**
```bash
# Maven build
mvn clean install -DskipTests

# With tests
mvn clean install

# Bazel build (if using Bazel)
bazel build //idp-service/...
```

**Run Locally:**
```bash
# Set environment variables
export MONGODB_URI="mongodb://localhost:27017/idp-harness"
export REDIS_URL="redis://localhost:6379"

# Run service
java -jar target/idp-service-1.18.0.jar server config/config.yml

# Or use the script
./build/run_local.sh
```

**Remote Debugging:**
```bash
# Start with debug enabled
./build/remote_debug.sh

# Attach debugger on port 5005
```

### Development Workflow

1. **Create Feature Branch:**
```bash
git checkout -b feature/my-feature
```

2. **Make Changes:**
   - Follow existing package structure
   - Add tests for new code
   - Update OpenAPI spec if adding APIs

3. **Run Tests:**
```bash
mvn test
mvn verify  # Includes integration tests
```

4. **Code Quality:**
```bash
# SonarQube scan
mvn sonar:sonar
```

5. **Commit:**
```bash
git add .
git commit -m "feat: Add new feature"
```

6. **Create PR:**
```bash
git push origin feature/my-feature
# Create PR on GitHub
```

### Adding New API Endpoint

**1. Define Request/Response DTOs:**
```java
// File: src/main/java/io/harness/idp/{domain}/beans/
@Data
@Builder
public class MyFeatureRequest {
    private String accountId;
    private String name;
    // ... fields
}

@Data
@Builder
public class MyFeatureResponse {
    private String id;
    private String name;
    // ... fields
}
```

**2. Create Entity:**
```java
// File: src/main/java/io/harness/idp/{domain}/entities/
@Document(collection = "myFeature")
@Data
@Builder
public class MyFeatureEntity {
    @Id private String id;
    private String accountId;
    private String name;
    // ... fields
}
```

**3. Create Repository:**
```java
// File: src/main/java/io/harness/idp/{domain}/repositories/
public interface MyFeatureRepository
    extends MongoRepository<MyFeatureEntity, String> {
    List<MyFeatureEntity> findByAccountId(String accountId);
}
```

**4. Create Service:**
```java
// File: src/main/java/io/harness/idp/{domain}/service/
public interface MyFeatureService {
    MyFeatureResponse create(String accountId, MyFeatureRequest request);
    MyFeatureResponse get(String accountId, String id);
}

// Implementation
@Service
public class MyFeatureServiceImpl implements MyFeatureService {
    @Inject private MyFeatureRepository repository;

    @Override
    public MyFeatureResponse create(String accountId, MyFeatureRequest request) {
        MyFeatureEntity entity = MyFeatureEntity.builder()
            .accountId(accountId)
            .name(request.getName())
            .build();
        entity = repository.save(entity);
        return toResponse(entity);
    }
}
```

**5. Create Resource (API):**
```java
// File: src/main/java/io/harness/idp/{domain}/resources/
@Path("/v1/my-feature")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@IdpServiceAuth
public class MyFeatureResource {
    @Inject private MyFeatureService service;

    @POST
    @Path("/{account-id}")
    public ResponseDTO<MyFeatureResponse> create(
        @PathParam("account-id") String accountId,
        MyFeatureRequest request) {
        return ResponseDTO.newResponse(
            service.create(accountId, request));
    }
}
```

**6. Register in Module:**
```java
// File: src/main/java/io/harness/idp/app/IdpModule.java
bind(MyFeatureService.class).to(MyFeatureServiceImpl.class);
```

**7. Update OpenAPI Spec:**
```yaml
# File: contracts/openapi/v1/idp.yaml
paths:
  /v1/my-feature/{account-id}:
    post:
      summary: Create my feature
      parameters:
        - name: account-id
          in: path
          required: true
          schema:
            type: string
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/MyFeatureRequest'
      responses:
        '200':
          description: Success
```

**8. Add Tests:**
```java
// File: src/test/java/io/harness/idp/{domain}/service/
@RunWith(MockitoJUnitRunner.class)
public class MyFeatureServiceImplTest {
    @Mock private MyFeatureRepository repository;
    @InjectMocks private MyFeatureServiceImpl service;

    @Test
    public void testCreate() {
        // Test implementation
    }
}
```

### Adding Background Iterator

**1. Create Iterator Handler:**
```java
// File: src/main/java/io/harness/idp/{domain}/iteratorhandler/
@Singleton
public class MyFeatureIteratorHandler extends IteratorBaseHandler {
    @Inject private MyFeatureService service;

    @Override
    public void handle(String accountId) {
        log.info("Running my feature iterator for account: {}", accountId);
        // Your logic here
        service.processData(accountId);
    }
}
```

**2. Configure in config.yml:**
```yaml
iteratorConfig:
  - name: "MyFeatureIteratorHandler"
    targetIntervalInSeconds: 3600  # 1 hour
    threadPoolSize: 2
```

**3. Register in Module:**
```java
// File: src/main/java/io/harness/idp/app/IdpModule.java
bind(ScheduledExecutorService.class)
    .annotatedWith(Names.named("MyFeatureIteratorHandler"))
    .toProvider(new ScheduledThreadPoolProviderImpl("MyFeatureIteratorHandler"));
```

---

## Testing

### Test Structure

```
src/test/java/io/harness/idp/
├── catalog/          # Catalog tests
├── scorecard/        # Scorecard tests
├── onboarding/       # Onboarding tests
├── configmanager/    # Config tests
├── provision/        # Provision tests
└── ...              # More domain tests
```

### Testing Technologies

- **JUnit 4/5** - Test framework
- **Mockito** - Mocking framework
- **Spring Boot Test** - Integration testing
- **WireMock** - HTTP mocking (if used)
- **Testcontainers** - Integration with real databases (if used)

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=CatalogEntitiesServiceImplTest

# Specific test method
mvn test -Dtest=CatalogEntitiesServiceImplTest#testCreateEntity

# Integration tests
mvn verify

# With coverage
mvn clean test jacoco:report
```

### Writing Tests

**Unit Test Example:**
```java
@RunWith(MockitoJUnitRunner.class)
public class CatalogEntitiesServiceImplTest {
    @Mock private CatalogEntityRepository repository;
    @Mock private GitSyncService gitSyncService;
    @InjectMocks private CatalogEntitiesServiceImpl service;

    @Test
    public void testCreateEntity_Success() {
        // Given
        String accountId = "acc123";
        CatalogEntityRequest request = CatalogEntityRequest.builder()
            .kind("Component")
            .metadata(Map.of("name", "my-service"))
            .build();

        CatalogEntity entity = CatalogEntity.builder()
            .id("id123")
            .accountId(accountId)
            .kind("Component")
            .build();

        when(repository.save(any())).thenReturn(entity);

        // When
        CatalogEntityResponse response = service.createEntity(accountId, request);

        // Then
        assertNotNull(response);
        assertEquals("id123", response.getId());
        verify(repository).save(any());
    }
}
```

**Integration Test Example:**
```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class CatalogEntitiesResourceIT {
    @Autowired private TestRestTemplate restTemplate;

    @Test
    public void testCreateEntityEndpoint() {
        CatalogEntityRequest request = new CatalogEntityRequest();
        request.setKind("Component");

        ResponseEntity<ResponseDTO> response = restTemplate
            .postForEntity("/v1/entities/acc123", request, ResponseDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

---

## Key Design Patterns

### 1. Dependency Injection (Guice)
**Usage:** All service classes, repositories, resources
**File:** `IdpModule.java`

### 2. Repository Pattern
**Usage:** Data access layer
**Example:** `CatalogEntityRepository`, `ScorecardRepository`

### 3. Service Layer Pattern
**Usage:** Business logic separation
**Example:** `CatalogEntitiesService`, `ScorecardService`

### 4. Factory Pattern
**Usage:** Creating connector processors, clients
**Example:** `ConnectorProcessorFactory`, `RedissonClientFactory`

### 5. Strategy Pattern
**Usage:** Multiple card types, plugin mappers
**Example:** `CardMapper`, `PluginMapper`

### 6. Builder Pattern
**Usage:** Entity creation, request/response objects
**Example:** All `@Builder` annotated classes

### 7. Observer Pattern (Event-Driven)
**Usage:** Event producers and consumers
**Example:** Debezium CDC events, outbox pattern

### 8. Proxy Pattern
**Usage:** Proxying external service calls
**Example:** `DelegateProxyService`, `LayoutProxyService`

### 9. Template Method Pattern
**Usage:** Abstract base classes
**Example:** `IteratorBaseHandler`

### 10. Iterator Pattern
**Usage:** Background job processing
**Example:** All iterator handlers in `iterators/`

### 11. Mapper Pattern
**Usage:** Entity-DTO conversion
**Example:** `CatalogEntityMapper`, `ScorecardMapper`

### 12. Outbox Pattern
**Usage:** Audit trail creation
**Example:** Audit event handlers

---

## Contributing

### Code Style

- Follow Java naming conventions
- Use Lombok for boilerplate reduction
- Write comprehensive JavaDocs
- Keep methods small and focused
- Use meaningful variable names

### Branch Strategy

- `develop` - Main development branch
- `feature/*` - Feature branches
- `bugfix/*` - Bug fix branches
- `release/*` - Release branches

### PR Guidelines

1. Write clear PR description
2. Include tests
3. Update documentation
4. Follow code review feedback
5. Ensure CI passes

### Code Review Checklist

- [ ] Tests added/updated
- [ ] OpenAPI spec updated (if API changed)
- [ ] Error handling included
- [ ] Logging added
- [ ] Performance considered
- [ ] Security reviewed
- [ ] Documentation updated

---

## Additional Resources

### Documentation

- **Index:** `docs/INDEX.md`
- **Scorecards:** `docs/scorecards/`
- **OpenAPI Spec:** `contracts/openapi/v1/idp.yaml`
- **README:** `README.md`

### Key Files

- **Application Entry:** `src/main/java/io/harness/idp/app/IdpApplication.java`
- **DI Configuration:** `src/main/java/io/harness/idp/app/IdpModule.java` (1497 lines)
- **Main Config:** `config/config.yml` (1063 lines)
- **Build Properties:** `build.properties`

### Useful Commands

```bash
# Find specific code
grep -r "ClassName" src/

# Count lines of code
find src/main/java -name "*.java" | xargs wc -l

# View git history
git log --oneline --graph

# Check dependencies
mvn dependency:tree

# Build without tests
mvn clean install -DskipTests

# Run specific iterator (for testing)
# Add logic in main method temporarily
```

---

## Summary

The **IDP Service** is a robust, production-ready Internal Developer Portal that:

✅ Centralizes developer experience
✅ Manages comprehensive service catalog
✅ Enforces quality governance through scorecards
✅ Enables self-service provisioning
✅ Integrates with 40+ external tools
✅ Provides customizable homepage
✅ Supports multi-tenancy
✅ Implements event-driven architecture
✅ Scales horizontally on Kubernetes
✅ Maintains enterprise-grade security

**Current Version:** 1.18.0
**Architecture:** Microservice
**Primary Language:** Java
**Frontend:** Backstage (separate)
**Database:** MongoDB, Redis, PostgreSQL

For questions or contributions, refer to the main repository documentation or reach out to the IDP team.

---

*Generated on November 5, 2025*

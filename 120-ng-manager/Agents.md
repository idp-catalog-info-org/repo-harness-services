# 120-ng-manager - Agents.md

## Purpose
Core orchestration service for Harness NextGen platform. Manages multi-tenant hierarchy (Account/Org/Project), services, environments, delegates, webhooks, and provides the central REST API for the platform.

## Quick Navigation

| Looking For | Location |
|-------------|----------|
| Application Bootstrap | `src/main/java/io/harness/ng/modules/NextGenApplication.java` |
| Organization Management | `src/main/java/io/harness/ng/core/impl/OrganizationServiceImpl.java` |
| Project Management | `src/main/java/io/harness/ng/core/impl/ProjectServiceImpl.java` |
| Service Entities | `src/main/java/io/harness/ng/core/service/` |
| Webhooks | `src/main/java/io/harness/ng/webhook/` |
| Agents/Delegates | `src/main/java/io/harness/ng/agent/` |
| Freeze Windows | `src/main/java/io/harness/ng/freeze/` |
| SCIM Integration | `src/main/java/io/harness/ng/scim/` |
| GitOps | `src/main/java/io/harness/ng/gitops/` |
| OAuth | `src/main/java/io/harness/ng/oauth/` |
| Change Streams | `src/main/java/io/harness/changestreams/` |
| Event Handlers | `src/main/java/io/harness/ng/core/event/` |
| DI Modules | `src/main/java/io/harness/ng/core/modules/` |
| Migrations | `src/main/java/io/harness/ng/migration/` |
| Access Control | `src/main/java/io/harness/ng/accesscontrol/` |

## Critical Files (Start Here)

| File | Purpose |
|------|---------|
| `src/main/java/io/harness/ng/modules/NextGenApplication.java` | Application bootstrap, 150+ dependencies, filter registration |
| `src/main/java/io/harness/ng/core/impl/OrganizationServiceImpl.java` | Organization CRUD operations |
| `src/main/java/io/harness/ng/core/impl/ProjectServiceImpl.java` | Project CRUD operations |
| `src/main/java/io/harness/ng/core/impl/ScopeInfoServiceImpl.java` | Scope resolution (Account/Org/Project) |
| `src/main/java/io/harness/ng/core/modules/CoreModule.java` | Core DI bindings |
| `src/main/java/io/harness/ng/core/modules/NGAggregateModule.java` | Aggregate module for NG services |
| `config.yml` | Runtime configuration (ports 7090/7457) |

## Package Map

| Package | What It Does |
|---------|--------------|
| `ng/core/` | Core domain logic, entities, services |
| `ng/core/api/` | Service interfaces (TokenService, ApiKeyService, etc.) |
| `ng/core/impl/` | Service implementations |
| `ng/core/entities/` | Domain models (ApiKey, Token, etc.) |
| `ng/core/service/` | Service management (CRUD, mappers) |
| `ng/core/event/` | Event handling system |
| `ng/core/modules/` | Guice DI modules |
| `ng/core/dto/` | Data Transfer Objects |
| `ng/webhook/` | Webhook management (entities, services, polling) |
| `ng/agent/` | Agent/Delegate management |
| `ng/freeze/` | Deployment freeze windows |
| `ng/scim/` | SCIM protocol for identity |
| `ng/oauth/` | OAuth authentication |
| `ng/gitops/` | GitOps integration |
| `ng/gitsync/` | Git synchronization |
| `ng/gitxwebhook/` | Git webhook handling |
| `ng/accesscontrol/` | Access control integration |
| `ng/overview/` | Dashboard and overview |
| `ng/filter/` | Entity filtering |
| `ng/instance/` | Instance management |
| `ng/instancesync/` | Instance state sync |
| `ng/jira/` | Jira integration |
| `ng/opa/` | Open Policy Agent |
| `ng/tunnel/` | Tunnel/proxy management |
| `ng/migration/` | Data migrations (24 sub-packages) |
| `changestreams/` | MongoDB change stream processing |
| `eventsframework/` | Events framework integration |

## Key Classes by Responsibility

### Core Services
| Class | Responsibility |
|-------|----------------|
| `OrganizationServiceImpl` | Organization CRUD, validation |
| `ProjectServiceImpl` | Project CRUD, org association |
| `ScopeInfoServiceImpl` | Resolve scope from identifiers |
| `ServiceEntityServiceImpl` | CD service management |
| `EnvironmentServiceImpl` | Environment management |

### Authentication & Authorization
| Class | Responsibility |
|-------|----------------|
| `ApiKeyServiceImpl` | API key management |
| `TokenServiceImpl` | Token generation and validation |
| `DelegateDetailsServiceImpl` | Delegate agent details |
| `NGModulesServiceImpl` | Module licensing and access |

### Event Processing
| Class | Responsibility |
|-------|----------------|
| `EntityCRUDStreamListener` | Listens to entity CRUD events |
| `SetupUsageStreamListener` | Setup usage tracking |
| `LicenseUsageStreamListener` | License usage events |
| `UserMembershipStreamListener` | User membership changes |
| `ProjectEventHandler` | Project-level events |

### Change Streams
| Class | Responsibility |
|-------|----------------|
| `ChangeStreamController` | Manages MongoDB change stream consumers |
| Various `*StreamConsumer` classes | Process specific entity changes |

### Webhooks
| Class | Responsibility |
|-------|----------------|
| `NgWebhookServiceImpl` | Webhook CRUD operations |
| `WebhookEventServiceImpl` | Webhook event processing |
| `WebhookPollingServiceImpl` | Webhook polling logic |

## Sub-Modules (in modules/ directory)

| Module | Purpose |
|--------|---------|
| `branding/` | UI branding customization |
| `eula/` | EULA management |
| `favorites/` | User favorites |
| `ip-allowlist/` | IP allowlist configuration |
| `ldap/` | LDAP authentication |
| `ng-banners/` | Application banners |
| `ng-certificates/` | Certificate management |
| `ng-settings/` | Global settings |
| `ng-subscriptions/` | Subscription management |
| `ng-variables/` | Variable management |
| `oidc-auth/` | OIDC authentication |

## Business Logic Patterns

| When You Need To... | Look In... |
|---------------------|------------|
| Manage organizations | `ng/core/impl/OrganizationServiceImpl` |
| Manage projects | `ng/core/impl/ProjectServiceImpl` |
| Resolve scope hierarchy | `ng/core/impl/ScopeInfoServiceImpl` |
| Handle entity events | `ng/core/event/` |
| Process MongoDB changes | `changestreams/` |
| Manage webhooks | `ng/webhook/` |
| Handle delegate agents | `ng/agent/` |
| Freeze deployments | `ng/freeze/` |
| SCIM user provisioning | `ng/scim/` |
| OAuth flows | `ng/oauth/` |

## REST API Entry Points

| Resource Pattern | Purpose |
|------------------|---------|
| `*Resource.java` classes | REST endpoints |
| `AgentMtlsEndpointNgResource` | Agent mTLS endpoints |
| `NgWebhookResource` | Webhook management |
| `WebhookEventResource` | Webhook events |
| `FreezeCRUDResource` | Freeze window CRUD |
| `FreezeEvaluationResource` | Freeze evaluation |
| `NGScimUserResource` | SCIM user operations |
| `NGScimGroupResource` | SCIM group operations |
| `ServiceResource` / `ServiceResourceV2` | Service CRUD |

## Scope Hierarchy

All entities respect the Account -> Organization -> Project hierarchy:

```
Account (accountIdentifier)
└── Organization (orgIdentifier)
    └── Project (projectIdentifier)
```

Key concepts:
- **Scope-based access control**: All queries filtered by scope
- **Scope resolution**: `ScopeInfoServiceImpl` resolves scope from identifiers
- **Scope events**: Changes propagate through scope hierarchy

## Event-Driven Architecture

### MongoDB Change Streams
- Location: `changestreams/`
- Controllers manage stream consumers
- Consumers process entity changes in real-time

### Event Framework
- Location: `ng/core/event/`
- Listeners process CRUD, setup, license events
- Handlers react to project, organization events

## Configuration

| Config Section | Purpose |
|----------------|---------|
| `mongo` | MongoDB connection |
| `eventsFramework` | Redis-based events |
| `accessControlClient` | RBAC integration |
| `secretsConfiguration` | Secret management |
| `ngManagerClientConfig` | Service client config |
| `ldapSyncJobConfig` | LDAP group sync |

## Migrations

The module has 24 migration sub-packages in `ng/migration/`:
- Account migrations
- Connector migrations
- Environment migrations
- Infrastructure migrations
- Pipeline migrations
- Service migrations
- Template migrations
- And more...

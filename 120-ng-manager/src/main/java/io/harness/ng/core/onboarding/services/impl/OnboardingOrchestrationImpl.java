/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingExecuteResponseDTO;
import io.harness.ng.core.onboarding.dto.OnboardingExecuteResponseDTO.OnboardingExecuteResponseDTOBuilder;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.mapper.ManifestProviderType;
import io.harness.ng.core.onboarding.mapper.OnboardingContextNormalizer;
import io.harness.ng.core.onboarding.mapper.OnboardingServiceYamlBuilder;
import io.harness.ng.core.onboarding.provisioners.spec.ArtifactProvisioner;
import io.harness.ng.core.onboarding.provisioners.spec.InfraProvisioner;
import io.harness.ng.core.onboarding.provisioners.spec.ManifestProvisioner;
import io.harness.ng.core.onboarding.services.OnboardingOrchestrationService;
import io.harness.ng.core.onboarding.support.OnboardingConnectorCreation;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.UpsertOptions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Orchestrates onboarding by provisioning the secrets, connectors, service, environment and infrastructure whose YAML
 * references those connectors. All dependencies are in-process Guice beans; there is no self-HTTP hop.
 *
 * <p>This coordinator is a thin conductor: each source (manifest, artifact, infrastructure) is provisioned by a
 * per-type provisioner behind a flat {@code MapBinder} registry, dispatched by the resolved provider/type. The
 * coordinator owns the cross-cutting flow — request validation ordering, connector upsert, service assembly, and the
 * environment/infrastructure sequence — while the provisioners own the type-specific secret, connector, and YAML-node
 * details.
 *
 * <p>This does <b>not</b> roll back on partial failure — resources created before a failure are left in place and the
 * error surfaced. Where a resource with the requested identifier already exists, it is updated (upsert) rather than
 * duplicated.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class OnboardingOrchestrationImpl implements OnboardingOrchestrationService {
  private final OnboardingConnectorCreation connectorCreation;
  private final ServiceEntityService serviceEntityService;
  private final ScopeInfoService scopeInfoService;
  private final EnvironmentService environmentService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final OnboardingPipelineGenerator pipelineGenerator;
  private final Map<ManifestProviderType, ManifestProvisioner> manifestProvisioners;
  private final Map<ArtifactProviderType, ArtifactProvisioner> artifactProvisioners;
  private final Map<InfrastructureType, InfraProvisioner> infraProvisioners;

  @Inject
  public OnboardingOrchestrationImpl(OnboardingConnectorCreation connectorCreation,
      ServiceEntityService serviceEntityService, ScopeInfoService scopeInfoService,
      EnvironmentService environmentService, InfrastructureEntityService infrastructureEntityService,
      OnboardingPipelineGenerator pipelineGenerator,
      Map<ManifestProviderType, ManifestProvisioner> manifestProvisioners,
      Map<ArtifactProviderType, ArtifactProvisioner> artifactProvisioners,
      Map<InfrastructureType, InfraProvisioner> infraProvisioners) {
    this.connectorCreation = connectorCreation;
    this.serviceEntityService = serviceEntityService;
    this.scopeInfoService = scopeInfoService;
    this.environmentService = environmentService;
    this.infrastructureEntityService = infrastructureEntityService;
    this.pipelineGenerator = pipelineGenerator;
    this.manifestProvisioners = manifestProvisioners;
    this.artifactProvisioners = artifactProvisioners;
    this.infraProvisioners = infraProvisioners;
  }

  @Override
  public OnboardingExecuteResponseDTO execute(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, OnboardingContextDTO context) {
    if (context == null) {
      throw new InvalidRequestException("Onboarding context is required");
    }

    // Strategy-based request: render a ready-to-run pipeline from the strategy's template and return it directly.
    // This path provisions no resources — the caller already has the service/environment/infrastructure identifiers.
    if (context.getStrategy() != null) {
      return OnboardingExecuteResponseDTO.builder().pipelineYaml(pipelineGenerator.generate(context)).build();
    }

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);

    // A single request provisions either a service or an infrastructure, never both. Detect which sections are present
    // to route to the matching branch, and reject a request that mixes the two.
    boolean hasInfra = isInfraPresent(context);
    // Update vs. create hinges on whether service_id targets an existing service. On update we merge into it (untouched
    // sections preserved), so service_type may be omitted; on create it is required.
    Optional<ServiceEntity> existingService = StringUtils.isBlank(context.getServiceId())
        ? Optional.empty()
        : serviceEntityService.get(scopeInfo, OnboardingIdentifiers.sanitizeIdentifier(context.getServiceId()), false);
    boolean isUpdate = existingService.isPresent();
    boolean hasService = hasService(context, existingService);
    boolean hasManifest = isManifestPresent(context);
    boolean hasArtifact = isArtifactPresent(context);
    if (hasInfra && (hasService || hasManifest || hasArtifact)) {
      throw new InvalidRequestException(
          "A single onboarding request provisions either a service or infrastructure, not both; send them as "
          + "separate requests");
    }

    OnboardingExecuteResponseDTOBuilder response = OnboardingExecuteResponseDTO.builder();
    List<String> createdSecrets = new ArrayList<>();

    // Infrastructure request: validate the deployment target up front (provisionInfrastructure does not re-validate),
    // then provision the K8s cluster connector, its environment, and the infrastructure in dependency order.
    if (hasInfra) {
      // Resolve the infra type from the request (not a hardcoded constant) and dispatch to its provisioner, so a new
      // infrastructure type is a matter of adding a provisioner + MapBinder binding + a resolveInfraType case.
      InfraProvisioner infraProvisioner =
          resolveInfraProvisioner(OnboardingContextNormalizer.resolveInfraType(context.getInfraType()));
      infraProvisioner.validate(context);
      provisionInfrastructure(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context, response,
          createdSecrets, infraProvisioner);
      response.secretIdentifiers(createdSecrets);
      log.info("Completed onboarding execute (infrastructure). account={}, org={}, project={}", accountIdentifier,
          orgIdentifier, projectIdentifier);
      return response.build();
    }

    // Service request. Fail fast before creating any resource. service_type is required on create; on update it may be
    // omitted, but is still validated when supplied.
    if (hasService && (!isUpdate || StringUtils.isNotBlank(context.getServiceType()))) {
      OnboardingContextNormalizer.validateServiceType(context.getServiceType());
    }
    // A manifest/artifact section is only ever written into the service YAML. Without a service to attach it to we
    // would create orphaned secrets/connectors and still report their identifiers as success, so reject up front.
    if (!hasService && (hasManifest || hasArtifact)) {
      throw new InvalidRequestException(
          "A manifest or artifact section requires a service to attach it to; provide service_type (create) "
          + "or a service_identifier that matches an existing service (update)");
    }
    ManifestProviderType manifestProvider =
        hasManifest ? OnboardingContextNormalizer.resolveManifestType(context.getManifestType()) : null;
    ArtifactProviderType artifactProvider =
        hasArtifact ? OnboardingContextNormalizer.resolveArtifactType(context.getArtifactType()) : null;
    // Seed fields the caller may omit for a fully backend-defined source (e.g. HarnessArtifactSample's
    // id/imagePath/tag) so downstream validation and the YAML builder treat it like any other artifact. No-op for
    // caller-supplied sources.
    if (hasArtifact) {
      resolveArtifactProvisioner(artifactProvider).applyDefaults(context);
    }
    // manifest_id is optional for every manifest type: when the caller omits it, generate one so the manifest store,
    // its connector and its credential secret share a stable key.
    if (hasManifest && StringUtils.isBlank(context.getManifestId())) {
      context.setManifestId(OnboardingIdentifiers.generateIdentifier("onboarding_manifest"));
    }
    // artifact_id is optional for every artifact type: when the caller omits it, generate one so the artifact source,
    // its connector and its credential secrets share a stable key. (HarnessArtifactSample already seeded its id above.)
    if (hasArtifact && StringUtils.isBlank(context.getArtifactId())) {
      context.setArtifactId(OnboardingIdentifiers.generateIdentifier("onboarding_artifact"));
    }
    validateRequiredFields(context, manifestProvider, artifactProvider);

    // Provision the service and its manifest/artifact connectors (when present).
    ProvisionedConnectors connectors = provisionService(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo,
        context, existingService, manifestProvider, artifactProvider, response, createdSecrets);
    response.secretIdentifiers(createdSecrets);

    log.info("Completed onboarding execute. account={}, org={}, project={}, connectors=[{}, {}]", accountIdentifier,
        orgIdentifier, projectIdentifier, connectors.manifestConnectorRef(), connectors.artifactConnectorRef());
    return response.build();
  }

  /**
   * Provisions the service section: the manifest connector (for providers that need one), the artifact connector
   * (created or reused), and the service entity itself, whose YAML references those connectors. Each part is emitted
   * only when its section is present; an infra-only request produces none of them. Returns the two connector
   * references for the completion log.
   */
  private ProvisionedConnectors provisionService(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, OnboardingContextDTO context,
      Optional<ServiceEntity> existingService, ManifestProviderType manifestProvider,
      ArtifactProviderType artifactProvider, OnboardingExecuteResponseDTOBuilder response,
      List<String> createdSecrets) {
    // 1 + 2. Manifest secret + connector — only for providers that need one (e.g. GitHub). Harness Code's
    // connection is built-in, so it creates neither a secret nor a connector; only the store is emitted later.
    String manifestConnectorRef = null;
    if (manifestProvider != null) {
      // Report the manifest id regardless of provider, so callers get a confirmation even when no connector exists.
      response.manifestIdentifier(context.getManifestId());
      ManifestProvisioner manifestProvisioner = resolveManifestProvisioner(manifestProvider);
      if (manifestProvisioner.requiresConnector()) {
        ConnectorInfoDTO manifestConnector = manifestProvisioner.buildConnector(
            provisionContext(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context, createdSecrets));
        manifestConnectorRef =
            connectorCreation.upsertConnector(scopeInfo, orgIdentifier, projectIdentifier, manifestConnector);
        response.manifestConnectorIdentifier(manifestConnectorRef);
      }
    }

    // 1 + 2. Artifact secret + connector — only when artifact data is supplied. DockerRegistry provisions a Docker
    // connector, ECR an AWS connector; HarnessArtifactSample reuses the built-in account-level Docker connector, so
    // it creates neither a secret nor a connector. The artifact source is emitted later, pointed at this connector.
    String artifactConnectorRef = null;
    if (artifactProvider != null) {
      response.artifactIdentifier(context.getArtifactId());
      ArtifactProvisioner artifactProvisioner = resolveArtifactProvisioner(artifactProvider);
      if (artifactProvisioner.requiresConnector()) {
        ConnectorInfoDTO artifactConnector = artifactProvisioner.buildConnector(
            provisionContext(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context, createdSecrets));
        artifactConnectorRef =
            connectorCreation.upsertConnector(scopeInfo, orgIdentifier, projectIdentifier, artifactConnector);
      } else {
        artifactConnectorRef = artifactProvisioner.reusedConnectorRef();
      }
      response.artifactConnectorIdentifier(artifactConnectorRef);
    }

    // 3. Service — YAML references the connectors created above (when present). On update, the request is merged
    // into the existing service so untouched sections are preserved. Skipped when no service section is present
    // (e.g. an infra-only request).
    if (hasService(context, existingService)) {
      String serviceIdentifier = upsertService(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context,
          existingService, manifestProvider, artifactProvider, manifestConnectorRef, artifactConnectorRef);
      response.serviceIdentifier(serviceIdentifier);
    }
    return new ProvisionedConnectors(manifestConnectorRef, artifactConnectorRef);
  }

  /** The manifest and artifact connector references produced while provisioning the service, for the completion log. */
  private record ProvisionedConnectors(String manifestConnectorRef, String artifactConnectorRef) {}

  /**
   * A service section is considered present when the request targets an existing service or carries any service
   * identifying field set.
   */
  private static boolean hasService(OnboardingContextDTO context, Optional<ServiceEntity> existingService) {
    return existingService.isPresent() || StringUtils.isNotBlank(context.getServiceType())
        || StringUtils.isNotBlank(context.getServiceId());
  }

  /** Assembles the working state threaded through the provisioners for one request. */
  private OnboardingProvisionContext provisionContext(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, OnboardingContextDTO context, List<String> createdSecrets) {
    return OnboardingProvisionContext.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .scopeInfo(scopeInfo)
        .request(context)
        .createdSecrets(createdSecrets)
        .build();
  }

  private void validateRequiredFields(
      OnboardingContextDTO context, ManifestProviderType manifestProvider, ArtifactProviderType artifactProvider) {
    // service_id is optional: when absent, one is generated. Manifest fields are validated only when a
    // manifest section is being provisioned, and per the resolved provider. manifest_id itself is optional:
    // it is generated on the backend (see execute) when the caller omits it.
    if (manifestProvider != null) {
      if (StringUtils.isBlank(context.getManifestPaths())) {
        throw new InvalidRequestException(
            "manifest_paths (at least one file/folder path) is required when manifest data is provided");
      }
      validateManifestGitFetchRef(context);
      resolveManifestProvisioner(manifestProvider).validate(context);
    }
    // Artifact fields are required only when an artifact section is being provisioned, and per the resolved provider.
    // artifact_id itself is optional: it is generated on the backend (see execute) when the caller omits it.
    boolean hasArtifact = artifactProvider != null;
    if (hasArtifact) {
      resolveArtifactProvisioner(artifactProvider).validate(context);
    }
    // The manifest and artifact connectors (and their credential secrets) are keyed off these identifiers. When the
    // manifest provider creates a connector, an id that sanitizes to the artifact's would clobber the manifest secret
    // and then fail with an opaque connector-type-mismatch, leaving orphaned resources behind. Reject the collision.
    if (manifestProvider != null && manifestProvider.requiresConnector() && hasArtifact
        && OnboardingIdentifiers.sanitizeIdentifier(context.getManifestId())
               .equals(OnboardingIdentifiers.sanitizeIdentifier(context.getArtifactId()))) {
      throw new InvalidRequestException(
          "manifest_id and artifact_id must be distinct; they resolve to the same identifier, which would clobber the "
          + "manifest connector and its credential");
    }
  }

  /**
   * When manifest_fetchType is 'commit' a manifest_commitId is required; otherwise (branch) a manifest_branch is
   * required. Shared by every git manifest provider (github/gitlab/bitbucket/harnessCode).
   */
  private static void validateManifestGitFetchRef(OnboardingContextDTO context) {
    if (OnboardingContextNormalizer.resolveGitFetchType(context.getManifestFetchType()) == FetchType.COMMIT) {
      if (StringUtils.isBlank(context.getManifestCommitId())) {
        throw new InvalidRequestException("manifest_commitId is required when manifest_fetchType is 'commit'");
      }
    } else if (StringUtils.isBlank(context.getManifestBranch())) {
      throw new InvalidRequestException("manifest_branch is required when manifest_fetchType is 'branch'");
    }
  }

  /** A manifest section is considered present when any identifying field is supplied. */
  private static boolean isManifestPresent(OnboardingContextDTO context) {
    return context.getManifestType() != null || StringUtils.isNotBlank(context.getManifestId())
        || StringUtils.isNotBlank(context.getManifestRepoUrl())
        || StringUtils.isNotBlank(context.getManifestRepoName());
  }

  /** An artifact section is considered present when any identifying field is supplied. */
  private static boolean isArtifactPresent(OnboardingContextDTO context) {
    return context.getArtifactType() != null || StringUtils.isNotBlank(context.getArtifactId())
        || StringUtils.isNotBlank(context.getArtifactImagePath());
  }

  /**
   * An infra section is considered present when any identifying field is supplied. Kept broad (checks every infra
   * field) so a request carrying only infra_id (and/or infra_serviceAccountToken) would slip past detection and return
   * 200 with a missing infra rather than a required-field error.
   */
  private static boolean isInfraPresent(OnboardingContextDTO context) {
    return StringUtils.isNotBlank(context.getInfraType()) || StringUtils.isNotBlank(context.getInfraConnectorType())
        || StringUtils.isNotBlank(context.getInfraClusterUrl()) || StringUtils.isNotBlank(context.getInfraNamespace())
        || StringUtils.isNotBlank(context.getInfraId())
        || StringUtils.isNotBlank(context.getInfraServiceAccountToken());
  }

  // ---- Service ----

  private String upsertService(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, OnboardingContextDTO context, Optional<ServiceEntity> existingService,
      ManifestProviderType manifestProvider, ArtifactProviderType artifactProvider, String manifestConnectorRef,
      String artifactConnectorRef) {
    // Build the manifest node(s) and artifact source from the per-source provisioners; null sections stay null so the
    // YAML builder only overlays what the request supplied.
    List<ManifestConfigWrapper> manifests = manifestProvider != null
        ? Collections.singletonList(
              resolveManifestProvisioner(manifestProvider).buildManifest(context, manifestConnectorRef))
        : null;
    ArtifactSource artifactSource = artifactProvider != null
        ? resolveArtifactProvisioner(artifactProvider).buildArtifactSource(context, artifactConnectorRef)
        : null;

    if (existingService.isPresent()) {
      // Update: merge the request into the existing service's YAML, preserving everything else.
      ServiceEntity existing = existingService.get();
      // The merge builder produces V0 (NGServiceConfig) YAML. A simplified (V1) service would be down-converted and
      // re-serialized as V0, and ServiceEntityServiceImpl.update rejects the harnessVersion change with an opaque
      // message. Reject V1 here with a clear one instead.
      if (!HarnessYamlVersion.V0.equals(existing.getHarnessVersion())) {
        throw new InvalidRequestException(
            "Onboarding update supports V0 services only; the existing service uses a simplified (V1) definition");
      }
      String serviceId = existing.getIdentifier();
      // fetchNonEmptyYaml (not getYaml): a V0 service created without YAML has an empty yaml column, and getYaml would
      // return that blank string verbatim, blowing up mergeServiceYaml's parse. fetchNonEmptyYaml rebuilds the YAML
      // from the entity in that case, as at every other read-the-existing-service-YAML site.
      String mergedYaml = OnboardingServiceYamlBuilder.mergeServiceYaml(
          existing.fetchNonEmptyYaml(scopeInfo), context, manifests, artifactSource);
      String serviceName =
          StringUtils.isNotBlank(context.getServiceName()) ? context.getServiceName() : existing.getName();
      ServiceRequestDTO serviceRequestDTO = ServiceRequestDTO.builder()
                                                .identifier(serviceId)
                                                .name(serviceName)
                                                .orgIdentifier(orgIdentifier)
                                                .projectIdentifier(projectIdentifier)
                                                .yaml(mergedYaml)
                                                .build();
      ServiceEntity serviceEntity =
          ServiceElementMapper.toServiceEntity(accountIdentifier, serviceRequestDTO, scopeInfo);
      return serviceEntityService.update(serviceEntity, scopeInfo).getService().getIdentifier();
    }

    // Create: when service_id is absent we generate one so a fresh service is always created.
    String serviceId = StringUtils.isBlank(context.getServiceId())
        ? OnboardingIdentifiers.generateServiceIdentifier(context)
        : OnboardingIdentifiers.sanitizeIdentifier(context.getServiceId());
    String serviceName = StringUtils.isBlank(context.getServiceName()) ? serviceId : context.getServiceName();

    String yaml =
        OnboardingServiceYamlBuilder.buildServiceYaml(context, serviceId, serviceName, manifests, artifactSource);

    ServiceRequestDTO serviceRequestDTO = ServiceRequestDTO.builder()
                                              .identifier(serviceId)
                                              .name(serviceName)
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .yaml(yaml)
                                              .build();

    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(accountIdentifier, serviceRequestDTO, scopeInfo);
    return serviceEntityService.create(serviceEntity, scopeInfo).getService().getIdentifier();
  }

  // ---- Deployment target: K8s connector + environment + infrastructure ----

  /**
   * Provisions the deployment target in dependency order: cluster credential secret, K8s cluster connector,
   * environment that holds the infra, and finally the infrastructure (which references both). The environment is
   * created/reused before the infra because infra create validates that {@code environmentRef} exists.
   */
  private void provisionInfrastructure(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, OnboardingContextDTO context, OnboardingExecuteResponseDTOBuilder response,
      List<String> createdSecrets, InfraProvisioner infraProvisioner) {
    // Resolve the infra id once so the connector id is derived from it: a repeat call for the same infra_id upserts
    // the same connector/secret instead of leaking a fresh connector and credential on every run.
    String infraId = OnboardingIdentifiers.resolveInfraId(context);

    // 1. Cluster credential secret + 2. deployment-target connector (id derived from the infra id by the provisioner).
    String connectorId = infraProvisioner.connectorIdentifier(infraId);
    ConnectorInfoDTO k8sConnector = infraProvisioner.buildConnector(
        provisionContext(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context, createdSecrets),
        connectorId);
    String connectorRef = connectorCreation.upsertConnector(scopeInfo, orgIdentifier, projectIdentifier, k8sConnector);
    response.infraConnectorIdentifier(connectorRef);

    // 3. Environment (create or reuse) — the infra is attached to it.
    String environmentRef = upsertEnvironment(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, context);
    response.environmentIdentifier(environmentRef);

    // 4. Infrastructure — references the environment and connector created above. Upserted so a repeat call for the
    // same infra_id updates in place rather than failing on the duplicate.
    String infrastructureRef = upsertInfrastructure(accountIdentifier, orgIdentifier, projectIdentifier, context,
        infraId, environmentRef, connectorRef, infraProvisioner);
    response.infrastructureIdentifier(infrastructureRef);
  }

  /**
   * Creates the environment when it does not yet exist, or reuses the existing one (the infra is attached either way).
   * The environment is built from fields (no YAML); {@code env_type} defaults to PreProduction. When reusing an
   * existing environment, an explicitly requested {@code env_type} that differs from the existing one is rejected
   * rather than silently ignored, since the environment type drives RBAC scoping and governance.
   */
  private String upsertEnvironment(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, OnboardingContextDTO context) {
    String environmentId = StringUtils.isBlank(context.getEnvId())
        ? OnboardingIdentifiers.generateIdentifier("onboarding_environment")
        : OnboardingIdentifiers.sanitizeIdentifier(context.getEnvId());
    Optional<Environment> existing = environmentService.get(scopeInfo, environmentId, false);
    if (existing.isPresent()) {
      // Reuse the existing environment, but don't silently discard an explicitly requested type that conflicts with
      // it — changing the type would alter RBAC/governance for every other consumer of that environment.
      if (StringUtils.isNotBlank(context.getEnvType())) {
        EnvironmentType requestedType = OnboardingContextNormalizer.resolveEnvironmentType(context.getEnvType());
        if (requestedType != existing.get().getType()) {
          throw new InvalidRequestException(String.format(
              "Environment '%s' already exists with type '%s', which conflicts with the requested env_type '%s'. "
                  + "Use a different env_id or request the existing type.",
              environmentId, existing.get().getType(), requestedType));
        }
      }
      return existing.get().getIdentifier();
    }

    String environmentName = StringUtils.isBlank(context.getEnvName()) ? environmentId : context.getEnvName();
    EnvironmentType environmentType = OnboardingContextNormalizer.resolveEnvironmentType(context.getEnvType());
    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(environmentId)
                                                      .name(environmentName)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .type(environmentType)
                                                      .build();
    Environment environment =
        EnvironmentMapper.toEnvironmentEntity(accountIdentifier, environmentRequestDTO, scopeInfo);
    return environmentService.create(environment, scopeInfo).getEnvironment().getIdentifier();
  }

  private String upsertInfrastructure(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      OnboardingContextDTO context, String infraId, String environmentRef, String connectorRef,
      InfraProvisioner infraProvisioner) {
    String infraName = StringUtils.isBlank(context.getInfraName()) ? infraId : context.getInfraName();
    String releaseName = StringUtils.isNotBlank(context.getInfraReleaseName())
        ? context.getInfraReleaseName()
        : OnboardingIdentifiers.generateReleaseName();

    String yaml = infraProvisioner.buildInfraYaml(
        context, infraId, infraName, orgIdentifier, projectIdentifier, environmentRef, connectorRef, releaseName);

    InfrastructureRequestDTO infrastructureRequestDTO = InfrastructureRequestDTO.builder()
                                                            .identifier(infraId)
                                                            .name(infraName)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .environmentRef(environmentRef)
                                                            .yaml(yaml)
                                                            .build();
    InfrastructureEntity infrastructureEntity =
        InfrastructureMapper.toInfrastructureEntity(accountIdentifier, infrastructureRequestDTO);
    // Upsert (not create) so re-running onboarding for the same infra_id updates the existing infrastructure in place
    // instead of failing on the duplicate, keeping the whole infra path idempotent across retries.
    return infrastructureEntityService.upsert(infrastructureEntity, UpsertOptions.DEFAULT)
        .getInfrastructureEntity()
        .getIdentifier();
  }

  // The registries are populated 1:1 from the resolved provider enums, so a null lookup can only mean a new enum
  // constant was added without its MapBinder binding. Fail with a clear message instead of a bare NPE.
  private ManifestProvisioner resolveManifestProvisioner(ManifestProviderType type) {
    ManifestProvisioner provisioner = manifestProvisioners.get(type);
    if (provisioner == null) {
      throw new InvalidRequestException(String.format("No manifest provisioner is registered for '%s'", type));
    }
    return provisioner;
  }

  private ArtifactProvisioner resolveArtifactProvisioner(ArtifactProviderType type) {
    ArtifactProvisioner provisioner = artifactProvisioners.get(type);
    if (provisioner == null) {
      throw new InvalidRequestException(String.format("No artifact provisioner is registered for '%s'", type));
    }
    return provisioner;
  }

  private InfraProvisioner resolveInfraProvisioner(InfrastructureType type) {
    InfraProvisioner provisioner = infraProvisioners.get(type);
    if (provisioner == null) {
      throw new InvalidRequestException(String.format("No infrastructure provisioner is registered for '%s'", type));
    }
    return provisioner;
  }
}

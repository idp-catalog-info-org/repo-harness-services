/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.imports;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.intfc.DelegateSelectable;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aiagent.AgentCandidate;
import io.harness.delegate.task.aiagent.AgentDescriptor;
import io.harness.delegate.task.aiagent.AgentDiscoveryMode;
import io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams;
import io.harness.delegate.task.aiagent.AgentDiscoveryTaskResponse;
import io.harness.delegate.task.aiagent.AgentPlatform;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.aiagent.dto.AgentCandidateDTO;
import io.harness.ng.core.aiagent.dto.AgentConfigVariableDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentImportRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentImportResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentPlatformDTO;
import io.harness.ng.core.aiagent.dto.AgentScopeDTO;
import io.harness.ng.core.aiagent.dto.AgentServiceRefDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.oidc.aws.delegate.AwsOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.helpers.OidcHelperUtility;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Implementation of AiAgentImportService.
 * Orchestrates agent discovery and import by:
 * 1. Checking feature flag gate
 * 2. Resolving connector and encryption details
 * 3. Submitting AGENT_DISCOVERY_TASK to delegate
 * 4. For import: synthesizing YAML and creating service entity
 */
@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class AiAgentImportServiceImpl implements AiAgentImportService {
  private static final String TASK_TYPE = "AGENT_DISCOVERY_TASK";
  // LIST timeout: paginated enumeration + credential resolution + potential OIDC token exchange
  private static final Duration LIST_TIMEOUT = Duration.ofMinutes(5);
  // DESCRIBE timeout: single cloud API call to fetch agent details
  private static final Duration DESCRIBE_TIMEOUT = Duration.ofMinutes(2);

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject @Named("connectorDecoratorService") private ConnectorService connectorService;
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private AiAgentServiceYamlSynthesizer synthesizer;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private OidcHelperUtility oidcHelperUtility;

  @Override
  public AgentDiscoverResponseDTO discover(String account, String org, String project, AgentDiscoverRequestDTO req) {
    checkFeatureFlag(account);

    BaseNGAccess ngAccess = buildNGAccess(account, org, project);

    // Resolve connector
    ConnectorInfoDTO connectorInfo = resolveConnector(account, org, project, req.getConnectorRef());
    ConnectorConfigDTO connectorConfig = connectorInfo.getConnectorConfig();

    // Get encryption details
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(ngAccess, connectorConfig);

    // Extract delegate selectors
    Set<String> delegateSelectors = extractDelegateSelectors(connectorConfig);

    // Mint OIDC tokens when the connector uses OIDC authentication.
    // getOidcIdTokenForPipelineConfiguration only requires account/org/project — no Ambiance needed.
    OidcTokens oidcTokens = mintOidcTokens(connectorInfo, org, project);

    // Build task params (LIST mode)
    AgentPlatform platform = mapPlatform(req.getPlatform());
    validateConnectorMatchesPlatform(platform, connectorInfo, req.getConnectorRef());
    ResolvedScope scope = resolveScope(platform, req.getScope());
    AgentDiscoveryTaskParams taskParams = AgentDiscoveryTaskParams.builder()
                                              .accountId(account)
                                              .platform(platform)
                                              .mode(AgentDiscoveryMode.LIST)
                                              .connectorDTO(connectorConfig)
                                              .encryptionDetails(encryptionDetails)
                                              .delegateSelectors(delegateSelectors)
                                              .region(scope.region)
                                              .projectId(scope.projectId)
                                              .location(scope.location)
                                              .awsOidcToken(oidcTokens.awsOidcToken)
                                              .gcpOidcDetails(oidcTokens.gcpOidcDetails)
                                              .build();

    // Submit to delegate
    AgentDiscoveryTaskResponse response = submitTask(ngAccess, taskParams, delegateSelectors);

    // Map candidates
    List<AgentCandidateDTO> candidateDTOs = response.getCandidates() != null
        ? response.getCandidates().stream().map(this::mapCandidateToDTO).collect(Collectors.toList())
        : Collections.emptyList();

    return AgentDiscoverResponseDTO.builder().candidates(candidateDTOs).build();
  }

  @Override
  public AgentImportResponseDTO importAgent(String account, String org, String project, AgentImportRequestDTO req) {
    checkFeatureFlag(account);

    BaseNGAccess ngAccess = buildNGAccess(account, org, project);

    // Resolve connector
    ConnectorInfoDTO connectorInfo = resolveConnector(account, org, project, req.getConnectorRef());
    ConnectorConfigDTO connectorConfig = connectorInfo.getConnectorConfig();

    // Get encryption details
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(ngAccess, connectorConfig);

    // Extract delegate selectors
    Set<String> delegateSelectors = extractDelegateSelectors(connectorConfig);

    // Mint OIDC tokens when the connector uses OIDC authentication.
    OidcTokens oidcTokens = mintOidcTokens(connectorInfo, org, project);

    // Build task params (DESCRIBE mode)
    AgentPlatform platform = mapPlatform(req.getPlatform());
    validateConnectorMatchesPlatform(platform, connectorInfo, req.getConnectorRef());
    // DESCRIBE needs the cloud scope just like LIST: the AWS client is region-scoped (agentRuntimeId
    // is not region-qualified) and the GCP client derives its base URL from the location. Without it
    // the delegate builds a client for a null region/location and every import fails.
    ResolvedScope scope = resolveScope(platform, req.getScope());
    AgentDiscoveryTaskParams taskParams = AgentDiscoveryTaskParams.builder()
                                              .accountId(account)
                                              .platform(platform)
                                              .mode(AgentDiscoveryMode.DESCRIBE)
                                              .connectorDTO(connectorConfig)
                                              .encryptionDetails(encryptionDetails)
                                              .delegateSelectors(delegateSelectors)
                                              .cloudId(req.getCloudId())
                                              .region(scope.region)
                                              .projectId(scope.projectId)
                                              .location(scope.location)
                                              .awsOidcToken(oidcTokens.awsOidcToken)
                                              .gcpOidcDetails(oidcTokens.gcpOidcDetails)
                                              .build();

    // Submit to delegate
    AgentDiscoveryTaskResponse response = submitTask(ngAccess, taskParams, delegateSelectors);

    // Extract descriptor
    AgentDescriptor descriptor = response.getDescriptor();
    if (descriptor == null) {
      throw new InvalidRequestException(
          String.format("Agent descriptor not returned for cloudId: %s", req.getCloudId()), WingsException.USER);
    }

    // Determine service name: prefer the requested name, then the descriptor name, and finally the
    // service identifier. The descriptor name can be blank (e.g. an unnamed cloud resource), and a
    // blank service name would fail persistence downstream, so fall back to the always-present
    // identifier as a last resort.
    String serviceName = StringUtils.isNotBlank(req.getService().getName())
        ? req.getService().getName()
        : (StringUtils.isNotBlank(descriptor.getName()) ? descriptor.getName() : req.getService().getIdentifier());

    // Synthesize YAML
    String yaml = synthesizer.synthesize(descriptor, platform, req.getService().getIdentifier(), serviceName);

    // Carry the cloud engine's description onto the imported Harness service. It rides on the
    // descriptor's own field (not a config variable), so it is delivered to the deploy plugin as
    // PLUGIN_HARNESS_AGENT_DESCRIPTION rather than leaking into the container as PLUGIN_CONFIG_*.
    String description = descriptor.getDescription();

    // Persist service
    persistService(account, org, project, req.getService().getIdentifier(), serviceName, description, yaml);

    // Extract config variables
    List<AgentConfigVariableDTO> configVariables = synthesizer.configVariablesFor(descriptor);

    return AgentImportResponseDTO.builder()
        .service(AgentServiceRefDTO.builder()
                     .identifier(req.getService().getIdentifier())
                     .name(serviceName)
                     .description(description)
                     .build())
        .yaml(yaml)
        .configVariables(configVariables)
        .notes(descriptor.getNotes())
        .build();
  }

  // --- Private helpers ---

  private void checkFeatureFlag(String accountId) {
    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)) {
      throw new InvalidRequestException(
          "Feature flag CDS_AGENT_RUNTIME_DEPLOYMENT is disabled for this account", WingsException.USER);
    }
  }

  private BaseNGAccess buildNGAccess(String account, String org, String project) {
    return BaseNGAccess.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
  }

  private ConnectorInfoDTO resolveConnector(String account, String org, String project, String connectorRef) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorRef, account, org, project);

    Optional<ConnectorResponseDTO> connectorResponse = connectorService.get(
        account, identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());

    if (!connectorResponse.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector not found for reference: %s", connectorRef), WingsException.USER);
    }

    ConnectorInfoDTO connectorInfo = connectorResponse.get().getConnector();
    ConnectorType connectorType = connectorInfo.getConnectorType();

    // Validate connector type
    if (connectorType != ConnectorType.AWS && connectorType != ConnectorType.GCP) {
      throw new InvalidRequestException(
          String.format("Connector type %s is not supported for agent import. Expected AWS or GCP.", connectorType),
          WingsException.USER);
    }

    return connectorInfo;
  }

  // Holder for OIDC tokens; null fields mean the connector does not use OIDC.
  private static final class OidcTokens {
    final String awsOidcToken;
    final GcpOidcTokenExchangeDetailsForDelegate gcpOidcDetails;

    OidcTokens(String awsOidcToken, GcpOidcTokenExchangeDetailsForDelegate gcpOidcDetails) {
      this.awsOidcToken = awsOidcToken;
      this.gcpOidcDetails = gcpOidcDetails;
    }
  }

  private OidcTokens mintOidcTokens(ConnectorInfoDTO connectorInfo, String org, String project) {
    ConnectorConfigDTO config = connectorInfo.getConnectorConfig();

    if (config instanceof AwsConnectorDTO) {
      AwsConnectorDTO aws = (AwsConnectorDTO) config;
      if (aws.getCredential() != null
          && aws.getCredential().getAwsCredentialType() == AwsCredentialType.OIDC_AUTHENTICATION) {
        AwsOidcTokenExchangeDetailsForDelegate details =
            (AwsOidcTokenExchangeDetailsForDelegate) oidcHelperUtility.getOidcIdTokenForPipelineConfiguration(
                connectorInfo, org, project, "AGENT_IMPORT");
        String token = details != null ? details.getOidcIdToken() : null;
        return new OidcTokens(token, null);
      }
    } else if (config instanceof GcpConnectorDTO) {
      GcpConnectorDTO gcp = (GcpConnectorDTO) config;
      if (gcp.getCredential() != null
          && gcp.getCredential().getGcpCredentialType() == GcpCredentialType.OIDC_AUTHENTICATION) {
        GcpOidcTokenExchangeDetailsForDelegate details =
            (GcpOidcTokenExchangeDetailsForDelegate) oidcHelperUtility.getOidcIdTokenForPipelineConfiguration(
                connectorInfo, org, project, "AGENT_IMPORT");
        return new OidcTokens(null, details);
      }
    }

    return new OidcTokens(null, null);
  }

  private List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, ConnectorConfigDTO connectorConfig) {
    // Resolve encryption details from the concrete connector credential so the delegate can decrypt secrets.
    if (connectorConfig instanceof AwsConnectorDTO) {
      AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorConfig;
      if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
        return secretManagerClientService.getEncryptionDetails(ngAccess, awsConnectorDTO.getCredential().getConfig());
      }
    } else if (connectorConfig instanceof GcpConnectorDTO) {
      GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorConfig;
      if (gcpConnectorDTO.getCredential() != null && gcpConnectorDTO.getCredential().getConfig() != null) {
        return secretManagerClientService.getEncryptionDetails(ngAccess, gcpConnectorDTO.getCredential().getConfig());
      }
    }
    return Collections.emptyList();
  }

  private Set<String> extractDelegateSelectors(ConnectorConfigDTO connectorConfig) {
    // Selectors live on the concrete connector types (Aws/GcpConnectorDTO), both of which
    // implement DelegateSelectable. Route the discovery task to the delegate(s) the connector
    // is pinned to, so selector-scoped (e.g. VPC-restricted) connectors reach a delegate that
    // can actually access the cloud API instead of falling back to any eligible delegate.
    if (connectorConfig instanceof DelegateSelectable) {
      Set<String> selectors = ((DelegateSelectable) connectorConfig).getDelegateSelectors();
      if (selectors != null) {
        return selectors;
      }
    }
    return Collections.emptySet();
  }

  /**
   * Immutable holder for the cloud scope actually forwarded to the delegate, after per-platform
   * validation and the GCP location fallback have been applied.
   */
  private static final class ResolvedScope {
    private final String region;
    private final String projectId;
    private final String location;

    private ResolvedScope(String region, String projectId, String location) {
      this.region = region;
      this.projectId = projectId;
      this.location = location;
    }
  }

  /**
   * Validates the caller-supplied cloud scope for the target platform and normalises it into the
   * fields the delegate reads. Fails fast (USER error) when a required field is missing so the
   * import returns a clear message instead of the delegate building a client for a null
   * region/location and surfacing an opaque cloud-SDK failure.
   *
   * <p>AWS: {@code region} is required (the AgentCore client is region-scoped and the runtime id is
   * not region-qualified).
   *
   * <p>GCP: {@code projectId} is required, and the location is resolved from {@code location} with a
   * fallback to {@code region} (the schema documents {@code region} as accepting a GCP location);
   * the resolved location must be non-blank because the Vertex client derives its base URL from it.
   */
  private ResolvedScope resolveScope(AgentPlatform platform, AgentScopeDTO scope) {
    String region = scope == null ? null : scope.getRegion();
    String projectId = scope == null ? null : scope.getProjectId();
    String location = scope == null ? null : scope.getLocation();

    if (platform == AgentPlatform.AWS_AGENT_CORE) {
      if (StringUtils.isBlank(region)) {
        throw new InvalidRequestException(
            "Cannot reach the agent: AWS region is required in the request scope (e.g. us-east-1).",
            WingsException.USER);
      }
      return new ResolvedScope(region, null, null);
    }

    if (platform == AgentPlatform.GOOGLE_AGENT_RUNTIME) {
      if (StringUtils.isBlank(projectId)) {
        throw new InvalidRequestException(
            "Cannot reach the agent: GCP projectId is required in the request scope.", WingsException.USER);
      }
      // Honour the documented contract that region may carry a GCP location.
      String resolvedLocation = StringUtils.isNotBlank(location) ? location : region;
      if (StringUtils.isBlank(resolvedLocation)) {
        throw new InvalidRequestException(
            "Cannot reach the agent: GCP location is required in the request scope (e.g. us-central1).",
            WingsException.USER);
      }
      return new ResolvedScope(null, projectId, resolvedLocation);
    }

    throw new InvalidRequestException(String.format("Unsupported platform: %s", platform), WingsException.USER);
  }

  private AgentPlatform mapPlatform(AgentPlatformDTO platformDTO) {
    if (platformDTO == AgentPlatformDTO.AWS_AGENT_CORE) {
      return AgentPlatform.AWS_AGENT_CORE;
    } else if (platformDTO == AgentPlatformDTO.GOOGLE_AGENT_RUNTIME) {
      return AgentPlatform.GOOGLE_AGENT_RUNTIME;
    } else {
      throw new InvalidRequestException(String.format("Unsupported platform: %s", platformDTO), WingsException.USER);
    }
  }

  // resolveConnector only proves the connector is AWS or GCP, not that it matches the requested platform.
  // Without this cross-check an AWS connector paired with GOOGLE_AGENT_RUNTIME (or a GCP connector with
  // AWS_AGENT_CORE) reaches the delegate, where casting the connector config to the platform-specific type
  // fails opaquely (e.g. ClassCastException). Fail fast here with an actionable message instead.
  private void validateConnectorMatchesPlatform(
      AgentPlatform platform, ConnectorInfoDTO connectorInfo, String connectorRef) {
    ConnectorType connectorType = connectorInfo.getConnectorType();
    ConnectorType expectedType = platform == AgentPlatform.AWS_AGENT_CORE ? ConnectorType.AWS : ConnectorType.GCP;
    if (connectorType != expectedType) {
      throw new InvalidRequestException(
          String.format("Connector %s of type %s does not match platform %s. Expected a %s connector.", connectorRef,
              connectorType, platform, expectedType),
          WingsException.USER);
    }
  }

  private AgentDiscoveryTaskResponse submitTask(
      BaseNGAccess ngAccess, TaskParameters taskParams, Set<String> delegateSelectors) {
    // Select timeout based on discovery mode: LIST (paginated, credential exchange) vs DESCRIBE (single API call)
    Duration timeout =
        ((AgentDiscoveryTaskParams) taskParams).getMode() == AgentDiscoveryMode.LIST ? LIST_TIMEOUT : DESCRIBE_TIMEOUT;

    // Build DelegateTaskRequest following AwsResourceServiceHelper pattern
    io.harness.beans.DelegateTaskRequest delegateTaskRequest =
        io.harness.beans.DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(TASK_TYPE)
            .taskParameters(taskParams)
            .executionTimeout(timeout)
            .taskSetupAbstractions(ArtifactUtils.getTaskSetupAbstractions(ngAccess))
            .taskSelectors(delegateSelectors)
            .build();

    try {
      io.harness.delegate.beans.DelegateResponseData responseData =
          delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);

      // Handle error responses
      if (responseData instanceof io.harness.delegate.beans.ErrorNotifyResponseData) {
        io.harness.delegate.beans.ErrorNotifyResponseData errorData =
            (io.harness.delegate.beans.ErrorNotifyResponseData) responseData;
        throw new InvalidRequestException(
            String.format("Delegate task failed: %s", errorData.getErrorMessage()), WingsException.USER);
      }

      // Cast to expected response
      if (!(responseData instanceof AgentDiscoveryTaskResponse)) {
        throw new InvalidRequestException(String.format("Unexpected response type: %s",
                                              responseData == null ? "null" : responseData.getClass().getName()),
            WingsException.USER);
      }

      AgentDiscoveryTaskResponse taskResponse = (AgentDiscoveryTaskResponse) responseData;

      // Check execution status
      if (taskResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        throw new InvalidRequestException(
            String.format("Agent discovery task failed: %s",
                taskResponse.getErrorMessage() != null ? taskResponse.getErrorMessage() : "Unknown error"),
            WingsException.USER);
      }

      return taskResponse;
    } catch (WingsException ex) {
      // The failures above (error response, unexpected type, non-SUCCESS status) are already
      // WingsExceptions with user-facing messages. Rethrow them as-is so the generic catch below
      // does not double-wrap "Agent discovery task execution failed: ..." around a message that
      // already reads "Delegate task failed: ...".
      throw ex;
    } catch (Exception ex) {
      log.error("Failed to execute agent discovery task", ex);
      throw new InvalidRequestException(
          String.format("Agent discovery task execution failed: %s", ex.getMessage()), ex, WingsException.USER);
    }
  }

  private AgentCandidateDTO mapCandidateToDTO(@NotNull AgentCandidate candidate) {
    AgentScopeDTO scope = AgentScopeDTO.builder()
                              .region(candidate.getRegion())
                              .projectId(candidate.getProjectId())
                              .location(candidate.getLocation())
                              .build();

    return AgentCandidateDTO.builder()
        .cloudId(candidate.getCloudId())
        .name(candidate.getName())
        .image(candidate.getImage())
        .identity(candidate.getIdentity())
        .scope(scope)
        .build();
  }

  private void persistService(String account, String org, String project, String serviceIdentifier, String serviceName,
      String description, String yaml) {
    // Resolve ScopeInfo (provides the real parentUniqueId used for persistence and uniqueness checks)
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(account, org, project);

    // Build ServiceRequestDTO
    ServiceRequestDTO serviceRequestDTO = ServiceRequestDTO.builder()
                                              .identifier(serviceIdentifier)
                                              .orgIdentifier(org)
                                              .projectIdentifier(project)
                                              .name(serviceName)
                                              .description(description)
                                              .yaml(yaml)
                                              .build();

    // Convert to ServiceEntity
    ServiceEntity serviceEntity = ServiceElementMapper.toServiceEntity(account, serviceRequestDTO, scopeInfo);

    // Create service (enforces identifier uniqueness internally)
    serviceEntityService.create(serviceEntity, scopeInfo);
  }
}

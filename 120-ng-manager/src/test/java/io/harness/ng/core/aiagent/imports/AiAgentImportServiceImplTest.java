/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.imports;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.task.aiagent.AgentCandidate;
import io.harness.delegate.task.aiagent.AgentDescriptor;
import io.harness.delegate.task.aiagent.AgentDiscoveryTaskResponse;
import io.harness.delegate.task.aiagent.AgentPlatform;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.aiagent.dto.AgentCandidateDTO;
import io.harness.ng.core.aiagent.dto.AgentConfigVariableDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentImportRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentImportResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentPlatformDTO;
import io.harness.ng.core.aiagent.dto.AgentScopeDTO;
import io.harness.ng.core.aiagent.dto.AgentServiceRefDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.oidc.helpers.OidcHelperUtility;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AiAgentImportServiceImplTest extends CategoryTest {
  private static final String ACCOUNT = "accountId";
  private static final String ORG = "orgId";
  private static final String PROJECT = "projectId";
  private static final String CONNECTOR_REF = "account.awsConnector";

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private AiAgentServiceYamlSynthesizer synthesizer;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private OidcHelperUtility oidcHelperUtility;

  @InjectMocks private AiAgentImportServiceImpl service;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverIsInertWhenFeatureFlagOff() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(false);

    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef(CONNECTOR_REF)
                                      .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                      .scope(AgentScopeDTO.builder().region("us-east-1").build())
                                      .build();

    assertThatThrownBy(() -> service.discover(ACCOUNT, ORG, PROJECT, req))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Feature flag CDS_AGENT_RUNTIME_DEPLOYMENT is disabled");

    verifyNoInteractions(delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void importAgentIsInertWhenFeatureFlagOff() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(false);

    AgentImportRequestDTO req = AgentImportRequestDTO.builder()
                                    .connectorRef(CONNECTOR_REF)
                                    .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                    .cloudId("agent-123")
                                    .service(AgentServiceRefDTO.builder().identifier("testService").build())
                                    .build();

    assertThatThrownBy(() -> service.importAgent(ACCOUNT, ORG, PROJECT, req))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Feature flag CDS_AGENT_RUNTIME_DEPLOYMENT is disabled");

    verifyNoInteractions(delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverMapsCandidatesToDTO() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // Mock connector resolution
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig())
        .thenReturn(mock(io.harness.delegate.beans.connector.ConnectorConfigDTO.class));
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // Mock delegate response
    AgentCandidate candidate = AgentCandidate.builder()
                                   .cloudId("agent-123")
                                   .name("test-agent")
                                   .image("bedrock/agent:v1")
                                   .identity("arn:aws:iam::123456789012:role/AgentRole")
                                   .region("us-east-1")
                                   .build();

    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.singletonList(candidate))
                                                  .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // Execute
    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef(CONNECTOR_REF)
                                      .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                      .scope(AgentScopeDTO.builder().region("us-east-1").build())
                                      .build();

    AgentDiscoverResponseDTO response = service.discover(ACCOUNT, ORG, PROJECT, req);

    // Verify
    assertThat(response.getCandidates()).hasSize(1);
    AgentCandidateDTO dto = response.getCandidates().get(0);
    assertThat(dto.getCloudId()).isEqualTo("agent-123");
    assertThat(dto.getName()).isEqualTo("test-agent");
    assertThat(dto.getImage()).isEqualTo("bedrock/agent:v1");
    assertThat(dto.getIdentity()).isEqualTo("arn:aws:iam::123456789012:role/AgentRole");
    assertThat(dto.getScope().getRegion()).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void importAgentCreatesServiceWithCorrectIdentifier() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // Mock connector resolution
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig())
        .thenReturn(mock(io.harness.delegate.beans.connector.ConnectorConfigDTO.class));
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // Mock delegate DESCRIBE response
    Map<String, String> configVars = new HashMap<>();
    configVars.put("PORT", "8080");

    AgentDescriptor descriptor = AgentDescriptor.builder()
                                     .cloudId("agent-123")
                                     .name("test-agent")
                                     .description("Imported agent description")
                                     .image("bedrock/agent:v1")
                                     .identity("arn:aws:iam::123456789012:role/AgentRole")
                                     .reconcilePinKey("agentName")
                                     .reconcilePinValue("test-agent-resource")
                                     .configVariables(configVars)
                                     .envVars(Arrays.asList("ENV=prod"))
                                     .notes(Arrays.asList("Warning: test note"))
                                     .build();

    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .descriptor(descriptor)
                                                  .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // Mock synthesizer
    String expectedYaml = "service:\n  name: test-agent\n  identifier: testService\n";
    when(synthesizer.synthesize(eq(descriptor), eq(AgentPlatform.AWS_AGENT_CORE), eq("testService"), eq("test-agent")))
        .thenReturn(expectedYaml);

    AgentConfigVariableDTO configVar =
        AgentConfigVariableDTO.builder().name("agentName").value("test-agent-resource").build();
    when(synthesizer.configVariablesFor(descriptor)).thenReturn(Collections.singletonList(configVar));

    // Mock scope resolution
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT)
                              .orgIdentifier(ORG)
                              .projectIdentifier(PROJECT)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("scope-unique-id")
                              .build();
    when(scopeInfoService.getScopeInfo(ACCOUNT, ORG, PROJECT)).thenReturn(scopeInfo);

    // Mock service creation (return mock response)
    when(serviceEntityService.create(any(ServiceEntity.class), any(ScopeInfo.class)))
        .thenReturn(mock(io.harness.ng.core.service.entity.ServiceGovernanceDataResponse.class));

    // Execute
    AgentImportRequestDTO req =
        AgentImportRequestDTO.builder()
            .connectorRef(CONNECTOR_REF)
            .platform(AgentPlatformDTO.AWS_AGENT_CORE)
            .cloudId("agent-123")
            .scope(AgentScopeDTO.builder().region("us-east-1").build())
            .service(AgentServiceRefDTO.builder().identifier("testService").name("test-agent").build())
            .build();

    AgentImportResponseDTO response = service.importAgent(ACCOUNT, ORG, PROJECT, req);

    // Verify response
    assertThat(response.getYaml()).isEqualTo(expectedYaml);
    assertThat(response.getConfigVariables()).hasSize(1);
    assertThat(response.getConfigVariables().get(0).getName()).isEqualTo("agentName");
    assertThat(response.getNotes()).containsExactly("Warning: test note");
    assertThat(response.getService().getIdentifier()).isEqualTo("testService");

    // Verify serviceEntityService.create was called with correct identifier
    ArgumentCaptor<ServiceEntity> entityCaptor = ArgumentCaptor.forClass(ServiceEntity.class);
    verify(serviceEntityService).create(entityCaptor.capture(), any(ScopeInfo.class));

    ServiceEntity capturedEntity = entityCaptor.getValue();
    assertThat(capturedEntity.getIdentifier()).isEqualTo("testService");
    // The cloud engine description (carried on the descriptor's own field) is set on the service.
    assertThat(capturedEntity.getDescription()).isEqualTo("Imported agent description");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void importAgentThreadsScopeIntoDescribeTaskParams() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig())
        .thenReturn(mock(io.harness.delegate.beans.connector.ConnectorConfigDTO.class));
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    AgentDescriptor descriptor = AgentDescriptor.builder().cloudId("agent-123").name("test-agent").build();
    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .descriptor(descriptor)
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);
    // persistService parses this YAML and validates the identifier matches the service ref.
    when(synthesizer.synthesize(any(), any(), any(), any()))
        .thenReturn("service:\n  name: test-agent\n  identifier: testService\n");
    when(synthesizer.configVariablesFor(any())).thenReturn(Collections.emptyList());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT)
                              .orgIdentifier(ORG)
                              .projectIdentifier(PROJECT)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("scope-unique-id")
                              .build();
    when(scopeInfoService.getScopeInfo(ACCOUNT, ORG, PROJECT)).thenReturn(scopeInfo);
    when(serviceEntityService.create(any(ServiceEntity.class), any(ScopeInfo.class)))
        .thenReturn(mock(io.harness.ng.core.service.entity.ServiceGovernanceDataResponse.class));

    // Import request carries the cloud scope (region), which the DESCRIBE task must forward so the
    // delegate can build a region-scoped AWS client. Without it every import fails.
    AgentImportRequestDTO req =
        AgentImportRequestDTO.builder()
            .connectorRef(CONNECTOR_REF)
            .platform(AgentPlatformDTO.AWS_AGENT_CORE)
            .cloudId("agent-123")
            .scope(AgentScopeDTO.builder().region("us-east-1").build())
            .service(AgentServiceRefDTO.builder().identifier("testService").name("test-agent").build())
            .build();

    service.importAgent(ACCOUNT, ORG, PROJECT, req);

    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());
    io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams params =
        (io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams) requestCaptor.getValue().getTaskParameters();
    assertThat(params.getRegion()).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void importAgentFailsFastWhenAwsRegionMissing() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig())
        .thenReturn(mock(io.harness.delegate.beans.connector.ConnectorConfigDTO.class));
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // AWS import with no region in scope must fail before dispatching a delegate task.
    AgentImportRequestDTO req = AgentImportRequestDTO.builder()
                                    .connectorRef(CONNECTOR_REF)
                                    .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                    .cloudId("agent-123")
                                    .service(AgentServiceRefDTO.builder().identifier("testService").build())
                                    .build();

    assertThatThrownBy(() -> service.importAgent(ACCOUNT, ORG, PROJECT, req))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AWS region is required");

    verifyNoInteractions(delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverFailsFastWhenGcpProjectIdMissing() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    io.harness.delegate.beans.connector.GcpConnectorDTO gcpConnector =
        io.harness.delegate.beans.connector.GcpConnectorDTO.builder().build();
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.GCP);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(gcpConnector);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // GCP discover with a location but no projectId must fail fast.
    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef("account.gcpConnector")
                                      .platform(AgentPlatformDTO.GOOGLE_AGENT_RUNTIME)
                                      .scope(AgentScopeDTO.builder().location("us-central1").build())
                                      .build();

    assertThatThrownBy(() -> service.discover(ACCOUNT, ORG, PROJECT, req))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("GCP projectId is required");

    verifyNoInteractions(delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverResolvesGcpLocationFromRegionFallback() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    io.harness.delegate.beans.connector.GcpConnectorDTO gcpConnector =
        io.harness.delegate.beans.connector.GcpConnectorDTO.builder().build();
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.GCP);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(gcpConnector);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.emptyList())
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // GCP scope carries the location only in the `region` field; the schema documents region as a
    // valid GCP-location alias, so the service must forward it as the delegate's location.
    AgentDiscoverRequestDTO req =
        AgentDiscoverRequestDTO.builder()
            .connectorRef("account.gcpConnector")
            .platform(AgentPlatformDTO.GOOGLE_AGENT_RUNTIME)
            .scope(AgentScopeDTO.builder().projectId("my-project").region("us-central1").build())
            .build();

    service.discover(ACCOUNT, ORG, PROJECT, req);

    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());
    io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams params =
        (io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams) requestCaptor.getValue().getTaskParameters();
    assertThat(params.getProjectId()).isEqualTo("my-project");
    assertThat(params.getLocation()).isEqualTo("us-central1");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverRoutesTaskToConnectorDelegateSelectors() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // A GCP connector pinned to specific delegate selectors.
    io.harness.delegate.beans.connector.GcpConnectorDTO gcpConnector =
        io.harness.delegate.beans.connector.GcpConnectorDTO.builder()
            .delegateSelectors(new java.util.HashSet<>(Arrays.asList("gcp-delegate", "prod")))
            .build();

    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.GCP);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(gcpConnector);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.emptyList())
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef("account.gcpConnector")
                                      .platform(AgentPlatformDTO.GOOGLE_AGENT_RUNTIME)
                                      .scope(AgentScopeDTO.builder().projectId("p").location("us-central1").build())
                                      .build();

    service.discover(ACCOUNT, ORG, PROJECT, req);

    // The discovery task carries the connector's delegate selectors so it lands on a reachable delegate.
    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getTaskSelectors()).containsExactlyInAnyOrder("gcp-delegate", "prod");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverThreadsAwsOidcTokenInTaskParams() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // Mock AWS OIDC connector
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    io.harness.delegate.beans.connector.AwsConnectorDTO awsConfig =
        io.harness.delegate.beans.connector.AwsConnectorDTO.builder()
            .credential(io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO.builder()
                            .awsCredentialType(
                                io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.OIDC_AUTHENTICATION)
                            .build())
            .build();

    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(awsConfig);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // Mock OIDC token minting: return a token
    io.harness.oidc.aws.delegate.AwsOidcTokenExchangeDetailsForDelegate oidcDetails =
        io.harness.oidc.aws.delegate.AwsOidcTokenExchangeDetailsForDelegate.builder()
            .oidcIdToken("eyJ.aws.oidc.token")
            .build();
    when(oidcHelperUtility.getOidcIdTokenForPipelineConfiguration(any(), any(), any(), any())).thenReturn(oidcDetails);

    // Mock empty candidate response
    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.emptyList())
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // Execute
    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef(CONNECTOR_REF)
                                      .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                      .scope(AgentScopeDTO.builder().region("us-east-1").build())
                                      .build();

    service.discover(ACCOUNT, ORG, PROJECT, req);

    // Verify: the task params include the minted OIDC token
    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());

    io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams taskParams =
        (io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams) requestCaptor.getValue().getTaskParameters();
    assertThat(taskParams.getAwsOidcToken()).isEqualTo("eyJ.aws.oidc.token");
    assertThat(taskParams.getGcpOidcDetails()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverThreadsGcpOidcDetailsInTaskParams() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // Mock GCP OIDC connector
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    io.harness.delegate.beans.connector.GcpConnectorDTO gcpConfig =
        io.harness.delegate.beans.connector.GcpConnectorDTO.builder()
            .credential(io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(
                                io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.OIDC_AUTHENTICATION)
                            .build())
            .build();

    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.GCP);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(gcpConfig);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // Mock OIDC token minting: return GCP exchange details
    io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate gcpOidcDetails =
        io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate.builder()
            .oidcIdToken("eyJ.gcp.oidc.token")
            .build();
    when(oidcHelperUtility.getOidcIdTokenForPipelineConfiguration(any(), any(), any(), any()))
        .thenReturn(gcpOidcDetails);

    // Mock empty candidate response
    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.emptyList())
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // Execute
    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef(CONNECTOR_REF)
                                      .platform(AgentPlatformDTO.GOOGLE_AGENT_RUNTIME)
                                      .scope(AgentScopeDTO.builder().projectId("p").location("us-central1").build())
                                      .build();

    service.discover(ACCOUNT, ORG, PROJECT, req);

    // Verify: the task params include the GCP OIDC details
    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());

    io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams taskParams =
        (io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams) requestCaptor.getValue().getTaskParameters();
    assertThat(taskParams.getGcpOidcDetails()).isNotNull();
    assertThat(taskParams.getGcpOidcDetails().getOidcIdToken()).isEqualTo("eyJ.gcp.oidc.token");
    assertThat(taskParams.getAwsOidcToken()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void discoverSendsNullOidcFieldsForNonOidcConnector() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT, FeatureName.CDS_AGENT_RUNTIME_DEPLOYMENT)).thenReturn(true);

    // Mock non-OIDC (INHERIT_FROM_DELEGATE) connector
    io.harness.connector.ConnectorResponseDTO connectorResponseDTO =
        mock(io.harness.connector.ConnectorResponseDTO.class);
    io.harness.connector.ConnectorInfoDTO connectorInfoDTO = mock(io.harness.connector.ConnectorInfoDTO.class);
    io.harness.delegate.beans.connector.AwsConnectorDTO awsConfig =
        io.harness.delegate.beans.connector.AwsConnectorDTO.builder()
            .credential(
                io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO.builder()
                    .awsCredentialType(
                        io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE)
                    .build())
            .build();

    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorType()).thenReturn(io.harness.delegate.beans.connector.utils.ConnectorType.AWS);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(awsConfig);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));

    // Mock empty candidate response
    AgentDiscoveryTaskResponse taskResponse = AgentDiscoveryTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .candidates(Collections.emptyList())
                                                  .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(taskResponse);

    // Execute
    AgentDiscoverRequestDTO req = AgentDiscoverRequestDTO.builder()
                                      .connectorRef(CONNECTOR_REF)
                                      .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                      .scope(AgentScopeDTO.builder().region("us-east-1").build())
                                      .build();

    service.discover(ACCOUNT, ORG, PROJECT, req);

    // Verify: the task params have null OIDC fields (no token minting for non-OIDC connectors)
    ArgumentCaptor<io.harness.beans.DelegateTaskRequest> requestCaptor =
        ArgumentCaptor.forClass(io.harness.beans.DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());

    io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams taskParams =
        (io.harness.delegate.task.aiagent.AgentDiscoveryTaskParams) requestCaptor.getValue().getTaskParameters();
    assertThat(taskParams.getAwsOidcToken()).isNull();
    assertThat(taskParams.getGcpOidcDetails()).isNull();
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import static io.harness.rule.OwnerRule.KAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.infrastructure.dto.DiscoveryStatusDTO;
import io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO;
import io.harness.ng.core.infrastructure.dto.ServiceDiscoveryEnableResponseDTO.ItemResult;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryAgentResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryResponseDTO;
import io.harness.servicediscovery.client.remote.ServiceDiscoveryHttpClient;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
public class DiscoveryOrchestratorImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String ENV_ID = "testEnv";
  private static final String CONNECTOR_REF = "account.k8sConnector";

  @Mock private ServiceDiscoveryHttpClient serviceDiscoveryHttpClient;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private HPersistence hPersistence;

  @Spy @InjectMocks private DiscoveryOrchestratorImpl orchestrator;

  private String infraYaml;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    infraYaml = "infrastructureDefinition:\n"
        + "  name: infra1\n"
        + "  identifier: infra1\n"
        + "  orgIdentifier: " + ORG_ID + "\n"
        + "  projectIdentifier: " + PROJECT_ID + "\n"
        + "  environmentRef: " + ENV_ID + "\n"
        + "  deploymentType: Kubernetes\n"
        + "  type: KubernetesDirect\n"
        + "  spec:\n"
        + "    connectorRef: " + CONNECTOR_REF + "\n"
        + "    namespace: default\n"
        + "    releaseName: release-<+INFRA_KEY_SHORT_ID>\n"
        + "  allowSimultaneousDeployments: false\n";
  }

  private InfrastructureEntity buildInfra(String identifier, boolean sdEnabled) {
    return InfrastructureEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .envIdentifier(ENV_ID)
        .identifier(identifier)
        .name(identifier)
        .serviceDiscoveryEnabled(sdEnabled)
        .yaml(infraYaml)
        .build();
  }

  @SuppressWarnings("unchecked")
  private void mockLookupAgent(ServiceDiscoveryAgentResponse agentResponse) throws IOException {
    Call<ServiceDiscoveryResponseDTO<ServiceDiscoveryAgentResponse>> call = mock(Call.class);
    when(serviceDiscoveryHttpClient.listAgents(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    if (agentResponse != null) {
      ServiceDiscoveryResponseDTO<ServiceDiscoveryAgentResponse> responseDTO =
          ServiceDiscoveryResponseDTO.<ServiceDiscoveryAgentResponse>builder()
              .items(Collections.singletonList(agentResponse))
              .build();
      when(call.execute()).thenReturn(Response.success(responseDTO));
    } else {
      ServiceDiscoveryResponseDTO<ServiceDiscoveryAgentResponse> emptyDTO =
          ServiceDiscoveryResponseDTO.<ServiceDiscoveryAgentResponse>builder().items(Collections.emptyList()).build();
      when(call.execute()).thenReturn(Response.success(emptyDTO));
    }
  }

  @SuppressWarnings("unchecked")
  private void mockCreateAgent(ServiceDiscoveryAgentResponse agentResponse) throws IOException {
    Call<ServiceDiscoveryAgentResponse> call = mock(Call.class);
    when(serviceDiscoveryHttpClient.createAgent(anyString(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(agentResponse));
  }

  @SuppressWarnings("unchecked")
  private void mockHPersistenceQuery(List<InfrastructureEntity> results) {
    Query<InfrastructureEntity> query = mock(Query.class);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(hPersistence.createQuery(InfrastructureEntity.class)).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.in(any())).thenReturn(query);
    when(fieldEnd.notEqual(any())).thenReturn(query);
    when(query.asList()).thenReturn(results);

    UpdateOperations<InfrastructureEntity> updateOps = mock(UpdateOperations.class);
    when(hPersistence.createUpdateOperations(InfrastructureEntity.class)).thenReturn(updateOps);
    when(updateOps.set(anyString(), any())).thenReturn(updateOps);
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testEnableAll_mixedFoundAndMissing() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", true);
    mockHPersistenceQuery(Collections.singletonList(infra1));
    mockLookupAgent(null);

    ServiceDiscoveryAgentResponse createdAgent =
        ServiceDiscoveryAgentResponse.builder().identity("harness-discovery-abc123").status("HEALTHY").build();
    mockCreateAgent(createdAgent);

    ServiceDiscoveryEnableResponseDTO response =
        orchestrator.enableAll(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, Arrays.asList("infra1", "missingInfra"));

    assertThat(response).isNotNull();
    assertThat(response.getSummary().getRequested()).isEqualTo(2);
    assertThat(response.getSummary().getFailed()).isEqualTo(1);
    assertThat(response.getSummary().getSucceeded()).isEqualTo(1);

    ItemResult missingResult =
        response.getResults().stream().filter(r -> "missingInfra".equals(r.getInfraIdentifier())).findFirst().get();
    assertThat(missingResult.getStatus()).isEqualTo("FAILED");
    assertThat(missingResult.getError()).isEqualTo("Infrastructure not found");

    ItemResult foundResult =
        response.getResults().stream().filter(r -> "infra1".equals(r.getInfraIdentifier())).findFirst().get();
    assertThat(foundResult.getStatus()).isEqualTo("SUCCESS");
    assertThat(foundResult.getAgent()).isNotNull();
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testEnableAll_agentCreationFails() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", false);
    mockHPersistenceQuery(Collections.singletonList(infra1));
    mockLookupAgent(null);

    ServiceDiscoveryAgentResponse failedAgent =
        ServiceDiscoveryAgentResponse.builder().identity("harness-discovery-abc123").build();
    mockCreateAgent(failedAgent);

    doReturn(DiscoveryStatusDTO.builder().agentId("harness-discovery-abc123").state("FAILED").build())
        .when(orchestrator)
        .enable(any(InfrastructureEntity.class));

    ServiceDiscoveryEnableResponseDTO response =
        orchestrator.enableAll(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, Collections.singletonList("infra1"));

    assertThat(response.getSummary().getRequested()).isEqualTo(1);
    assertThat(response.getSummary().getFailed()).isEqualTo(1);
    assertThat(response.getSummary().getSucceeded()).isEqualTo(0);

    ItemResult result = response.getResults().get(0);
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getError()).isEqualTo("Agent creation failed");
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testEnableAll_exceptionDuringEnable() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", true);
    mockHPersistenceQuery(Collections.singletonList(infra1));

    doThrow(new RuntimeException("SD service unreachable")).when(orchestrator).enable(any(InfrastructureEntity.class));

    ServiceDiscoveryEnableResponseDTO response =
        orchestrator.enableAll(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, Collections.singletonList("infra1"));

    assertThat(response.getSummary().getFailed()).isEqualTo(1);
    assertThat(response.getSummary().getSucceeded()).isEqualTo(0);

    ItemResult result = response.getResults().get(0);
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getError()).isEqualTo("SD service unreachable");
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testReconcile_connectorChange_tearsDownOldAndCreatesNew() throws IOException {
    String oldConnector = "account.oldConnector";
    String newConnector = "account.newConnector";

    String oldYaml = infraYaml.replace(CONNECTOR_REF, oldConnector);
    String newYaml = infraYaml.replace(CONNECTOR_REF, newConnector);

    InfrastructureEntity oldInfra = buildInfra("infra1", true);
    oldInfra.setYaml(oldYaml);
    InfrastructureEntity newInfra = buildInfra("infra1", true);
    newInfra.setYaml(newYaml);

    mockHPersistenceQuery(Collections.emptyList());
    mockLookupAgent(null);

    ServiceDiscoveryAgentResponse createdAgent =
        ServiceDiscoveryAgentResponse.builder().identity("harness-discovery-new123").status("HEALTHY").build();
    mockCreateAgent(createdAgent);

    DiscoveryStatusDTO result = orchestrator.reconcile(oldInfra, newInfra);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testReconcile_disableFlow() throws IOException {
    InfrastructureEntity oldInfra = buildInfra("infra1", true);
    InfrastructureEntity newInfra = buildInfra("infra1", false);
    newInfra.setServiceDiscoveryEnabled(false);

    mockHPersistenceQuery(Collections.emptyList());
    mockLookupAgent(null);

    DiscoveryStatusDTO result = orchestrator.reconcile(oldInfra, newInfra);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testDisable_withSiblings_skipsAgentDeletion() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", true);
    InfrastructureEntity sibling = buildInfra("infra2", true);

    mockHPersistenceQuery(Collections.singletonList(sibling));

    orchestrator.disable(infra1);

    verify(serviceDiscoveryHttpClient, never())
        .deleteAgent(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testDisable_noSiblings_deletesAgent() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", true);

    mockHPersistenceQuery(Collections.emptyList());
    mockLookupAgent(null);

    orchestrator.disable(infra1);

    verify(infrastructureEntityService, never())
        .delete(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyBoolean());
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testHasSiblingsWithSameAgent_excludesPseudoInfra() throws IOException {
    InfrastructureEntity infra1 = buildInfra("infra1", true);
    String agentIdentity = DiscoveryOrchestratorImpl.buildAgentIdentity(CONNECTOR_REF);

    InfrastructureEntity pseudoInfra = buildInfra(agentIdentity, true);

    mockHPersistenceQuery(Collections.singletonList(pseudoInfra));
    mockLookupAgent(null);

    orchestrator.disable(infra1);
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testBuildAgentIdentity_deterministic() {
    String identity1 = DiscoveryOrchestratorImpl.buildAgentIdentity("account.k8sConnector");
    String identity2 = DiscoveryOrchestratorImpl.buildAgentIdentity("account.k8sConnector");

    assertThat(identity1).isEqualTo(identity2);
    assertThat(identity1).startsWith("harness-discovery-");
    assertThat(identity1).hasSize("harness-discovery-".length() + 12);
  }

  @Test
  @Owner(developers = KAVYA)
  @Category(UnitTests.class)
  public void testBuildAgentIdentity_differentConnectors() {
    String identity1 = DiscoveryOrchestratorImpl.buildAgentIdentity("account.connector1");
    String identity2 = DiscoveryOrchestratorImpl.buildAgentIdentity("account.connector2");

    assertThat(identity1).isNotEqualTo(identity2);
  }
}

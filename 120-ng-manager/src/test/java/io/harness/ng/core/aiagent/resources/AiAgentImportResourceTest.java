/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.resources;

import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.ACCESS_CONNECTOR_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.CONNECTOR;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.aiagent.dto.AgentDiscoverRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentImportRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentImportResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentPlatformDTO;
import io.harness.ng.core.aiagent.dto.AgentScopeDTO;
import io.harness.ng.core.aiagent.dto.AgentServiceRefDTO;
import io.harness.ng.core.aiagent.imports.AiAgentImportService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AiAgentImportResourceTest extends CategoryTest {
  private AiAgentImportResource resource;
  private AiAgentImportService importService;
  private AccessControlClient accessControlClient;

  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org456";
  private static final String PROJECT_ID = "project789";

  @Before
  public void setUp() {
    importService = mock(AiAgentImportService.class);
    accessControlClient = mock(AccessControlClient.class);
    resource = new AiAgentImportResource(importService, accessControlClient);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testDiscoverReturnsAgents() {
    // Given
    AgentDiscoverRequestDTO request = AgentDiscoverRequestDTO.builder()
                                          .connectorRef("aws-conn")
                                          .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                          .scope(AgentScopeDTO.builder().region("us-west-2").build())
                                          .build();

    AgentDiscoverResponseDTO serviceResponse =
        AgentDiscoverResponseDTO.builder().candidates(Collections.emptyList()).build();

    when(importService.discover(ACCOUNT_ID, ORG_ID, PROJECT_ID, request)).thenReturn(serviceResponse);

    // When
    ResponseDTO<AgentDiscoverResponseDTO> response = resource.discover(ACCOUNT_ID, ORG_ID, PROJECT_ID, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(serviceResponse);
    verify(accessControlClient)
        .checkForAccessOrThrow(
            ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION);
    // The caller must also have access to the connector whose credentials discovery will use.
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), Resource.of(CONNECTOR, "aws-conn"),
            ACCESS_CONNECTOR_PERMISSION);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testImportAgentEnforcesRBAC() {
    // Given
    AgentImportRequestDTO request = AgentImportRequestDTO.builder()
                                        .connectorRef("aws-conn")
                                        .platform(AgentPlatformDTO.AWS_AGENT_CORE)
                                        .cloudId("agent-123")
                                        .service(AgentServiceRefDTO.builder().identifier("imported-agent").build())
                                        .build();

    AgentImportResponseDTO serviceResponse =
        AgentImportResponseDTO.builder()
            .service(AgentServiceRefDTO.builder().identifier("imported-agent").build())
            .yaml("service:\n  identifier: imported-agent")
            .build();

    when(importService.importAgent(ACCOUNT_ID, ORG_ID, PROJECT_ID, request)).thenReturn(serviceResponse);

    // When
    ResponseDTO<AgentImportResponseDTO> response = resource.importAgent(ACCOUNT_ID, ORG_ID, PROJECT_ID, request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(serviceResponse);
    verify(accessControlClient)
        .checkForAccessOrThrow(
            ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    // The caller must also have access to the connector whose credentials import will use.
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), Resource.of(CONNECTOR, "aws-conn"),
            ACCESS_CONNECTOR_PERMISSION);
  }
}

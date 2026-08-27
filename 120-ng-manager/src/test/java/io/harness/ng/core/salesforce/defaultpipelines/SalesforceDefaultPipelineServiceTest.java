/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.SalesforceDefaultPipelineDTO;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDP)
public class SalesforceDefaultPipelineServiceTest {
  @InjectMocks SalesforceDefaultPipelineService service;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock EnvironmentService environmentService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ScopeInfoService scopeInfoService;

  @Mock Call<PipelineGetResponseBody> getPipelineCall;
  @Mock Call<PipelineCreateResponseBody> createPipelineCall;

  private static final String ACCOUNT = "testAccount";
  private static final String ORG = "testOrg";
  private static final String PROJECT = "testProject";
  private static final String ENV_IDENTIFIER = "salesforce_runtime";
  private static final String INFRA_IDENTIFIER = "salesforce_runtime";

  private ScopeInfo scopeInfo;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT)
                    .orgIdentifier(ORG)
                    .projectIdentifier(PROJECT)
                    .uniqueId("uniqueId")
                    .build();
    when(scopeInfoService.getScopeInfo(ACCOUNT, ORG, PROJECT)).thenReturn(scopeInfo);
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_whenNoPipelinesExist_createsAll() {
    when(environmentService.get(ACCOUNT, ORG, PROJECT, ENV_IDENTIFIER, false)).thenReturn(Optional.empty());
    when(environmentService.create(any(Environment.class), any(ScopeInfo.class)))
        .thenReturn(mock(EnvironmentGovernanceDataResponse.class));
    when(infrastructureEntityService.get(ACCOUNT, ORG, PROJECT, scopeInfo, ENV_IDENTIFIER, INFRA_IDENTIFIER))
        .thenReturn(Optional.empty());
    when(infrastructureEntityService.create(any())).thenReturn(mock(InfrastructureGovernanceDataResponse.class));

    when(pipelineServiceClient.getPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getPipelineCall);
    when(pipelineServiceClient.createPipeline(any(), any(), any(), any())).thenReturn(createPipelineCall);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(getPipelineCall))
          .thenThrow(new RuntimeException("pipeline not found"));
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(createPipelineCall))
          .thenReturn(new PipelineCreateResponseBody());

      List<SalesforceDefaultPipelineDTO> results = service.createDefaultPipelines(ACCOUNT, ORG, PROJECT);

      assertThat(results).hasSize(7);
      assertThat(results)
          .extracting(SalesforceDefaultPipelineDTO::getStatus)
          .containsOnly(SalesforceDefaultPipelineDTO.StatusEnum.CREATED);
    }
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_whenAllPipelinesExist_returnsAlreadyExists() {
    when(environmentService.get(ACCOUNT, ORG, PROJECT, ENV_IDENTIFIER, false))
        .thenReturn(Optional.of(mock(Environment.class)));
    when(infrastructureEntityService.get(ACCOUNT, ORG, PROJECT, scopeInfo, ENV_IDENTIFIER, INFRA_IDENTIFIER))
        .thenReturn(Optional.of(mock(io.harness.ng.core.infrastructure.entity.InfrastructureEntity.class)));

    when(pipelineServiceClient.getPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getPipelineCall);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(getPipelineCall)).thenReturn(new PipelineGetResponseBody());

      List<SalesforceDefaultPipelineDTO> results = service.createDefaultPipelines(ACCOUNT, ORG, PROJECT);

      assertThat(results).hasSize(7);
      assertThat(results)
          .extracting(SalesforceDefaultPipelineDTO::getStatus)
          .containsOnly(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
      verify(pipelineServiceClient, never()).createPipeline(any(), any(), any(), any());
    }
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_whenCreateFails_returnsFailed() {
    when(environmentService.get(ACCOUNT, ORG, PROJECT, ENV_IDENTIFIER, false)).thenReturn(Optional.empty());
    when(environmentService.create(any(Environment.class), any(ScopeInfo.class)))
        .thenThrow(new RuntimeException("env create failed"));
    when(infrastructureEntityService.get(ACCOUNT, ORG, PROJECT, scopeInfo, ENV_IDENTIFIER, INFRA_IDENTIFIER))
        .thenReturn(Optional.empty());
    when(infrastructureEntityService.create(any())).thenThrow(new RuntimeException("infra create failed"));

    when(pipelineServiceClient.getPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getPipelineCall);
    when(pipelineServiceClient.createPipeline(any(), any(), any(), any())).thenReturn(createPipelineCall);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(getPipelineCall))
          .thenThrow(new RuntimeException("pipeline not found"));
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(createPipelineCall))
          .thenThrow(new RuntimeException("invalid yaml"));

      List<SalesforceDefaultPipelineDTO> results = service.createDefaultPipelines(ACCOUNT, ORG, PROJECT);

      assertThat(results).hasSize(7);
      assertThat(results)
          .extracting(SalesforceDefaultPipelineDTO::getStatus)
          .containsOnly(SalesforceDefaultPipelineDTO.StatusEnum.FAILED);
      assertThat(results).extracting(SalesforceDefaultPipelineDTO::getErrorMessage).doesNotContainNull();
    }
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_envAndInfraAlreadyExist_pipelinesCreated() {
    when(environmentService.get(ACCOUNT, ORG, PROJECT, ENV_IDENTIFIER, false))
        .thenReturn(Optional.of(mock(Environment.class)));
    when(infrastructureEntityService.get(ACCOUNT, ORG, PROJECT, scopeInfo, ENV_IDENTIFIER, INFRA_IDENTIFIER))
        .thenReturn(Optional.of(mock(io.harness.ng.core.infrastructure.entity.InfrastructureEntity.class)));

    when(pipelineServiceClient.getPipeline(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getPipelineCall);
    when(pipelineServiceClient.createPipeline(any(), any(), any(), any())).thenReturn(createPipelineCall);

    try (MockedStatic<NGRestUtils> ngRestUtils = mockStatic(NGRestUtils.class)) {
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(getPipelineCall))
          .thenThrow(new RuntimeException("pipeline not found"));
      ngRestUtils.when(() -> NGRestUtils.getGeneralResponse(createPipelineCall))
          .thenReturn(new PipelineCreateResponseBody());

      List<SalesforceDefaultPipelineDTO> results = service.createDefaultPipelines(ACCOUNT, ORG, PROJECT);

      assertThat(results).hasSize(7);
      assertThat(results.get(0).getStatus()).isEqualTo(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
      assertThat(results.get(1).getStatus()).isEqualTo(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
      assertThat(results.subList(2, 7))
          .extracting(SalesforceDefaultPipelineDTO::getStatus)
          .containsOnly(SalesforceDefaultPipelineDTO.StatusEnum.CREATED);
    }
  }
}

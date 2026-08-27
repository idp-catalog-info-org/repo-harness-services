/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.SalesforceDefaultPipelineDTO;

import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ProjectSalesforceDefaultPipelinesApiImplTest {
  @InjectMocks ProjectSalesforceDefaultPipelinesApiImpl apiImpl;
  @Mock SalesforceDefaultPipelineService salesforceDefaultPipelineService;
  @Mock AccessControlClient accessControlClient;

  private static final String ORG = "testOrg";
  private static final String PROJECT = "testProject";
  private static final String ACCOUNT = "testAccount";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_success_returns201() {
    List<SalesforceDefaultPipelineDTO> dtos = List.of(new SalesforceDefaultPipelineDTO()
                                                          .identifier("salesforce_dx_deploy")
                                                          .status(SalesforceDefaultPipelineDTO.StatusEnum.CREATED));
    when(salesforceDefaultPipelineService.createDefaultPipelines(ACCOUNT, ORG, PROJECT)).thenReturn(dtos);

    Response response = apiImpl.createProjectScopedSalesforceDefaultPipelines(ORG, PROJECT, ACCOUNT);

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isEqualTo(dtos);
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void createDefaultPipelines_accessDenied_throws() {
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());

    assertThatThrownBy(() -> apiImpl.createProjectScopedSalesforceDefaultPipelines(ORG, PROJECT, ACCOUNT))
        .isInstanceOf(NGAccessDeniedException.class);
    verify(salesforceDefaultPipelineService, never()).createDefaultPipelines(any(), any(), any());
  }
}

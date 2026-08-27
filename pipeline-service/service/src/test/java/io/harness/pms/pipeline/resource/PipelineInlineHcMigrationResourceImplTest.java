/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.RollbackResponse;
import io.harness.pms.pipeline.RollbackResponseDTO;
import io.harness.pms.pipeline.service.intfc.PMSPipelineInlineHcMigrationService;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineInlineHcMigrationResourceImplTest extends CategoryTest {
  @Mock private PMSPipelineInlineHcMigrationService pmsPipelineInlineHcMigrationService;
  @Mock private UserHelperService userHelperService;
  @InjectMocks private PipelineInlineHcMigrationResourceImpl pipelineInlineHcMigrationResource;

  private final String accountId = "account123";
  private final String userId = "testUser";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    // Mock UserHelperService
    UserPrincipal userPrincipal = new UserPrincipal(userId, "email", userId, accountId);
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userHelperService.isHarnessSupportUser(userId)).thenReturn(true);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInlineHCToInline_Success() {
    RollbackResponse serviceResponse = RollbackResponse.builder().migratedCount(10L).build();
    when(pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(accountId))
        .thenReturn(serviceResponse);

    ResponseDTO<RollbackResponseDTO> response = pipelineInlineHcMigrationResource.rollbackInlineHCToInline(accountId);

    verify(pmsPipelineInlineHcMigrationService).rollbackPipelinesFromInlineHCToInline(accountId);
    verify(userHelperService).getUserPrincipalOrThrow();
    verify(userHelperService).isHarnessSupportUser(userId);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getMigratedCount()).isEqualTo(10);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInlineHCToInline_NoPipelinesFound() {
    RollbackResponse serviceResponse = RollbackResponse.builder().migratedCount(0L).build();
    when(pmsPipelineInlineHcMigrationService.rollbackPipelinesFromInlineHCToInline(accountId))
        .thenReturn(serviceResponse);

    ResponseDTO<RollbackResponseDTO> response = pipelineInlineHcMigrationResource.rollbackInlineHCToInline(accountId);

    verify(pmsPipelineInlineHcMigrationService).rollbackPipelinesFromInlineHCToInline(accountId);
    verify(userHelperService).getUserPrincipalOrThrow();
    verify(userHelperService).isHarnessSupportUser(userId);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getMigratedCount()).isEqualTo(0);
  }
}

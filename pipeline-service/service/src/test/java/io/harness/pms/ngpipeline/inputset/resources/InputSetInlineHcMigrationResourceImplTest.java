/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponse;
import io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponseDTO;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetInlineHcMigrationService;
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
public class InputSetInlineHcMigrationResourceImplTest extends CategoryTest {
  @Mock private PMSInputSetInlineHcMigrationService pmsInputSetInlineHcMigrationService;
  @Mock private UserHelperService userHelperService;
  @InjectMocks private InputSetInlineHcMigrationResourceImpl inputSetInlineHcMigrationResource;

  private final String accountId = "account123";
  private final String userId = "testUser";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

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
    when(pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountId))
        .thenReturn(serviceResponse);

    ResponseDTO<RollbackResponseDTO> response = inputSetInlineHcMigrationResource.rollbackInlineHCToInline(accountId);

    verify(pmsInputSetInlineHcMigrationService).rollbackInputSetsFromInlineHCToInline(accountId);
    verify(userHelperService).getUserPrincipalOrThrow();
    verify(userHelperService).isHarnessSupportUser(userId);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getMigratedCount()).isEqualTo(10);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInlineHCToInline_NoInputSetsFound() {
    RollbackResponse serviceResponse = RollbackResponse.builder().migratedCount(0L).build();
    when(pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountId))
        .thenReturn(serviceResponse);

    ResponseDTO<RollbackResponseDTO> response = inputSetInlineHcMigrationResource.rollbackInlineHCToInline(accountId);

    verify(pmsInputSetInlineHcMigrationService).rollbackInputSetsFromInlineHCToInline(accountId);
    verify(userHelperService).getUserPrincipalOrThrow();
    verify(userHelperService).isHarnessSupportUser(userId);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getMigratedCount()).isEqualTo(0);
  }
}

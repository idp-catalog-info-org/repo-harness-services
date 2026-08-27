/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.ConsolidatedRollbackResponse;
import io.harness.pms.pipeline.ConsolidatedRollbackResponseDTO;
import io.harness.pms.pipeline.InlineHcMigrationEntityType;
import io.harness.pms.pipeline.service.intfc.InlineHcRollbackService;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InlineHcRollbackResourceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";
  private static final String USER_ID = "user123";

  @Mock private InlineHcRollbackService inlineHcRollbackService;
  @Mock private UserHelperService userHelperService;
  @Mock private UserPrincipal userPrincipal;

  private InlineHcRollbackResourceImpl inlineHcRollbackResource;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    inlineHcRollbackResource = new InlineHcRollbackResourceImpl(inlineHcRollbackService, userHelperService);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInlineHCToInline_Success() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(true);

    ConsolidatedRollbackResponse mockResponse = ConsolidatedRollbackResponse.builder()
                                                    .pipelineMigratedCount(10L)
                                                    .inputSetMigratedCount(5L)
                                                    .templateMigratedCount(3L)
                                                    .errors(List.of("error1"))
                                                    .build();

    when(inlineHcRollbackService.rollbackFromInlineHCToInline(ACCOUNT_ID, InlineHcMigrationEntityType.ALL))
        .thenReturn(mockResponse);

    ResponseDTO<ConsolidatedRollbackResponseDTO> response =
        inlineHcRollbackResource.rollbackInlineHCToInline(ACCOUNT_ID, ACCOUNT_ID, InlineHcMigrationEntityType.ALL);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getPipelineMigratedCount()).isEqualTo(10L);
    assertThat(response.getData().getInputSetMigratedCount()).isEqualTo(5L);
    assertThat(response.getData().getTemplateMigratedCount()).isEqualTo(3L);
    assertThat(response.getData().getTotalMigratedCount()).isEqualTo(18L);
    assertThat(response.getData().getErrors()).containsExactly("error1");

    verify(inlineHcRollbackService).rollbackFromInlineHCToInline(ACCOUNT_ID, InlineHcMigrationEntityType.ALL);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInlineHCToInline_UnauthorizedUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(false);

    assertThatThrownBy(()
                           -> inlineHcRollbackResource.rollbackInlineHCToInline(
                               ACCOUNT_ID, ACCOUNT_ID, InlineHcMigrationEntityType.ALL))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not Authorized");
  }
}

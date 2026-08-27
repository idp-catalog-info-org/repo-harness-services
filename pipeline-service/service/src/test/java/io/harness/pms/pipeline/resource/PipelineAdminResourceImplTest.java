/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.PlanConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.StepConcurrencyCounterResponseDTO;
import io.harness.pms.pipeline.service.PipelineAdminResourceService;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.UserHelperService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineAdminResourceImplTest extends CategoryTest {
  private static final String USER_ID = "userId";

  @Mock private PipelineAdminResourceService pipelineAdminResourceService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private UserHelperService userHelperService;
  @Mock private UserPrincipal userPrincipal;

  @InjectMocks private PipelineAdminResourceImpl pipelineAdminResource;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputeStepConcurrencyCountersDelegatesForHarnessSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(true);

    pipelineAdminResource.recomputeStepConcurrencyCounters();

    verify(pipelineAdminResourceService).recomputeStepConcurrencyCounters();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputeStepConcurrencyCountersThrowsForNonSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> pipelineAdminResource.recomputeStepConcurrencyCounters())
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(pipelineAdminResourceService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterDelegatesForHarnessSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    StepConcurrencyCounterResponseDTO responseDTO =
        StepConcurrencyCounterResponseDTO.builder().scope("cluster").value(5L).build();
    when(pipelineAdminResourceService.getStepConcurrencyCounter("cluster", null)).thenReturn(responseDTO);

    ResponseDTO<StepConcurrencyCounterResponseDTO> response =
        pipelineAdminResource.getStepConcurrencyCounter("cluster", null);

    assertThat(response.getData()).isEqualTo(responseDTO);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getStepConcurrencyCounterThrowsForNonSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> pipelineAdminResource.getStepConcurrencyCounter("cluster", null))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(pipelineAdminResourceService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputePlanConcurrencyCountersDelegatesForHarnessSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(true);

    pipelineAdminResource.recomputePlanConcurrencyCounters();

    verify(pipelineAdminResourceService).recomputePlanConcurrencyCounters();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void recomputePlanConcurrencyCountersThrowsForNonSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> pipelineAdminResource.recomputePlanConcurrencyCounters())
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(pipelineAdminResourceService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getPlanConcurrencyCountersDelegatesForHarnessSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    PlanConcurrencyCounterResponseDTO responseDTO =
        PlanConcurrencyCounterResponseDTO.builder().accountIdentifier("acc").accountCount(4L).build();
    when(pipelineAdminResourceService.getPlanConcurrencyCounters("acc")).thenReturn(responseDTO);

    ResponseDTO<PlanConcurrencyCounterResponseDTO> response = pipelineAdminResource.getPlanConcurrencyCounters("acc");

    assertThat(response.getData()).isEqualTo(responseDTO);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getPlanConcurrencyCountersThrowsForNonSupportUser() {
    when(userHelperService.getUserPrincipalOrThrow()).thenReturn(userPrincipal);
    when(userPrincipal.getName()).thenReturn(USER_ID);
    when(userHelperService.isHarnessSupportUser(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> pipelineAdminResource.getPlanConcurrencyCounters("acc"))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(pipelineAdminResourceService);
  }
}

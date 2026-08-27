/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.resources;

import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.service.IntegrationService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IntegrationsApiImplFeatureFlagTest extends CategoryTest {
  static final String ACCOUNT_ID = "testAccount";

  @InjectMocks IntegrationsApiImpl integrationsApi;
  @Mock IntegrationService integrationService;
  @Mock IdpCommonService idpCommonService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testDiscoverEntities_NewFlow_Succeeds() {
    doNothing().when(idpCommonService).newFlowCheck(ACCOUNT_ID);

    DiscoverEntitiesDTO dto = new DiscoverEntitiesDTO();
    dto.setDiscoverEntitiesResponses(new ArrayList<>());
    dto.setMergeSuggestions(new ArrayList<>());
    dto.setTotalElements(0L);
    dto.setOffsetPagination(false);

    when(integrationService.discoverEntities(eq(ACCOUNT_ID), isNull(), isNull(), any(), eq("integration-1"), eq(0),
             eq(10), isNull(), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(dto);
    when(idpCommonService.buildPageResponse(eq(0), eq(10), eq(0L), any()))
        .thenReturn(Response.status(Response.Status.OK).build());

    Response response =
        integrationsApi.discoverEntities("integration-1", ACCOUNT_ID, null, null, 0, 10, null, null, null, null, null);

    verify(idpCommonService).newFlowCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_NewFlow_OffsetPagination_SkipsBuildPageResponse() {
    doNothing().when(idpCommonService).newFlowCheck(ACCOUNT_ID);

    DiscoverEntitiesDTO dto = DiscoverEntitiesDTO.builder()
                                  .discoverEntitiesResponses(List.of())
                                  .mergeSuggestions(List.of())
                                  .prevOffset(2)
                                  .nextOffset(8)
                                  .offsetPagination(true)
                                  .build();

    when(integrationService.discoverEntities(eq(ACCOUNT_ID), isNull(), isNull(), any(), eq("integration-1"), eq(0),
             eq(10), isNull(), isNull(), isNull(), eq(2), isNull()))
        .thenReturn(dto);

    Response response =
        integrationsApi.discoverEntities("integration-1", ACCOUNT_ID, null, null, 0, 10, null, null, null, 2, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) response.getEntity();
    assertThat(body).containsEntry("prev_offset", 2).containsEntry("next_offset", 8);
    verify(idpCommonService, never()).buildPageResponse(anyInt(), anyInt(), anyLong(), any());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testDiscoverEntities_LegacyFlow_Throws() {
    doThrow(new InvalidRequestException("Account is in legacy CD flow"))
        .when(idpCommonService)
        .newFlowCheck(ACCOUNT_ID);

    assertThatThrownBy(()
                           -> integrationsApi.discoverEntities(
                               "integration-1", ACCOUNT_ID, null, null, 0, 10, null, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntities_NewFlow_Succeeds() {
    doNothing().when(idpCommonService).newFlowCheck(ACCOUNT_ID);

    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setIntegrationEntities(new ArrayList<>());

    Response response = integrationsApi.saveDiscoverEntities(request, "integration-1", ACCOUNT_ID, null, null);

    verify(idpCommonService).newFlowCheck(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testSaveDiscoverEntities_LegacyFlow_Throws() {
    doThrow(new InvalidRequestException("Account is in legacy CD flow"))
        .when(idpCommonService)
        .newFlowCheck(ACCOUNT_ID);

    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest();
    request.setIntegrationEntities(new ArrayList<>());

    assertThatThrownBy(() -> integrationsApi.saveDiscoverEntities(request, "integration-1", ACCOUNT_ID, null, null))
        .isInstanceOf(InvalidRequestException.class);
  }
}

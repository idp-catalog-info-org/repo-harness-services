/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.resources;

import static io.harness.idp.common.Constants.IDP_PREFIX;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_BRANCH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_PATH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_REPO;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.utils.ApiUtils.X_PAGE_NUMBER;
import static io.harness.utils.ApiUtils.X_PAGE_SIZE;
import static io.harness.utils.ApiUtils.X_TOTAL_ELEMENTS;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.service.IntegrationService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationRequest;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationResponse;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponseActionDestinationMerge;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.ReadValidationDetails;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IntegrationsApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";

  AutoCloseable openMocks;

  @InjectMocks IntegrationsApiImpl integrationsApi;

  @Mock IntegrationService integrationService;
  @Mock IdpCommonService idpCommonService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateIntegration() {
    AbstractIntegrationRequest abstractIntegrationRequest = new AbstractIntegrationRequest().request(
        new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .readValidationDetails(new ReadValidationDetails().fileUrl(TEST_READ_VALIDATION_FILE_URL))
            .writeValidationDetails(new WriteValidationDetails()
                                        .repository(TEST_WRITE_VALIDATION_REPO)
                                        .branch(TEST_WRITE_VALIDATION_BRANCH)
                                        .path(TEST_WRITE_VALIDATION_PATH))
            .type(BaseIntegrationRequest.TypeEnum.GIT));

    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(integrationService.save(
             eq(TEST_ACCOUNT_IDENTIFIER), eq("git"), eq(abstractIntegrationRequest), anyBoolean(), anyBoolean()))
        .thenReturn(baseIntegrationResponse);

    Response response =
        integrationsApi.createIntegration(abstractIntegrationRequest, "git", TEST_ACCOUNT_IDENTIFIER, false, false);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    AbstractIntegrationResponse abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    GitIntegrationResponse gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());

    response =
        integrationsApi.createIntegration(abstractIntegrationRequest, "git", TEST_ACCOUNT_IDENTIFIER, true, false);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());

    response =
        integrationsApi.createIntegration(abstractIntegrationRequest, "git", TEST_ACCOUNT_IDENTIFIER, true, true);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegration() {
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(integrationService.get(TEST_ACCOUNT_IDENTIFIER, "git", IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER))
        .thenReturn(baseIntegrationResponse);

    Response response =
        integrationsApi.getIntegration("git", IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    AbstractIntegrationResponse abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    GitIntegrationResponse gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrations() {
    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    List<BaseIntegrationResponse> baseIntegrationResponseList = new ArrayList<>();
    baseIntegrationResponseList.add(baseIntegrationResponse);

    List<AbstractIntegrationResponse> abstractIntegrationResponses = new ArrayList<>();
    baseIntegrationResponseList.forEach(baseIntegrationRes -> {
      AbstractIntegrationResponse abstractIntegrationResponse = new AbstractIntegrationResponse();
      abstractIntegrationResponse.setResponse(baseIntegrationRes);
      abstractIntegrationResponses.add(abstractIntegrationResponse);
    });

    Response response = Response.ok()
                            .header(X_TOTAL_ELEMENTS, 1)
                            .header(X_PAGE_NUMBER, 0)
                            .header(X_PAGE_SIZE, 100)
                            .entity(abstractIntegrationResponses)
                            .build();

    when(integrationService.get(eq(TEST_ACCOUNT_IDENTIFIER), eq("git"), any(), any()))
        .thenReturn(baseIntegrationResponseList);
    when(idpCommonService.buildPageResponse(anyInt(), anyInt(), anyLong(), any())).thenReturn(response);

    Response apiResponse = integrationsApi.getIntegrations("git", TEST_ACCOUNT_IDENTIFIER, null, null, null, null);
    assertThat(apiResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_TOTAL_ELEMENTS))).isEqualTo(1);
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_NUMBER))).isZero();
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_SIZE))).isEqualTo(100);
    List<AbstractIntegrationResponse> abstractIntegrationResponseList =
        (List<AbstractIntegrationResponse>) response.getEntity();
    assertEquals(abstractIntegrationResponses, abstractIntegrationResponseList);

    response = Response.ok()
                   .header(X_TOTAL_ELEMENTS, 1)
                   .header(X_PAGE_NUMBER, 0)
                   .header(X_PAGE_SIZE, 10)
                   .entity(abstractIntegrationResponses)
                   .build();
    when(idpCommonService.buildPageResponse(anyInt(), anyInt(), anyLong(), any())).thenReturn(response);

    apiResponse = integrationsApi.getIntegrations("git", TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null);
    assertThat(apiResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_TOTAL_ELEMENTS))).isEqualTo(1);
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_NUMBER))).isZero();
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_SIZE))).isEqualTo(10);
    abstractIntegrationResponseList = (List<AbstractIntegrationResponse>) response.getEntity();
    assertEquals(abstractIntegrationResponses, abstractIntegrationResponseList);

    apiResponse = integrationsApi.getIntegrations("git", TEST_ACCOUNT_IDENTIFIER, 0, 10, null, "search");
    assertThat(apiResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_TOTAL_ELEMENTS))).isEqualTo(1);
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_NUMBER))).isZero();
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_SIZE))).isEqualTo(10);
    abstractIntegrationResponseList = (List<AbstractIntegrationResponse>) response.getEntity();
    assertEquals(abstractIntegrationResponses, abstractIntegrationResponseList);

    apiResponse = integrationsApi.getIntegrations("git", TEST_ACCOUNT_IDENTIFIER, 0, 10, "status,DESC", null);
    assertThat(apiResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_TOTAL_ELEMENTS))).isEqualTo(1);
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_NUMBER))).isZero();
    assertThat(Long.valueOf(apiResponse.getHeaderString(X_PAGE_SIZE))).isEqualTo(10);
    abstractIntegrationResponseList = (List<AbstractIntegrationResponse>) response.getEntity();
    assertEquals(abstractIntegrationResponses, abstractIntegrationResponseList);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationsInvalidSort() {
    integrationsApi.getIntegrations("GIT", TEST_ACCOUNT_IDENTIFIER, null, null, "invalid,ASC", null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateIntegration() {
    AbstractIntegrationRequest abstractIntegrationRequest = new AbstractIntegrationRequest().request(
        new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .readValidationDetails(new ReadValidationDetails().fileUrl(TEST_READ_VALIDATION_FILE_URL))
            .writeValidationDetails(new WriteValidationDetails()
                                        .repository(TEST_WRITE_VALIDATION_REPO)
                                        .branch(TEST_WRITE_VALIDATION_BRANCH)
                                        .path(TEST_WRITE_VALIDATION_PATH))
            .type(BaseIntegrationRequest.TypeEnum.GIT));

    BaseIntegrationResponse baseIntegrationResponse = baseIntegrationResponse();

    when(integrationService.update(eq(TEST_ACCOUNT_IDENTIFIER), eq("git"), eq(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER),
             eq(abstractIntegrationRequest), anyBoolean()))
        .thenReturn(baseIntegrationResponse);

    Response response = integrationsApi.updateIntegration(
        abstractIntegrationRequest, "git", IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, false);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    AbstractIntegrationResponse abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    GitIntegrationResponse gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());

    response = integrationsApi.updateIntegration(
        abstractIntegrationRequest, "git", IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, true);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    abstractIntegrationResponse = (AbstractIntegrationResponse) response.getEntity();
    gitIntegrationResponse = (GitIntegrationResponse) abstractIntegrationResponse.getResponse();
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteIntegrations() {
    doNothing().when(integrationService).delete(TEST_ACCOUNT_IDENTIFIER, "git");

    Response response = integrationsApi.deleteIntegrations("git", TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_OffsetPagination_ReturnsBodyWithoutBuildPageResponse() {
    doNothing().when(idpCommonService).newFlowCheck(TEST_ACCOUNT_IDENTIFIER);

    DiscoverEntitiesResponse entity = new DiscoverEntitiesResponse();
    entity.setIntegrationEntityId("entity-1");
    DiscoverEntitiesResponseActionDestinationMerge suggestion = new DiscoverEntitiesResponseActionDestinationMerge();
    suggestion.setEntityRef("component:account/svc");
    DiscoverEntitiesDTO dto = DiscoverEntitiesDTO.builder()
                                  .discoverEntitiesResponses(List.of(entity))
                                  .mergeSuggestions(List.of(suggestion))
                                  .prevOffset(null)
                                  .nextOffset(5)
                                  .offsetPagination(true)
                                  .build();

    when(integrationService.discoverEntities(eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(),
             eq(BaseIntegrationRequest.TypeEnum.CATALOG.value()), eq("integration-1"), eq(0), eq(10), isNull(),
             isNull(), isNull(), isNull(), isNull()))
        .thenReturn(dto);

    Response response = integrationsApi.discoverEntities(
        "integration-1", TEST_ACCOUNT_IDENTIFIER, null, null, 0, 10, null, null, null, null, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) response.getEntity();
    assertThat(body).containsKeys("entities", "merge_suggestions", "prev_offset", "next_offset");
    assertThat(body.get("entities")).isEqualTo(List.of(entity));
    assertThat(body.get("merge_suggestions")).isEqualTo(List.of(suggestion));
    assertThat(body.get("prev_offset")).isNull();
    assertThat(body.get("next_offset")).isEqualTo(5);
    verify(idpCommonService, never()).buildPageResponse(anyInt(), anyInt(), anyLong(), any());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testDiscoverEntities_LegacyPagination_UsesBuildPageResponse() {
    doNothing().when(idpCommonService).newFlowCheck(TEST_ACCOUNT_IDENTIFIER);

    DiscoverEntitiesDTO dto = DiscoverEntitiesDTO.builder()
                                  .discoverEntitiesResponses(List.of())
                                  .mergeSuggestions(List.of())
                                  .totalElements(12)
                                  .offsetPagination(false)
                                  .build();
    Response pageResponse = Response.ok().entity(Map.of("entities", List.of())).build();

    when(integrationService.discoverEntities(eq(TEST_ACCOUNT_IDENTIFIER), isNull(), isNull(),
             eq(BaseIntegrationRequest.TypeEnum.CATALOG.value()), eq("integration-1"), eq(1), eq(10), isNull(),
             isNull(), isNull(), eq(3), isNull()))
        .thenReturn(dto);
    when(idpCommonService.buildPageResponse(eq(1), eq(10), eq(12L), any())).thenReturn(pageResponse);

    Response response = integrationsApi.discoverEntities(
        "integration-1", TEST_ACCOUNT_IDENTIFIER, null, null, 1, 10, null, null, null, 3, null);

    assertThat(response).isSameAs(pageResponse);
    verify(idpCommonService).buildPageResponse(eq(1), eq(10), eq(12L), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private BaseIntegrationResponse baseIntegrationResponse() {
    return new GitIntegrationResponse()
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .name(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .type(BaseIntegrationResponse.TypeEnum.GIT);
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.resources;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Tier;
import io.harness.spec.server.idp.v1.model.TierGroupDetails;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;
import io.harness.spec.server.idp.v1.model.TierGroupResponse;

import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupsApiImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String TIER_GROUP_ID = "default_tiers";
  private static final String CUSTOM_TIER_GROUP_ID = "compliance_tiers";

  @Mock private TierGroupService tierGroupService;
  @Mock private IdpCommonService idpCommonService;

  private TierGroupsApiImpl tierGroupsApi;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(true);
    tierGroupsApi = new TierGroupsApiImpl(tierGroupService, idpCommonService);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupsReturnsGroupsSortedByName() {
    when(tierGroupService.getAllTierGroups(ACCOUNT_ID))
        .thenReturn(List.of(TierGroupEntity.builder().identifier(TIER_GROUP_ID).name("Zulu").build(),
            TierGroupEntity.builder().identifier("alpha_tiers").name("alpha").build(),
            TierGroupEntity.builder().identifier(CUSTOM_TIER_GROUP_ID).name("Beta").build()));

    Response response = tierGroupsApi.getTierGroups(ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<?> responseEntities = (List<?>) response.getEntity();
    assertThat(responseEntities)
        .extracting(entity -> ((TierGroupResponse) entity).getTierGroup().getName())
        .containsExactly("alpha", "Beta", "Zulu");
    verify(tierGroupService).getAllTierGroups(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupsReturnsInternalServerErrorOnFailure() {
    when(tierGroupService.getAllTierGroups(ACCOUNT_ID)).thenThrow(new RuntimeException("provisioning failed"));

    Response response = tierGroupsApi.getTierGroups(ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).contains("provisioning failed");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupAllowsWhitespacePaddedBodyIdentifier() {
    TierGroupDetailsRequest request = buildUpdateRequest(CUSTOM_TIER_GROUP_ID + "  ");
    TierGroupDetailsResponse detailsResponse =
        new TierGroupDetailsResponse().tierGroup(new TierGroupDetails().identifier(CUSTOM_TIER_GROUP_ID));
    when(tierGroupService.updateTierGroup(request, ACCOUNT_ID)).thenReturn(detailsResponse);

    Response response = tierGroupsApi.updateTierGroup(CUSTOM_TIER_GROUP_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    verify(tierGroupService).updateTierGroup(request, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupReturnsOk() {
    TierGroupDetailsResponse detailsResponse =
        new TierGroupDetailsResponse().tierGroup(new TierGroupDetails().identifier(TIER_GROUP_ID).name("Default"));
    when(tierGroupService.getTierGroupDetails(ACCOUNT_ID, TIER_GROUP_ID)).thenReturn(detailsResponse);

    Response response = tierGroupsApi.getTierGroup(TIER_GROUP_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(((TierGroupDetailsResponse) response.getEntity()).getTierGroup().getIdentifier())
        .isEqualTo(TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupReturnsInternalServerErrorWhenNotFound() {
    when(tierGroupService.getTierGroupDetails(ACCOUNT_ID, "missing_tiers"))
        .thenThrow(new InvalidRequestException("Tier group not found for identifier [missing_tiers]"));

    Response response = tierGroupsApi.getTierGroup("missing_tiers", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).contains("Tier group not found");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createTierGroupReturnsCreated() {
    TierGroupDetailsRequest request = buildRequest(CUSTOM_TIER_GROUP_ID);
    TierGroupDetailsResponse detailsResponse =
        new TierGroupDetailsResponse().tierGroup(new TierGroupDetails().identifier(CUSTOM_TIER_GROUP_ID));
    when(tierGroupService.saveTierGroup(request, ACCOUNT_ID)).thenReturn(detailsResponse);

    Response response = tierGroupsApi.createTierGroup(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(((TierGroupDetailsResponse) response.getEntity()).getTierGroup().getIdentifier())
        .isEqualTo(CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createTierGroupReturnsBadRequestForReservedIdentifier() {
    doThrow(new InvalidRequestException("reserved for system use"))
        .when(tierGroupService)
        .saveTierGroup(null, ACCOUNT_ID);

    Response response = tierGroupsApi.createTierGroup(null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void createTierGroupReturnsInternalServerErrorOnDuplicateKey() {
    TierGroupDetailsRequest request = buildRequest(CUSTOM_TIER_GROUP_ID);
    when(tierGroupService.saveTierGroup(request, ACCOUNT_ID)).thenThrow(new DuplicateKeyException("duplicate"));

    Response response = tierGroupsApi.createTierGroup(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).contains("already exists");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupReturnsOk() {
    TierGroupDetailsRequest request = buildRequest(CUSTOM_TIER_GROUP_ID);
    TierGroupDetailsResponse detailsResponse = new TierGroupDetailsResponse().tierGroup(
        new TierGroupDetails().identifier(CUSTOM_TIER_GROUP_ID).name("Updated"));
    when(tierGroupService.updateTierGroup(request, ACCOUNT_ID)).thenReturn(detailsResponse);

    Response response = tierGroupsApi.updateTierGroup(CUSTOM_TIER_GROUP_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(((TierGroupDetailsResponse) response.getEntity()).getTierGroup().getName()).isEqualTo("Updated");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupReturnsBadRequestOnIdentifierMismatch() {
    TierGroupDetailsRequest request = buildRequest("group_b");

    Response response = tierGroupsApi.updateTierGroup("group_a", request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage())
        .contains("Path tier-group-id must match tier_group.identifier");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void updateTierGroupReturnsInternalServerErrorWhenNotFound() {
    TierGroupDetailsRequest request = buildRequest(CUSTOM_TIER_GROUP_ID);
    when(tierGroupService.updateTierGroup(request, ACCOUNT_ID))
        .thenThrow(new InvalidRequestException("Tier group not found for identifier [compliance_tiers]"));

    Response response = tierGroupsApi.updateTierGroup(CUSTOM_TIER_GROUP_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).contains("Tier group not found");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteTierGroupReturnsNoContent() {
    Response response = tierGroupsApi.deleteTierGroup(CUSTOM_TIER_GROUP_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    verify(tierGroupService).deleteTierGroup(ACCOUNT_ID, CUSTOM_TIER_GROUP_ID);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteTierGroupReturnsBadRequestForDefaultTierGroup() {
    doThrow(new InvalidRequestException("cannot be deleted"))
        .when(tierGroupService)
        .deleteTierGroup(ACCOUNT_ID, TIER_GROUP_ID);

    Response response = tierGroupsApi.deleteTierGroup(TIER_GROUP_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void getTierGroupsReturnsForbiddenWhenFeatureDisabled() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);

    Response response = tierGroupsApi.getTierGroups(ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage())
        .isEqualTo("Scorecard tiers is not enabled for this account");
  }

  private TierGroupDetailsRequest buildRequest(String identifier) {
    TierGroupDetails details = new TierGroupDetails()
                                   .identifier(identifier)
                                   .name("Compliance Tiers")
                                   .tiers(List.of(buildTier("Bronze", 0, 49), buildTier("Silver", 50, 100)));
    return new TierGroupDetailsRequest().tierGroup(details);
  }

  private TierGroupDetailsRequest buildUpdateRequest(String identifier) {
    return buildRequest(identifier);
  }

  private Tier buildTier(String name, int minScore, int maxScore) {
    return new Tier()
        .name(name)
        .description(name + " description")
        .icon("https://example.com/" + name.toLowerCase() + ".png")
        .colour("#000000")
        .minScore(minScore)
        .maxScore(maxScore);
  }
}

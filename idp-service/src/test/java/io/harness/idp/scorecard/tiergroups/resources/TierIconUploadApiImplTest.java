/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.resources;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CardIconResponse;

import java.io.ByteArrayInputStream;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class TierIconUploadApiImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String ICON_URL =
      "https://storage.googleapis.com/idp-tiers-qa/static/qa/account1/tiers/gold.png";

  @Mock private TierGroupService tierGroupService;
  @Mock private IdpCommonService idpCommonService;

  private TierIconUploadApiImpl tierIconUploadApi;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(true);
    tierIconUploadApi = new TierIconUploadApiImpl(tierGroupService, idpCommonService);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconReturnsForbiddenWhenFeatureDisabled() {
    when(idpCommonService.idpScorecardTiersEnabled(ACCOUNT_ID)).thenReturn(false);
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();

    Response response =
        tierIconUploadApi.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconReturnsPublicUrl() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();
    when(tierGroupService.uploadTierIcon(eq("ICON"), any(), eq(fileDetail), eq(ACCOUNT_ID))).thenReturn(ICON_URL);

    Response response =
        tierIconUploadApi.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(((CardIconResponse) response.getEntity()).getIconUrl()).isEqualTo(ICON_URL);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconReturnsBadRequestForInvalidInput() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.exe").size(1024).build();
    when(tierGroupService.uploadTierIcon(eq("ICON"), any(), eq(fileDetail), eq(ACCOUNT_ID)))
        .thenThrow(new InvalidRequestException("Tier icon format 'exe' is not supported"));

    Response response =
        tierIconUploadApi.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).contains("format 'exe' is not supported");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void uploadTierIconReturnsInternalServerErrorForUploadFailure() {
    FormDataContentDisposition fileDetail =
        FormDataContentDisposition.name("file").fileName("gold.png").size(1024).build();
    when(tierGroupService.uploadTierIcon(eq("ICON"), any(), eq(fileDetail), eq(ACCOUNT_ID)))
        .thenThrow(new RuntimeException("storage credentials expired"));

    Response response =
        tierIconUploadApi.uploadTierIcon("ICON", new ByteArrayInputStream("data".getBytes()), fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(((ResponseMessage) response.getEntity()).getMessage()).isEqualTo("Could not upload tier icon");
  }
}

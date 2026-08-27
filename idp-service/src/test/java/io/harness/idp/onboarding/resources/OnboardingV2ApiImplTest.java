/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.resources;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.onboarding.service.OnboardingServiceV2;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CDEntityAsIdpEntity;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchResponse;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefRequest;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefResponse;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesResponse;
import io.harness.spec.server.idp.v1.model.OnboardingSkipRequest;
import io.harness.spec.server.idp.v1.model.OnboardingSkipResponse;
import io.harness.spec.server.idp.v1.model.OnboardingStatusResponse;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.data.domain.PageRequest;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingV2ApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "accountIdentifier";
  AutoCloseable openMocks;

  @InjectMocks private OnboardingV2ApiImpl onboardingV2Api;

  @Mock private OnboardingServiceV2 onboardingServiceV2;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesCount() {
    OnboardingCdEntitiesCountResponse onboardingCdEntitiesCountResponse = new OnboardingCdEntitiesCountResponse();
    onboardingCdEntitiesCountResponse.setCdEntitiesCount(3);

    when(onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER)).thenReturn(onboardingCdEntitiesCountResponse);

    Response response = onboardingV2Api.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingCdEntitiesCountResponse onboardingCdEntitiesCountResponseFromApi =
        (OnboardingCdEntitiesCountResponse) response.getEntity();
    assertThat(onboardingCdEntitiesCountResponseFromApi.getCdEntitiesCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesFetch() {
    OnboardingCdEntitiesFetchRequest onboardingCdEntitiesFetchRequest = new OnboardingCdEntitiesFetchRequest();

    OnboardingCdEntitiesFetchResponse onboardingCdEntitiesFetchResponse = new OnboardingCdEntitiesFetchResponse();
    onboardingCdEntitiesFetchResponse.setOrganizationsCount(1);
    onboardingCdEntitiesFetchResponse.setProjectsCount(1);
    onboardingCdEntitiesFetchResponse.setServicesCount(1);
    List<CDEntityAsIdpEntity> entities = new ArrayList<>();
    CDEntityAsIdpEntity cdEntityAsIdpEntity = new CDEntityAsIdpEntity();
    cdEntityAsIdpEntity.setType("type");
    cdEntityAsIdpEntity.setHarnessType("harnessType");
    cdEntityAsIdpEntity.setDomain("domain");
    cdEntityAsIdpEntity.setSystem("system");
    cdEntityAsIdpEntity.setName("name");
    cdEntityAsIdpEntity.setHarnessAbsoluteIdentifier("name|name|name");
    cdEntityAsIdpEntity.setOwner("owner");
    entities.add(cdEntityAsIdpEntity);
    onboardingCdEntitiesFetchResponse.setEntities(entities);

    when(onboardingServiceV2.cdEntitiesFetch(eq(TEST_ACCOUNT_IDENTIFIER), any(), any(PageRequest.class), any()))
        .thenReturn(onboardingCdEntitiesFetchResponse);

    Response response =
        onboardingV2Api.cdEntitiesFetch(onboardingCdEntitiesFetchRequest, TEST_ACCOUNT_IDENTIFIER, null, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingCdEntitiesFetchResponse onboardingCdEntitiesFetchResponseFromApi =
        (OnboardingCdEntitiesFetchResponse) response.getEntity();
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getOrganizationsCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getProjectsCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getServicesCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getEntities()).hasSize(1);

    response = onboardingV2Api.cdEntitiesFetch(onboardingCdEntitiesFetchRequest, TEST_ACCOUNT_IDENTIFIER, 0, 10, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    onboardingCdEntitiesFetchResponseFromApi = (OnboardingCdEntitiesFetchResponse) response.getEntity();
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getOrganizationsCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getProjectsCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getServicesCount()).isEqualTo(1);
    assertThat(onboardingCdEntitiesFetchResponseFromApi.getEntities()).hasSize(1);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYamlDef() {
    OnboardingGenerateYamlDefRequest onboardingGenerateYamlDefRequest = new OnboardingGenerateYamlDefRequest();

    OnboardingGenerateYamlDefResponse onboardingGenerateYamlDefResponse = new OnboardingGenerateYamlDefResponse();
    onboardingGenerateYamlDefResponse.setYamlDef("yamlDef");
    onboardingGenerateYamlDefResponse.setYamlDefDesc("yamlDefDesc");

    when(onboardingServiceV2.generateYamlDef(eq(TEST_ACCOUNT_IDENTIFIER), any()))
        .thenReturn(onboardingGenerateYamlDefResponse);

    Response response = onboardingV2Api.generateYamlDef(onboardingGenerateYamlDefRequest, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingGenerateYamlDefResponse onboardingGenerateYamlDefResponseFromApi =
        (OnboardingGenerateYamlDefResponse) response.getEntity();
    assertThat(onboardingGenerateYamlDefResponseFromApi.getYamlDef()).isEqualTo("yamlDef");
    assertThat(onboardingGenerateYamlDefResponseFromApi.getYamlDefDesc()).isEqualTo("yamlDefDesc");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOnboardingStatus() {
    OnboardingStatusResponse onboardingStatusResponse = new OnboardingStatusResponse();
    onboardingStatusResponse.setStatus(OnboardingStatusResponse.StatusEnum.GET_STARTED);

    when(onboardingServiceV2.getOnboardingStatus(TEST_ACCOUNT_IDENTIFIER)).thenReturn(onboardingStatusResponse);

    Response response = onboardingV2Api.getOnboardingStatus(TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingStatusResponse onboardingStatusResponseFromApi = (OnboardingStatusResponse) response.getEntity();
    assertThat(onboardingStatusResponseFromApi.getStatus()).isEqualTo(OnboardingStatusResponse.StatusEnum.GET_STARTED);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportCdEntities() {
    OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest = new OnboardingImportCdEntitiesRequest();

    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponse = new OnboardingImportCdEntitiesResponse();
    onboardingImportCdEntitiesResponse.setStatus("SUCCESS");

    when(onboardingServiceV2.importCdEntities(eq(TEST_ACCOUNT_IDENTIFIER), any()))
        .thenReturn(onboardingImportCdEntitiesResponse);

    Response response = onboardingV2Api.importCdEntities(onboardingImportCdEntitiesRequest, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponseFromApi =
        (OnboardingImportCdEntitiesResponse) response.getEntity();
    assertThat(onboardingImportCdEntitiesResponseFromApi.getStatus()).isEqualTo("SUCCESS");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPostOnboardingSkip() {
    OnboardingSkipRequest onboardingSkipRequest = new OnboardingSkipRequest();
    onboardingSkipRequest.setSkippedAt(OnboardingSkipRequest.SkippedAtEnum.GET_STARTED);

    OnboardingSkipResponse onboardingSkipResponse = new OnboardingSkipResponse();
    onboardingSkipResponse.setStatus("SUCCESS");

    when(onboardingServiceV2.postOnboardingSkip(eq(TEST_ACCOUNT_IDENTIFIER), any())).thenReturn(onboardingSkipResponse);

    Response response = onboardingV2Api.postOnboardingSkip(onboardingSkipRequest, TEST_ACCOUNT_IDENTIFIER);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    OnboardingSkipResponse onboardingSkipResponseFromApi = (OnboardingSkipResponse) response.getEntity();
    assertThat(onboardingSkipResponseFromApi.getStatus()).isEqualTo("SUCCESS");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}

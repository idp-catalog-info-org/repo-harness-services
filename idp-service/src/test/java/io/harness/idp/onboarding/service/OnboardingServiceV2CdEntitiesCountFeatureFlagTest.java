/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service;

import static io.harness.rule.OwnerRule.DHRUVX;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.config.OnboardingModuleV2Config;
import io.harness.idp.onboarding.entities.OnboardingFlowEntity;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.OnboardingFlowEntityRepository;
import io.harness.idp.onboarding.service.impl.OnboardingServiceV2Impl;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingServiceV2CdEntitiesCountFeatureFlagTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "123";

  @InjectMocks private OnboardingServiceV2Impl onboardingServiceV2;
  @Mock IdpCommonService idpCommonService;
  @Mock OnboardingFlowEntityRepository onboardingFlowEntityRepository;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) ServiceResourceClient serviceResourceClient;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    OnboardingModuleConfig onboardingModuleConfig =
        OnboardingModuleConfig.builder()
            .descriptionForSampleEntity("sample")
            .descriptionForEntitySelected("selected")
            .tmpPathForCatalogInfoYamlStore("/tmp")
            .harnessCiCdAnnotations(Map.of("projectUrl", "url", "serviceUrl", "url"))
            .build();

    HarnessServiceToBackstageComponent harnessServiceToBackstageComponent =
        new HarnessServiceToBackstageComponent(onboardingModuleConfig, "local");
    FieldUtils.writeField(
        onboardingServiceV2, "harnessServiceToBackstageComponent", harnessServiceToBackstageComponent, true);

    OnboardingModuleV2Config onboardingModuleV2Config = OnboardingModuleV2Config.builder()
                                                            .descriptionForSampleCatalogInfoDef("sample")
                                                            .descriptionForActualCatalogInfoDef("actual")
                                                            .build();
    FieldUtils.writeField(onboardingServiceV2, "onboardingModuleV2Config", onboardingModuleV2Config, true);

    mockServicesTotalCount(5);
  }

  @SuppressWarnings("unchecked")
  private void mockServicesTotalCount(int totalCount) throws Exception {
    PageResponse pageResponse = PageResponse.builder().totalItems(totalCount).content(new ArrayList<>()).build();
    ResponseDTO responseDTO = ResponseDTO.newResponse(pageResponse);
    Call call = org.mockito.Mockito.mock(Call.class);
    when(serviceResourceClient.getAllServicesList(anyString(), any(), any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(responseDTO));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCdEntitiesCount_LegacyFlow_NoFiltering() {
    when(idpCommonService.isLegacyCDFlow(TEST_ACCOUNT_IDENTIFIER)).thenReturn(true);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setImportedCDEntities(Set.of("org-proj-svc"));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesCountResponse response = onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);

    assertEquals(5, response.getCdEntitiesCount().intValue());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCdEntitiesCount_NewFlow_FiltersImported() {
    when(idpCommonService.isLegacyCDFlow(TEST_ACCOUNT_IDENTIFIER)).thenReturn(false);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setImportedCDEntities(Set.of("org-proj-svc"));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesCountResponse response = onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);

    assertEquals(4, response.getCdEntitiesCount().intValue());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCdEntitiesCount_IntOff_CdAdOff_FiltersImported() {
    when(idpCommonService.isLegacyCDFlow(TEST_ACCOUNT_IDENTIFIER)).thenReturn(false);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesCountResponse response = onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);

    assertEquals(5, response.getCdEntitiesCount().intValue());
  }
}

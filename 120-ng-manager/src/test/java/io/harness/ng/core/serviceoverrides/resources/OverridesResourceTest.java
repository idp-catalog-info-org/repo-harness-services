/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.resources;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverridesV2YamlSchemaHelper;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.serviceoverride.beans.NGOverrideGovernanceDataResponse;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverrideRequestDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;

public class OverridesResourceTest extends CategoryTest {
  @InjectMocks OverridesResource overridesResource;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock FilterService filterService;
  @Mock ServiceOverrideValidatorService serviceOverrideValidatorService;
  @Mock ServiceOverridesV2YamlSchemaHelper serviceOverridesV2YamlSchemaHelper;
  @Mock ScopeInfoService scopeInfoService;
  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder().uniqueId("unique-id").build());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListApiForOverridesWithFilterIdentifier() {
    OverrideFilterPropertiesDTO overrideFilterPropertiesDTO = OverrideFilterPropertiesDTO.builder()
                                                                  .serviceRefs(List.of("Svc1"))
                                                                  .infraIdentifiers(List.of("Infra1"))
                                                                  .environmentRefs(List.of("Env1"))
                                                                  .build();

    when(filterService.get(any(), any(), any(), any(), any()))
        .thenReturn(FilterDTO.builder().filterProperties(overrideFilterPropertiesDTO).build());
    Page<NGServiceOverridesEntity> page = PageUtils.getPage(new ArrayList<>(), 0, 10);
    when(serviceOverridesServiceV2.list(any(), any())).thenReturn(page);

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    overridesResource.listOverrides(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        ServiceOverridesType.ENV_SERVICE_OVERRIDE, null, null, "filter", null);
    verify(serviceOverridesServiceV2, times(1)).list(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListApiForOverridesWithFilterProperties() {
    OverrideFilterPropertiesDTO overrideFilterPropertiesDTO = OverrideFilterPropertiesDTO.builder()
                                                                  .serviceRefs(List.of("Svc1"))
                                                                  .infraIdentifiers(List.of("Infra1"))
                                                                  .environmentRefs(List.of("Env1"))
                                                                  .build();

    OverrideFilterPropertiesDTO savedFilterProperties = OverrideFilterPropertiesDTO.builder()
                                                            .serviceRefs(List.of("Svc2"))
                                                            .infraIdentifiers(List.of("Infra2"))
                                                            .environmentRefs(List.of("Env2"))
                                                            .build();

    when(filterService.get(any(), any(), any(), any(), any()))
        .thenReturn(FilterDTO.builder().filterProperties(savedFilterProperties).build());
    Page<NGServiceOverridesEntity> page = PageUtils.getPage(new ArrayList<>(), 0, 10);
    when(serviceOverridesServiceV2.list(any(), any())).thenReturn(page);

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    overridesResource.listOverrides(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        ServiceOverridesType.ENV_SERVICE_OVERRIDE, overrideFilterPropertiesDTO, null, "filter", null);
    verify(serviceOverridesServiceV2, times(1)).list(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForServiceOverride() {
    GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo = GitMetadataUpdateRequestInfoDTO.builder()
                                                                       .connectorRef("newConnectorRef")
                                                                       .filePath("newFilePath")
                                                                       .repoName("repoName")
                                                                       .build();

    ServiceOverrideGitUpdateRequestDTO serviceOverrideGitUpdateRequestDTO =
        ServiceOverrideGitUpdateRequestDTO.builder()
            .environmentRef("environmentRef")
            .serviceRef("serviceRef")
            .serviceOverridesType(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
            .gitMetadataUpdateRequestInfo(gitMetadataUpdateRequestInfo)
            .build();

    ServiceOverrideGitUpdateResponseDTO serviceOverrideGitUpdateResponseDTO =
        ServiceOverrideGitUpdateResponseDTO.builder()
            .environmentRef("environmentRef")
            .serviceRef("serviceRef")
            .identifier("environmentRef_serviceRef")
            .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
            .build();

    doReturn(serviceOverrideGitUpdateResponseDTO)
        .when(serviceOverridesServiceV2)
        .updateGitMetadata(any(), any(), any(), any(), any());

    ResponseDTO<ServiceOverrideGitUpdateResponseDTO> response = overridesResource.updateGitMetadataForServiceOverride(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, serviceOverrideGitUpdateRequestDTO, null);
    Assertions.assertThat(response.getData().getIdentifier()).isEqualTo("environmentRef_serviceRef");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testYamlSchemaValidationInCreateAndUpdateCalls() throws IOException {
    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .identifier("i1")
                                                          .environmentRef("e1")
                                                          .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                                          .spec(ServiceOverridesSpec.builder().build())
                                                          .build();
    NGOverrideGovernanceDataResponse ngOverrideGovernanceDataResponse =
        NGOverrideGovernanceDataResponse.builder().overridesEntity(serviceOverridesEntity).build();
    doReturn(ngOverrideGovernanceDataResponse).when(serviceOverridesServiceV2).create(any(), any());
    doReturn(ngOverrideGovernanceDataResponse).when(serviceOverridesServiceV2).update(any(), any());
    OverrideRequestDTO requestV2 =
        OverrideRequestDTO.builder().type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE).yaml("").build();
    overridesResource.create(ACCOUNT_ID, requestV2, null);
    verify(serviceOverridesV2YamlSchemaHelper, times(1)).validateSchema(any());
    overridesResource.update(ACCOUNT_ID, requestV2, null);
    verify(serviceOverridesV2YamlSchemaHelper, times(2)).validateSchema(any());
  }
}

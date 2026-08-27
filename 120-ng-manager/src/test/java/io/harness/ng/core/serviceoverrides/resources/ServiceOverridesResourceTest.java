/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.resources;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.ng.core.opa.gitx.ServiceOverrideOpaStatusHandler;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.OverrideFilterPropertiesDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideGitUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import software.wings.beans.ServiceKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class ServiceOverridesResourceTest extends CategoryTest {
  @InjectMocks ServiceOverridesResource serviceOverridesResource;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock FilterService filterService;
  @Mock ServiceOverrideValidatorService serviceOverrideValidatorService;
  @Mock ServiceOverridesV2YamlSchemaHelper serviceOverridesV2YamlSchemaHelper;
  @Mock ScopeInfoService scopeInfoService;
  @Mock ServiceOverrideOpaStatusHandler serviceOverrideOpaStatusHandler;
  @Mock CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private ScopeInfo scopeInfo;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJ_IDENTIFIER)
                    .uniqueId("uniqueId")
                    .build();
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testV2ListApiForOverridesWithFilterIdentifier() {
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
    serviceOverridesResource.listServiceOverrides(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        ServiceOverridesType.ENV_SERVICE_OVERRIDE, null, null, "filter", scopeInfo);
    verify(serviceOverridesServiceV2, times(1)).list(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testV2ListApiForOverridesWithFilterProperties() {
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
    serviceOverridesResource.listServiceOverrides(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        ServiceOverridesType.ENV_SERVICE_OVERRIDE, overrideFilterPropertiesDTO, null, "filter", scopeInfo);
    verify(serviceOverridesServiceV2, times(1)).list(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("serviceRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("serviceRef").toString()).contains("Svc1");
    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("environmentRef");
    Assertions.assertThat(criteria.getCriteriaObject().get("environmentRef").toString()).contains("Env1");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
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
        .updateGitMetadata(any(), any(), any());

    ResponseDTO<ServiceOverrideGitUpdateResponseDTO> response =
        serviceOverridesResource.updateGitMetadataForServiceOverride(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, serviceOverrideGitUpdateRequestDTO, scopeInfo);
    Assertions.assertThat(response.getData().getIdentifier()).isEqualTo("environmentRef_serviceRef");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListServiceOverridesV3() {
    NGServiceOverridesEntity overridesEntity = NGServiceOverridesEntity.builder()
                                                   .identifier("i1")
                                                   .environmentRef("e1")
                                                   .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                                   .spec(ServiceOverridesSpec.builder().build())
                                                   .build();
    List<NGServiceOverridesEntity> list = new ArrayList<>();
    list.add(overridesEntity);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<NGServiceOverridesEntity> page = new PageImpl<>(Collections.singletonList(overridesEntity), pageable, 1);
    doReturn(page).when(serviceOverridesServiceV2).list(any(), any());
    doReturn(list).when(serviceOverrideValidatorService).getPermittedOverridesList(any(), anyBoolean(), any());
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    ResponseDTO<PageResponse<ServiceOverridesResponseDTOV2>> response = serviceOverridesResource.listServiceOverridesV3(
        0, 500, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, scopeInfo);

    verify(serviceOverrideValidatorService, times(1)).getPermittedOverridesList(any(), anyBoolean(), any());
    verify(serviceOverridesServiceV2, times(2)).list(criteriaArgumentCaptor.capture(), any());

    Criteria criteria = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria.getCriteriaObject()).containsKey("$and");
    Assertions.assertThat(criteria.getCriteriaObject().toJson()).contains("i1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListServiceOverridesV3WithSearchTermCombinesIdentifierCriteria() {
    NGServiceOverridesEntity overridesEntity = NGServiceOverridesEntity.builder()
                                                   .identifier("i1")
                                                   .environmentRef("e1")
                                                   .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                                                   .spec(ServiceOverridesSpec.builder().build())
                                                   .build();
    List<NGServiceOverridesEntity> list = new ArrayList<>();
    list.add(overridesEntity);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<NGServiceOverridesEntity> page = new PageImpl<>(Collections.singletonList(overridesEntity), pageable, 1);
    doReturn(page).when(serviceOverridesServiceV2).list(any(), any());
    doReturn(list).when(serviceOverrideValidatorService).getPermittedOverridesList(any(), anyBoolean(), any());
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    serviceOverridesResource.listServiceOverridesV3(
        0, 500, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "search", null, scopeInfo);

    verify(serviceOverrideValidatorService, times(1)).getPermittedOverridesList(any(), anyBoolean(), any());
    verify(serviceOverridesServiceV2, times(2)).list(criteriaArgumentCaptor.capture(), any());

    Criteria firstCriteria = criteriaArgumentCaptor.getAllValues().get(0);
    Criteria pagedCriteria = criteriaArgumentCaptor.getAllValues().get(1);
    Assertions.assertThat(firstCriteria.getCriteriaObject().toJson()).contains("search");
    Assertions.assertThat(pagedCriteria.getCriteriaObject()).containsKey("$and");
    Assertions.assertThat(pagedCriteria.getCriteriaObject().toJson()).contains("search");
    Assertions.assertThat(pagedCriteria.getCriteriaObject().toJson()).contains("i1");
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testGetSetsOpaOnSaveStatus() {
    NGServiceOverridesEntity overridesEntity = NGServiceOverridesEntity.builder()
                                                   .identifier("envId_svcId")
                                                   .environmentRef("envId")
                                                   .serviceRef("svcId")
                                                   .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
                                                   .spec(ServiceOverridesSpec.builder().build())
                                                   .build();
    doReturn(Optional.of(overridesEntity))
        .when(serviceOverridesServiceV2)
        .get(any(), any(), anyBoolean(), anyBoolean());
    when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    OpaOnSaveStatusResponseDTO opaStatus =
        OpaOnSaveStatusResponseDTO.builder().status(OpaOnSaveEvaluationStatus.SUCCESS).build();
    when(cdOpaOnSaveStatusApiHelper.resolveGetOpaOnSaveStatus(any(), any(), any(), any()))
        .thenReturn(Optional.of(opaStatus));

    ServiceOverridesResponseDTOV2 response =
        serviceOverridesResource
            .get("envId_svcId", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", false, false, scopeInfo)
            .getData();

    Assertions.assertThat(response.getOpaOnSaveStatus()).isEqualTo(opaStatus);
    verify(cdOpaOnSaveStatusApiHelper).resolveGetOpaOnSaveStatus(any(), any(), any(), any());
  }
}

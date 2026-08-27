/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionCategory;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncStatus;
import io.harness.gitops.models.ApplicationSyncStatusList;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.dto.ApplicationSyncStatusDTO;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RunWith(MockitoJUnitRunner.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CDDashboardOverviewResourceTest {
  private static final String ACCOUNT_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";

  @Mock CDOverviewDashboardService cdOverviewDashboardService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock ScopeInfoService scopeResolverService;

  @InjectMocks CDDashboardOverviewResource cdDashboardOverviewResource;

  @Before
  public void setUp() {
    // No setup needed for this test since we're only testing the mapper method
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testMapToApplicationSyncStatusDTOPage_nullSource() {
    // When
    Page<ApplicationSyncStatusDTO> result =
        cdDashboardOverviewResource.mapToApplicationSyncStatusDTOPage(null, PageRequest.of(0, 10));

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testMapToApplicationSyncStatusDTOPage_nullTotalItems() {
    // Given
    ApplicationSyncStatusList source = ApplicationSyncStatusList.builder().content(new ArrayList<>()).build();
    Pageable pageable = PageRequest.of(0, 10);

    // When
    Page<ApplicationSyncStatusDTO> result =
        cdDashboardOverviewResource.mapToApplicationSyncStatusDTOPage(source, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getTotalPages()).isZero();
    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testMapToApplicationSyncStatusDTOPage_emptyContent() {
    // Given
    ApplicationSyncStatusList source =
        ApplicationSyncStatusList.builder().totalItems(0).totalPages(0).content(Collections.emptyList()).build();
    Pageable pageable = PageRequest.of(0, 10);

    // When
    Page<ApplicationSyncStatusDTO> result =
        cdDashboardOverviewResource.mapToApplicationSyncStatusDTOPage(source, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getContent()).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testMapToApplicationSyncStatusDTOPage_withContent() {
    // Given
    int timestamp1 = 1672574400; // 2023-01-01T12:00:00Z
    int timestamp2 = 1672570800; // 2023-01-01T11:00:00Z

    List<ApplicationSyncStatus> statusList =
        Arrays.asList(createApplicationSyncStatus("app1", timestamp1), createApplicationSyncStatus("app2", timestamp2));

    ApplicationSyncStatusList source =
        ApplicationSyncStatusList.builder().totalItems(statusList.size()).totalPages(1).content(statusList).build();
    Pageable pageable = PageRequest.of(0, 10);

    // When
    Page<ApplicationSyncStatusDTO> result =
        cdDashboardOverviewResource.mapToApplicationSyncStatusDTOPage(source, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).hasSize(2);

    // Verify first item
    ApplicationSyncStatusDTO firstResult = result.getContent().get(0);
    assertThat(firstResult.getApplicationName()).isEqualTo("app1");
    assertThat(firstResult.getCreatedAt()).isEqualTo(timestamp1);

    // Verify second item
    ApplicationSyncStatusDTO secondResult = result.getContent().get(1);
    assertThat(secondResult.getApplicationName()).isEqualTo("app2");
    assertThat(secondResult.getCreatedAt()).isEqualTo(timestamp2);
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void testMapToApplicationSyncStatusDTOPage_allFieldsMapping() {
    // Given
    ApplicationSyncStatus status = createFullApplicationSyncStatus();

    ApplicationSyncStatusList source = ApplicationSyncStatusList.builder()
                                           .totalItems(1)
                                           .totalPages(1)
                                           .content(Collections.singletonList(status))
                                           .build();
    Pageable pageable = PageRequest.of(0, 10);

    // When
    Page<ApplicationSyncStatusDTO> result =
        cdDashboardOverviewResource.mapToApplicationSyncStatusDTOPage(source, pageable);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).hasSize(1);

    ApplicationSyncStatusDTO dto = result.getContent().get(0);
    assertThat(dto.getAccountIdentifier()).isEqualTo("account123");
    assertThat(dto.getProjectIdentifier()).isEqualTo("project123");
    assertThat(dto.getOrgIdentifier()).isEqualTo("org123");
    assertThat(dto.getAgentIdentifier()).isEqualTo("agent123");
    assertThat(dto.getApplicationName()).isEqualTo("testApp");
    assertThat(dto.getCreatedAt()).isEqualTo(1672574400); // 2023-01-01T12:00:00Z
    assertThat(dto.getLastModifiedAt()).isEqualTo(1672660800); // 2023-01-02T12:00:00Z
    assertThat(dto.getReqIdentifier()).isEqualTo("req123");
    assertThat(dto.getLastKnownRevisionId()).isEqualTo(123);
    assertThat(dto.getSyncedBy()).isNotNull();
    assertThat(dto.getSyncedBy().getId()).isEqualTo("user123");
    assertThat(dto.getAutoSyncCount()).isEqualTo(5);
    assertThat(dto.getServiceRef()).isEqualTo("service123");
    assertThat(dto.getEnvRef()).isEqualTo("env123");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getServicesList_nullServiceDefinitionType_passesNullFilter() throws Exception {
    when(cdOverviewDashboardService.getServicesList(
             any(), any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt(), any(), any(), any()))
        .thenReturn(PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).build());

    cdDashboardOverviewResource.getServicesList(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, 0, 100, null, null, null);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService)
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), isNull(), isNull(), eq(100), eq(0), isNull(),
            any(), filterCaptor.capture());
    assertThat(filterCaptor.getValue()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getServicesList_withServiceDefinitionType_passesFilterWithSingleServiceType() throws Exception {
    when(cdOverviewDashboardService.getServicesList(
             any(), any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt(), any(), any(), any()))
        .thenReturn(PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).build());

    cdDashboardOverviewResource.getServicesList(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, 0, 100, null, "AiAgent", null);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService)
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), isNull(), isNull(), eq(100), eq(0), isNull(),
            any(), filterCaptor.capture());
    assertThat(filterCaptor.getValue()).isNotNull();
    assertThat(filterCaptor.getValue().getServiceTypes()).containsExactly("AiAgent");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getServicesList_withCategory_passesFilterWithCategory() throws Exception {
    when(cdOverviewDashboardService.getServicesList(
             any(), any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt(), any(), any(), any()))
        .thenReturn(PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).build());

    cdDashboardOverviewResource.getServicesList(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, 0, 100, null, null, ServiceDefinitionCategory.AI_SERVICE);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService)
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), isNull(), isNull(), eq(100), eq(0), isNull(),
            any(), filterCaptor.capture());
    assertThat(filterCaptor.getValue()).isNotNull();
    assertThat(filterCaptor.getValue().getServiceTypes()).isNull();
    assertThat(filterCaptor.getValue().getCategory()).isEqualTo(ServiceDefinitionCategory.AI_SERVICE);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void getServicesList_withServiceDefinitionTypeAndCategory_passesBothToFilter() throws Exception {
    when(cdOverviewDashboardService.getServicesList(
             any(), any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt(), any(), any(), any()))
        .thenReturn(PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).build());

    cdDashboardOverviewResource.getServicesList(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, 0, 100, null, "AwsAgentCore", ServiceDefinitionCategory.AI_SERVICE);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService)
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), isNull(), isNull(), eq(100), eq(0), isNull(),
            any(), filterCaptor.capture());
    assertThat(filterCaptor.getValue()).isNotNull();
    assertThat(filterCaptor.getValue().getServiceTypes()).containsExactly("AwsAgentCore");
    assertThat(filterCaptor.getValue().getCategory()).isEqualTo(ServiceDefinitionCategory.AI_SERVICE);
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getActiveServiceDeployments_passesParamsToService() {
    when(ngFeatureFlagHelperService.isEnabled(
             any(), eq(FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)))
        .thenReturn(false);
    when(cdOverviewDashboardService.getActiveServiceDeploymentsList(any(), any(), any(), any()))
        .thenReturn(InstanceGroupedByServiceList.InstanceGroupedByService.builder().build());

    cdDashboardOverviewResource.getActiveServiceDeployments(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc");

    verify(cdOverviewDashboardService).getActiveServiceDeploymentsList(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc");
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getActiveServiceDeployments_nullParams_passedThrough() {
    when(ngFeatureFlagHelperService.isEnabled(
             any(), eq(FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)))
        .thenReturn(false);
    when(cdOverviewDashboardService.getActiveServiceDeploymentsList(any(), any(), any(), any()))
        .thenReturn(InstanceGroupedByServiceList.InstanceGroupedByService.builder().build());

    cdDashboardOverviewResource.getActiveServiceDeployments(ACCOUNT_ID, null, null, "svc");

    verify(cdOverviewDashboardService).getActiveServiceDeploymentsList(eq(ACCOUNT_ID), isNull(), isNull(), eq("svc"));
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getOpenTasks_passesParamsToService() {
    when(ngFeatureFlagHelperService.isEnabled(
             any(), eq(FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)))
        .thenReturn(false);
    when(cdOverviewDashboardService.getOpenTasks(any(), any(), any(), any(), eq(1000L)))
        .thenReturn(OpenTaskDetails.builder().build());

    cdDashboardOverviewResource.getOpenTasks(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc", 1000L);

    verify(cdOverviewDashboardService).getOpenTasks(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc", 1000L);
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getDeploymentsByServiceId_passesParamsToService() {
    when(ngFeatureFlagHelperService.isEnabled(
             any(), eq(FeatureName.CDS_MOVE_TIMESCALE_SQL_QUERIES_TO_JOOQ_FOR_NG_MANAGER)))
        .thenReturn(false);
    when(cdOverviewDashboardService.getDeploymentsByServiceId(any(), any(), any(), any(), eq(1000L), eq(2000L)))
        .thenReturn(DeploymentsInfo.builder().build());

    cdDashboardOverviewResource.getDeploymentsByServiceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc", 1000L, 2000L);

    verify(cdOverviewDashboardService).getDeploymentsByServiceId(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc", 1000L, 2000L);
  }

  private ApplicationSyncStatus createApplicationSyncStatus(String appName, int createdAt) {
    return ApplicationSyncStatus.builder()
        .applicationName(appName)
        .syncStatus(ApplicationResource.SyncResult.builder().build())
        .createdAt(createdAt)
        .build();
  }

  private ApplicationSyncStatus createFullApplicationSyncStatus() {
    return ApplicationSyncStatus.builder()
        .accountIdentifier("account123")
        .projectIdentifier("project123")
        .orgIdentifier("org123")
        .agentIdentifier("agent123")
        .applicationName("testApp")
        .syncStatus(ApplicationResource.SyncResult.builder().build())
        .createdAt(1672574400) // 2023-01-01T12:00:00Z
        .lastModifiedAt(1672660800) // 2023-01-02T12:00:00Z
        .operationState(ApplicationResource.OperationState.builder().build())
        .reqIdentifier("req123")
        .lastKnownRevisionId(123)
        .syncedBy(ApplicationSyncStatus.User.builder().id("user123").build())
        .autoSyncCount(5)
        .serviceRef("service123")
        .envRef("env123")
        .build();
  }
}

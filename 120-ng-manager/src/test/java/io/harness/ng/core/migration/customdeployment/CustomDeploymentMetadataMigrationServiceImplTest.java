/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.customdeployment;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationRequestDTO.EntityType;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationRequestDTO.MigrationMode;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentMetadataMigrationServiceImplTest extends NgManagerTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String DEFAULT_BRANCH = "main";
  private static final String FALLBACK_BRANCH = "feature/branch";
  private static final String YAML = "service:\n  spec:\n    customDeploymentRef: templateRef";

  @Mock ServiceRepository serviceRepository;
  @Mock InfrastructureRepository infrastructureRepository;
  @Mock ScmFacilitatorService scmFacilitatorService;
  @Mock CustomDeploymentEntitySetupHelper helper;
  @Mock ScopeInfoService scopeInfoService;
  @Mock ExecutorService migrationExecutor;

  @InjectMocks CustomDeploymentMetadataMigrationServiceImpl migrationService;

  @Before
  public void setUp() {
    doAnswer(inv -> {
      ((Runnable) inv.getArgument(0)).run();
      return null;
    })
        .when(migrationExecutor)
        .submit(any(Runnable.class));
    lenient()
        .when(serviceRepository.findAll(any(), any(), eq(false), anySet()))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    lenient()
        .when(infrastructureRepository.findAll(any(), any(), eq(false), anySet()))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    lenient().when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo());
    lenient().when(helper.extractCustomDeploymentRefFromServiceYaml(any())).thenReturn(new String[] {"ref", "v1"});
    lenient()
        .when(scmFacilitatorService.getFileByBranchV2(any()))
        .thenReturn(ScmGetFileResponseDTO.builder().fileContent(YAML).build());
    lenient()
        .when(
            scmFacilitatorService.listBranchesV2(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(GitBranchesResponseDTO.builder()
                        .defaultBranch(branchDTO(DEFAULT_BRANCH))
                        .branches(Collections.emptyList())
                        .build());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void entityTypeService_skipsInfra() {
    trigger(EntityType.SERVICE, null);
    verifyNoInteractions(infrastructureRepository);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void entityTypeInfra_skipsServices() {
    trigger(EntityType.INFRA, null);
    verifyNoInteractions(serviceRepository);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void defaultAndFallback_processesDistinctFallbackAndDefaultBranches() {
    givenService("svc1", FALLBACK_BRANCH);
    trigger(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK);
    assertThat(capturedBranches()).containsExactlyInAnyOrder(DEFAULT_BRANCH, FALLBACK_BRANCH);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void defaultAndFallback_deduplicatesWhenFallbackEqualsDefault() {
    givenService("svc1", DEFAULT_BRANCH);
    trigger(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK);
    verify(scmFacilitatorService, times(1)).getFileByBranchV2(any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void allBranches_processesEveryBranchFromRepo() {
    givenService("svc1", FALLBACK_BRANCH);
    when(scmFacilitatorService.listBranchesV2(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(GitBranchesResponseDTO.builder()
                        .branches(List.of(branchDTO("main"), branchDTO("dev"), branchDTO("feature")))
                        .build());
    trigger(EntityType.SERVICE, MigrationMode.ALL_BRANCHES);
    verify(scmFacilitatorService, times(3)).getFileByBranchV2(any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void additionalBranches_appendedToBranchSet() {
    givenService("svc1", DEFAULT_BRANCH);
    migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        dto(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK, List.of("release/1.5")));
    assertThat(capturedBranches()).containsExactlyInAnyOrder(DEFAULT_BRANCH, "release/1.5");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void nonCustomDeploymentStoredYaml_entitySkipped() {
    givenService("svc1", FALLBACK_BRANCH);
    when(helper.extractCustomDeploymentRefFromServiceYaml(any())).thenReturn(new String[] {"", ""});
    trigger(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK);
    verifyNoInteractions(scmFacilitatorService);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void fileFetchException_skippedSilentlyAndOtherEntitiesProcessed() {
    when(serviceRepository.findAll(any(), any(), eq(false), anySet()))
        .thenReturn(new PageImpl<>(
            List.of(service("svc1", DEFAULT_BRANCH, "f1.yaml"), service("svc2", DEFAULT_BRANCH, "f2.yaml"))));
    when(scmFacilitatorService.getFileByBranchV2(argThat(r -> "f1.yaml".equals(r.getFilePath()))))
        .thenThrow(new RuntimeException("not found"));
    assertThatCode(() -> trigger(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK)).doesNotThrowAnyException();
    verify(serviceRepository, times(1)).updateEntity(any(), any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void update_calledWithCorrectParams() {
    givenService("svc1", FALLBACK_BRANCH);
    trigger(EntityType.SERVICE, MigrationMode.DEFAULT_AND_FALLBACK);
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceRepository, atLeastOnce()).updateEntity(criteriaCaptor.capture(), any());
    Criteria criteria = criteriaCaptor.getAllValues().get(0);
    assertThat(criteria.getCriteriaObject().get("accountId")).isEqualTo(ACCOUNT_ID);
    assertThat(criteria.getCriteriaObject().get("orgIdentifier")).isEqualTo(ORG_ID);
    assertThat(criteria.getCriteriaObject().get("projectIdentifier")).isEqualTo(PROJECT_ID);
    assertThat(criteria.getCriteriaObject().get("identifier")).isEqualTo("svc1");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void dryRun_skipsDbWrite() {
    givenService("svc1", FALLBACK_BRANCH);
    migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID, dryRunDto(EntityType.SERVICE, null));
    verify(serviceRepository, never()).updateEntity(any(), any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void dryRun_scmFetchStillExecuted() {
    givenService("svc1", FALLBACK_BRANCH);
    migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID, dryRunDto(EntityType.SERVICE, null));
    verify(scmFacilitatorService, atLeastOnce()).getFileByBranchV2(any());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void withOrg_scopeInfoResolvedForParentUniqueId() {
    trigger(EntityType.SERVICE, null);
    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void accountOnly_scopeInfoCalled() {
    migrationService.triggerMigration(ACCOUNT_ID, null, null, dto(EntityType.SERVICE, null, null));
    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void nullEntityType_throwsInvalidRequestException() {
    assertThatCode(() -> migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID, dto(null, null, null)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void triggerMigration_returnsNonNullRunId() {
    String runId =
        migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID, dto(EntityType.SERVICE, null, null));
    assertThat(runId).isNotBlank();
  }

  // --- helpers ---

  private void trigger(EntityType entityType, MigrationMode mode) {
    migrationService.triggerMigration(ACCOUNT_ID, ORG_ID, PROJECT_ID, dto(entityType, mode, null));
  }

  private CustomDeploymentMetadataMigrationRequestDTO dto(
      EntityType entityType, MigrationMode mode, List<String> additionalBranches) {
    CustomDeploymentMetadataMigrationRequestDTO dto = new CustomDeploymentMetadataMigrationRequestDTO();
    dto.setEntityType(entityType);
    dto.setMigrationMode(mode);
    dto.setAdditionalBranches(additionalBranches);
    return dto;
  }

  private CustomDeploymentMetadataMigrationRequestDTO dryRunDto(EntityType entityType, MigrationMode mode) {
    CustomDeploymentMetadataMigrationRequestDTO dto = new CustomDeploymentMetadataMigrationRequestDTO();
    dto.setEntityType(entityType);
    dto.setMigrationMode(mode);
    dto.setDryRun(true);
    return dto;
  }

  private void givenService(String id, String fallBackBranch) {
    when(serviceRepository.findAll(any(), any(), eq(false), anySet()))
        .thenReturn(new PageImpl<>(List.of(service(id, fallBackBranch, "svc.yaml"))));
  }

  private ServiceEntity service(String id, String fallBackBranch, String filePath) {
    return ServiceEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .identifier(id)
        .storeType(StoreType.REMOTE)
        .repo("repo")
        .connectorRef("connector")
        .filePath(filePath)
        .fallBackBranch(fallBackBranch)
        .yaml(YAML)
        .deleted(false)
        .build();
  }

  private Set<String> capturedBranches() {
    ArgumentCaptor<ScmGetFileByBranchRequestDTO> captor = ArgumentCaptor.forClass(ScmGetFileByBranchRequestDTO.class);
    verify(scmFacilitatorService, atLeastOnce()).getFileByBranchV2(captor.capture());
    return captor.getAllValues().stream().map(ScmGetFileByBranchRequestDTO::getBranchName).collect(Collectors.toSet());
  }

  private GitBranchDetailsDTO branchDTO(String name) {
    return GitBranchDetailsDTO.builder().name(name).build();
  }

  private ScopeInfo scopeInfo() {
    return new ScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, ScopeLevel.PROJECT, "projectUniqueId");
  }
}

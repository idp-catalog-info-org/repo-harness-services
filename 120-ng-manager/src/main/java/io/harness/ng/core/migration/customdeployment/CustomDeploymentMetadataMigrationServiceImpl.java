/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.customdeployment;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.BranchFilterParameters;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.entity.metadata.TemplateMetadata;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationRequestDTO.EntityType;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationRequestDTO.InfraTarget;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationRequestDTO.MigrationMode;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.helpers.ServiceFilterHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentMetadataMigrationServiceImpl implements CustomDeploymentMetadataMigrationService {
  private static final int PAGE_SIZE = 100;
  private static final int BRANCH_PAGE_SIZE = 500;

  @Inject private ServiceRepository serviceRepository;
  @Inject private InfrastructureRepository infrastructureRepository;
  @Inject private ScmFacilitatorService scmFacilitatorService;
  @Inject private CustomDeploymentEntitySetupHelper customDeploymentEntitySetupHelper;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject @Named("customDeploymentMetadataMigrationExecutor") private ExecutorService migrationExecutor;

  @Override
  public String triggerMigration(
      String accountId, String orgId, String projectId, CustomDeploymentMetadataMigrationRequestDTO requestDTO) {
    String runId = UUID.randomUUID().toString();

    EntityType entityType = requestDTO != null ? requestDTO.getEntityType() : null;
    MigrationMode mode = (requestDTO == null || requestDTO.getMigrationMode() == null)
        ? MigrationMode.DEFAULT_AND_FALLBACK
        : requestDTO.getMigrationMode();
    List<String> additionalBranches = (requestDTO != null && requestDTO.getAdditionalBranches() != null)
        ? requestDTO.getAdditionalBranches()
        : List.of();
    List<String> serviceIdentifiers = (requestDTO != null && requestDTO.getServiceIdentifiers() != null)
        ? requestDTO.getServiceIdentifiers()
        : List.of();
    List<InfraTarget> infraTargets =
        (requestDTO != null && requestDTO.getInfraTargets() != null) ? requestDTO.getInfraTargets() : List.of();
    boolean dryRun = requestDTO != null && requestDTO.isDryRun();

    if (entityType == null) {
      throw new InvalidRequestException("[CustomDeploymentMetadataMigration] [runId=" + runId
          + "] entityType is required. Supported values: SERVICE, INFRA");
    }

    migrationExecutor.submit(() -> {
      try (GlobalContextManager.GlobalContextGuard ignored = GlobalContextManager.ensureGlobalContextGuard()) {
        SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(new ServicePrincipal(NG_MANAGER.getServiceId()));
        try {
          log.info(
              "[CustomDeploymentMetadataMigration] [runId={}] [STATUS] STARTED: account=[{}] org=[{}] project=[{}] "
                  + "entityType=[{}] mode=[{}] dryRun=[{}]",
              runId, accountId, orgId, projectId, entityType, mode, dryRun);
          if (entityType == EntityType.SERVICE) {
            migrateServices(runId, accountId, orgId, projectId, mode, additionalBranches, serviceIdentifiers, dryRun);
          }
          if (entityType == EntityType.INFRA) {
            migrateInfrastructures(runId, accountId, orgId, projectId, mode, additionalBranches, infraTargets, dryRun);
          }
          log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] COMPLETED: account=[{}] dryRun=[{}]", runId,
              accountId, dryRun);
        } catch (Exception e) {
          log.error(
              "[CustomDeploymentMetadataMigration] [runId={}] [STATUS] FAILED: account=[{}]", runId, accountId, e);
        }
      }
    });
    return runId;
  }

  private void migrateServices(String runId, String accountId, String orgId, String projectId, MigrationMode mode,
      List<String> additionalBranches, List<String> identifiers, boolean dryRun) {
    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(accountId)
                            .and(ServiceEntityKeys.storeType)
                            .is(StoreType.REMOTE.name());
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgId, projectId);
    criteria.and(ServiceEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    if (!identifiers.isEmpty()) {
      criteria.and(ServiceEntityKeys.identifier).in(identifiers);
    }
    log.info("[CustomDeploymentMetadataMigration] [runId={}] DB query: accountId=[{}] parentUniqueId=[{}] "
            + "storeType=REMOTE{}",
        runId, accountId, scopeInfo.getUniqueId(), identifiers.isEmpty() ? "" : " identifiers=" + identifiers);

    Map<String, List<ServiceEntity>> repoToServicesMap = new HashMap<>();
    int pageNum = 0;
    Page<ServiceEntity> page;
    int totalServices = 0;
    int customDeploymentServices = 0;

    do {
      // Use the overload with explicit empty exclusion set so templateMetadata is included in the projection.
      // The single-boolean overload always excludes templateMetadata, which would break the up-to-date check.
      page = serviceRepository.findAll(criteria, PageRequest.of(pageNum, PAGE_SIZE), false, Collections.emptySet());
      for (ServiceEntity service : page.getContent()) {
        totalServices++;
        String yaml = service.getYaml();
        if (isEmpty(yaml)) {
          continue;
        }
        String[] cdRef = customDeploymentEntitySetupHelper.extractCustomDeploymentRefFromServiceYaml(yaml);
        if (isEmpty(cdRef[0])) {
          continue;
        }
        customDeploymentServices++;
        String repoKey = buildRepoKey(
            service.getOrgIdentifier(), service.getProjectIdentifier(), service.getRepo(), service.getConnectorRef());
        repoToServicesMap.computeIfAbsent(repoKey, k -> new ArrayList<>()).add(service);
      }
      pageNum++;
    } while (!page.isLast());

    log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] DB_SCAN_COMPLETE: account=[{}] "
            + "totalRemoteServices={} customDeploymentServices={}",
        runId, accountId, totalServices, customDeploymentServices);

    int updatedCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    for (Map.Entry<String, List<ServiceEntity>> entry : repoToServicesMap.entrySet()) {
      List<ServiceEntity> services = entry.getValue();
      ServiceEntity representative = services.get(0);
      int[] counts = processRepoForServices(runId, accountId, representative.getOrgIdentifier(),
          representative.getProjectIdentifier(), representative.getRepo(), representative.getConnectorRef(), services,
          mode, additionalBranches, dryRun);
      updatedCount += counts[0];
      skippedCount += counts[1];
      failedCount += counts[2];
      log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] REPO_DONE: repo=[{}] updated={} skipped={} "
              + "failed={} service-branch pairs",
          runId, representative.getRepo(), counts[0], counts[1], counts[2]);
    }

    log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] SERVICE_MIGRATION_SUMMARY: account=[{}] "
            + "updated={} skipped={} failed={} service-branch pairs",
        runId, accountId, updatedCount, skippedCount, failedCount);
    Map<String, List<TemplateMetadata>> serviceTemplateMetadataMap = new HashMap<>();
    repoToServicesMap.values()
        .stream()
        .flatMap(List::stream)
        .forEach(service -> serviceTemplateMetadataMap.put(service.getIdentifier(), service.getTemplateMetadata()));
  }

  // Returns int[]{updated, skipped, failed}
  private int[] processRepoForServices(String runId, String accountId, String orgId, String projectId, String repoName,
      String connectorRef, List<ServiceEntity> services, MigrationMode mode, List<String> additionalBranches,
      boolean dryRun) {
    if (mode == MigrationMode.ALL_BRANCHES) {
      List<GitBranchDetailsDTO> allBranches = listBranches(runId, accountId, orgId, projectId, connectorRef, repoName);
      if (allBranches.isEmpty()) {
        log.warn("[CustomDeploymentMetadataMigration] [runId={}] No branches found for repo [{}] in account [{}]",
            runId, repoName, accountId);
        return new int[] {0, 0, 0};
      }
      Set<String> branchSet = new LinkedHashSet<>();
      allBranches.forEach(b -> branchSet.add(b.getName()));
      branchSet.addAll(additionalBranches);

      int[] counts = new int[3];
      int branchIndex = 0;
      int totalBranches = branchSet.size();
      for (String branchName : branchSet) {
        branchIndex++;
        log.info(
            "[CustomDeploymentMetadataMigration] [runId={}] [HEARTBEAT] Services: branch [{}/{}] [{}] in repo [{}]",
            runId, branchIndex, totalBranches, branchName, repoName);
        for (ServiceEntity service : services) {
          int[] result = updateServiceForBranch(
              runId, accountId, orgId, projectId, repoName, connectorRef, service, branchName, dryRun);
          counts[0] += result[0];
          counts[1] += result[1];
          counts[2] += result[2];
        }
      }
      return counts;
    }

    // DEFAULT_AND_FALLBACK: per entity, process its fallback branch + repo default branch + additionalBranches
    String defaultBranchName = getDefaultBranchName(runId, accountId, orgId, projectId, connectorRef, repoName);
    int[] counts = new int[3];
    for (ServiceEntity service : services) {
      Set<String> branchSet =
          buildFallbackBranchSet(defaultBranchName, service.getFallBackBranch(), additionalBranches);
      log.info("[CustomDeploymentMetadataMigration] [runId={}] Service [{}] will process branches: {}", runId,
          service.getIdentifier(), branchSet);
      for (String branchName : branchSet) {
        int[] result = updateServiceForBranch(
            runId, accountId, orgId, projectId, repoName, connectorRef, service, branchName, dryRun);
        counts[0] += result[0];
        counts[1] += result[1];
        counts[2] += result[2];
      }
    }
    return counts;
  }

  // Returns int[]{updated, skipped, failed}
  private int[] updateServiceForBranch(String runId, String accountId, String orgId, String projectId, String repoName,
      String connectorRef, ServiceEntity service, String branchName, boolean dryRun) {
    try {
      log.debug("[CustomDeploymentMetadataMigration] [runId={}] Fetching file [{}] from repo [{}] branch [{}] for "
              + "service [{}]",
          runId, service.getFilePath(), repoName, branchName, service.getIdentifier());
      ScmGetFileResponseDTO fileResponse =
          scmFacilitatorService.getFileByBranchV2(ScmGetFileByBranchRequestDTO.builder()
                                                      .scope(Scope.of(accountId, orgId, projectId))
                                                      .repoName(repoName)
                                                      .branchName(branchName)
                                                      .filePath(service.getFilePath())
                                                      .connectorRef(connectorRef)
                                                      .useCache(true)
                                                      .getOnlyFileContent(true)
                                                      .build());
      if (fileResponse == null || StringUtils.isBlank(fileResponse.getFileContent())) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Service [{}] branch [{}]: file not found or empty, "
                + "skipping",
            runId, service.getIdentifier(), branchName);
        return new int[] {0, 1, 0};
      }
      String[] cdRef =
          customDeploymentEntitySetupHelper.extractCustomDeploymentRefFromServiceYaml(fileResponse.getFileContent());
      if (StringUtils.isBlank(cdRef[0])) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Service [{}] branch [{}]: no custom deployment ref "
                + "found in YAML, skipping",
            runId, service.getIdentifier(), branchName);
        return new int[] {0, 1, 0};
      }
      String templateRef = cdRef[0];
      String templateVersion = StringUtils.isEmpty(cdRef[1]) ? "" : cdRef[1];

      List<TemplateMetadata> existingList =
          service.getTemplateMetadata() != null ? new ArrayList<>(service.getTemplateMetadata()) : new ArrayList<>();
      TemplateMetadata existing =
          existingList.stream().filter(m -> Objects.equals(m.getBranchName(), branchName)).findFirst().orElse(null);
      if (existing != null && Objects.equals(existing.getTemplateRef(), templateRef)
          && Objects.equals(existing.getTemplateVersion(), templateVersion)) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Service [{}] branch [{}] already up-to-date "
                + "(templateRef=[{}] templateVersion=[{}]), skipping",
            runId, service.getIdentifier(), branchName, existing.getTemplateRef(), existing.getTemplateVersion());
        return new int[] {0, 1, 0};
      }
      existingList.removeIf(m -> Objects.equals(m.getBranchName(), branchName));
      existingList.add(TemplateMetadata.builder()
                           .templateRef(templateRef)
                           .templateVersion(templateVersion)
                           .branchName(branchName)
                           .build());
      service.setTemplateMetadata(existingList);

      log.info("[CustomDeploymentMetadataMigration] [runId={}] {}service [{}] branch [{}]: upsert templateRef=[{}] "
              + "templateVersion=[{}]",
          runId, dryRun ? "[DRY_RUN] " : "", service.getIdentifier(), branchName, templateRef, templateVersion);

      if (dryRun) {
        return new int[] {1, 0, 0};
      }

      Criteria serviceCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                     .is(accountId)
                                     .and(ServiceEntityKeys.orgIdentifier)
                                     .is(service.getOrgIdentifier())
                                     .and(ServiceEntityKeys.projectIdentifier)
                                     .is(service.getProjectIdentifier())
                                     .and(ServiceEntityKeys.identifier)
                                     .is(service.getIdentifier());
      serviceRepository.updateEntity(
          serviceCriteria, ServiceFilterHelper.getUpdateOperationsForTemplateMetadataOnly(existingList));
      return new int[] {1, 0, 0};
    } catch (Exception e) {
      log.info("[CustomDeploymentMetadataMigration] [runId={}] Failed to fetch/update service [{}] for branch [{}] in "
              + "repo [{}]",
          runId, service.getIdentifier(), branchName, repoName, e);
      return new int[] {0, 0, 1};
    }
  }

  private void migrateInfrastructures(String runId, String accountId, String orgId, String projectId,
      MigrationMode mode, List<String> additionalBranches, List<InfraTarget> infraTargets, boolean dryRun) {
    Criteria criteria = Criteria.where(InfrastructureEntityKeys.accountId)
                            .is(accountId)
                            .and(InfrastructureEntityKeys.storeType)
                            .is(StoreType.REMOTE.name());

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgId, projectId);
    criteria.and(InfrastructureEntityKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    if (!infraTargets.isEmpty()) {
      Criteria[] targetCriteria =
          infraTargets.stream()
              .map(target -> {
                Criteria c = Criteria.where(InfrastructureEntityKeys.envIdentifier).is(target.getEnvIdentifier());
                if (isNotEmpty(target.getInfraIdentifiers())) {
                  c.and(InfrastructureEntityKeys.identifier).in(target.getInfraIdentifiers());
                }
                return c;
              })
              .toArray(Criteria[] ::new);
      criteria.andOperator(new Criteria().orOperator(targetCriteria));
    }
    log.info("[CustomDeploymentMetadataMigration] [runId={}] DB query: accountId=[{}] parentUniqueId=[{}] "
            + "storeType=REMOTE{}",
        runId, accountId, scopeInfo.getUniqueId(), infraTargets.isEmpty() ? "" : " infraTargets=" + infraTargets);

    Map<String, List<InfrastructureEntity>> repoToInfraMap = new HashMap<>();
    int pageNum = 0;
    Page<InfrastructureEntity> page;
    int totalInfras = 0;
    int customDeploymentInfras = 0;

    do {
      // Use the overload with explicit empty exclusion set so templateMetadata is included in the projection.
      // The single-boolean overload always excludes templateMetadata, which would break the up-to-date check.
      page =
          infrastructureRepository.findAll(criteria, PageRequest.of(pageNum, PAGE_SIZE), false, Collections.emptySet());
      for (InfrastructureEntity infra : page.getContent()) {
        totalInfras++;
        String yaml = infra.getYaml();
        if (StringUtils.isBlank(yaml)) {
          continue;
        }
        String[] cdRef = customDeploymentEntitySetupHelper.extractCustomDeploymentRefFromInfraYaml(yaml);
        if (StringUtils.isBlank(cdRef[0])) {
          continue;
        }
        customDeploymentInfras++;
        String repoKey = buildRepoKey(
            infra.getOrgIdentifier(), infra.getProjectIdentifier(), infra.getRepo(), infra.getConnectorRef());
        repoToInfraMap.computeIfAbsent(repoKey, k -> new ArrayList<>()).add(infra);
      }
      pageNum++;
    } while (!page.isLast());

    log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] DB_SCAN_COMPLETE: account=[{}] "
            + "totalRemoteInfras={} customDeploymentInfras={}",
        runId, accountId, totalInfras, customDeploymentInfras);

    int updatedCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    for (Map.Entry<String, List<InfrastructureEntity>> entry : repoToInfraMap.entrySet()) {
      List<InfrastructureEntity> infras = entry.getValue();
      InfrastructureEntity representative = infras.get(0);
      int[] counts = processRepoForInfras(runId, accountId, representative.getOrgIdentifier(),
          representative.getProjectIdentifier(), representative.getRepo(), representative.getConnectorRef(), infras,
          mode, additionalBranches, dryRun);
      updatedCount += counts[0];
      skippedCount += counts[1];
      failedCount += counts[2];
      log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] REPO_DONE: repo=[{}] updated={} skipped={} "
              + "failed={} infra-branch pairs",
          runId, representative.getRepo(), counts[0], counts[1], counts[2]);
    }

    log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] INFRA_MIGRATION_SUMMARY: account=[{}] updated={} "
            + "skipped={} failed={} infra-branch pairs",
        runId, accountId, updatedCount, skippedCount, failedCount);
    Map<String, List<TemplateMetadata>> infraTemplateMetadataMap = new HashMap<>();
    repoToInfraMap.values()
        .stream()
        .flatMap(List::stream)
        .forEach(infra
            -> infraTemplateMetadataMap.put(
                infra.getEnvIdentifier() + "|" + infra.getIdentifier(), infra.getTemplateMetadata()));
  }

  // Returns int[]{updated, skipped, failed}
  private int[] processRepoForInfras(String runId, String accountId, String orgId, String projectId, String repoName,
      String connectorRef, List<InfrastructureEntity> infras, MigrationMode mode, List<String> additionalBranches,
      boolean dryRun) {
    if (mode == MigrationMode.ALL_BRANCHES) {
      List<GitBranchDetailsDTO> allBranches = listBranches(runId, accountId, orgId, projectId, connectorRef, repoName);
      if (allBranches == null || allBranches.isEmpty()) {
        log.warn("[CustomDeploymentMetadataMigration] [runId={}] No branches found for repo [{}] in account [{}]",
            runId, repoName, accountId);
        return new int[] {0, 0, 0};
      }
      Set<String> branchSet = new LinkedHashSet<>();
      allBranches.forEach(b -> branchSet.add(b.getName()));
      branchSet.addAll(additionalBranches);

      int[] counts = new int[3];
      int branchIndex = 0;
      int totalBranches = branchSet.size();
      for (String branchName : branchSet) {
        branchIndex++;
        log.info("[CustomDeploymentMetadataMigration] [runId={}] [HEARTBEAT] Infras: branch [{}/{}] [{}] in repo [{}]",
            runId, branchIndex, totalBranches, branchName, repoName);
        for (InfrastructureEntity infra : infras) {
          int[] result = updateInfraForBranch(
              runId, accountId, orgId, projectId, repoName, connectorRef, infra, branchName, dryRun);
          counts[0] += result[0];
          counts[1] += result[1];
          counts[2] += result[2];
        }
      }
      return counts;
    }

    // DEFAULT_AND_FALLBACK: per entity, process its fallback branch + repo default branch + additionalBranches
    String defaultBranchName = getDefaultBranchName(runId, accountId, orgId, projectId, connectorRef, repoName);
    int[] counts = new int[3];
    for (InfrastructureEntity infra : infras) {
      Set<String> branchSet = buildFallbackBranchSet(defaultBranchName, infra.getFallBackBranch(), additionalBranches);
      log.info("[CustomDeploymentMetadataMigration] [runId={}] Infra [{}] (env=[{}]) will process branches: {}", runId,
          infra.getIdentifier(), infra.getEnvIdentifier(), branchSet);
      for (String branchName : branchSet) {
        int[] result =
            updateInfraForBranch(runId, accountId, orgId, projectId, repoName, connectorRef, infra, branchName, dryRun);
        counts[0] += result[0];
        counts[1] += result[1];
        counts[2] += result[2];
      }
    }
    return counts;
  }

  // Returns int[]{updated, skipped, failed}
  private int[] updateInfraForBranch(String runId, String accountId, String orgId, String projectId, String repoName,
      String connectorRef, InfrastructureEntity infra, String branchName, boolean dryRun) {
    try {
      log.debug(
          "[CustomDeploymentMetadataMigration] [runId={}] Fetching file [{}] from repo [{}] branch [{}] for infra "
              + "[{}] (env=[{}])",
          runId, infra.getFilePath(), repoName, branchName, infra.getIdentifier(), infra.getEnvIdentifier());
      ScmGetFileResponseDTO fileResponse =
          scmFacilitatorService.getFileByBranchV2(ScmGetFileByBranchRequestDTO.builder()
                                                      .scope(Scope.of(accountId, orgId, projectId))
                                                      .repoName(repoName)
                                                      .branchName(branchName)
                                                      .filePath(infra.getFilePath())
                                                      .connectorRef(connectorRef)
                                                      .useCache(true)
                                                      .getOnlyFileContent(true)
                                                      .build());
      if (fileResponse == null || StringUtils.isBlank(fileResponse.getFileContent())) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Infra [{}] (env=[{}]) branch [{}]: file not found or "
                + "empty, skipping",
            runId, infra.getIdentifier(), infra.getEnvIdentifier(), branchName);
        return new int[] {0, 1, 0};
      }
      String[] cdRef =
          customDeploymentEntitySetupHelper.extractCustomDeploymentRefFromInfraYaml(fileResponse.getFileContent());
      if (StringUtils.isBlank(cdRef[0])) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Infra [{}] (env=[{}]) branch [{}]: no custom "
                + "deployment ref found in YAML, skipping",
            runId, infra.getIdentifier(), infra.getEnvIdentifier(), branchName);
        return new int[] {0, 1, 0};
      }
      String templateRef = cdRef[0];
      String templateVersion = StringUtils.isEmpty(cdRef[1]) ? "" : cdRef[1];

      List<TemplateMetadata> existingList =
          infra.getTemplateMetadata() != null ? new ArrayList<>(infra.getTemplateMetadata()) : new ArrayList<>();
      TemplateMetadata existing =
          existingList.stream().filter(m -> Objects.equals(m.getBranchName(), branchName)).findFirst().orElse(null);
      if (existing != null && Objects.equals(existing.getTemplateRef(), templateRef)
          && Objects.equals(existing.getTemplateVersion(), templateVersion)) {
        log.info("[CustomDeploymentMetadataMigration] [runId={}] Infra [{}] (env=[{}]) branch [{}] already up-to-date "
                + "(templateRef=[{}] templateVersion=[{}]), skipping",
            runId, infra.getIdentifier(), infra.getEnvIdentifier(), branchName, existing.getTemplateRef(),
            existing.getTemplateVersion());
        return new int[] {0, 1, 0};
      }
      existingList.removeIf(m -> Objects.equals(m.getBranchName(), branchName));
      existingList.add(TemplateMetadata.builder()
                           .templateRef(templateRef)
                           .templateVersion(templateVersion)
                           .branchName(branchName)
                           .build());
      infra.setTemplateMetadata(existingList);

      log.info("[CustomDeploymentMetadataMigration] [runId={}] {}infra [{}] (env=[{}]) branch [{}]: upsert "
              + "templateRef=[{}] templateVersion=[{}]",
          runId, dryRun ? "[DRY_RUN] " : "", infra.getIdentifier(), infra.getEnvIdentifier(), branchName, templateRef,
          templateVersion);

      if (dryRun) {
        return new int[] {1, 0, 0};
      }

      Criteria infraCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                   .is(accountId)
                                   .and(InfrastructureEntityKeys.orgIdentifier)
                                   .is(infra.getOrgIdentifier())
                                   .and(InfrastructureEntityKeys.projectIdentifier)
                                   .is(infra.getProjectIdentifier())
                                   .and(InfrastructureEntityKeys.envIdentifier)
                                   .is(infra.getEnvIdentifier())
                                   .and(InfrastructureEntityKeys.identifier)
                                   .is(infra.getIdentifier());
      infrastructureRepository.updateEntityInDb(
          infraCriteria, InfrastructureFilterHelper.getUpdateOperationsForTemplateMetadataOnly(existingList));
      return new int[] {1, 0, 0};
    } catch (Exception e) {
      log.info("[CustomDeploymentMetadataMigration] [runId={}] Failed to fetch/update infra [{}] (env=[{}]) for branch "
              + "[{}] in repo [{}]",
          runId, infra.getIdentifier(), infra.getEnvIdentifier(), branchName, repoName, e);
      return new int[] {0, 0, 1};
    }
  }

  private List<GitBranchDetailsDTO> listBranches(
      String runId, String accountId, String orgId, String projectId, String connectorRef, String repoName) {
    try {
      // TODO [CDS-127197]: Only fetches the first BRANCH_PAGE_SIZE branches. Repos with >500 branches will silently
      // miss the remainder.
      GitBranchesResponseDTO response = scmFacilitatorService.listBranchesV2(accountId, orgId, projectId, connectorRef,
          repoName, io.harness.ng.beans.PageRequest.builder().pageSize(BRANCH_PAGE_SIZE).build(),
          BranchFilterParameters.builder().build(), null, false);
      if (response == null) {
        return new ArrayList<>();
      }
      List<GitBranchDetailsDTO> branches = new ArrayList<>();
      if (response.getBranches() != null) {
        branches.addAll(response.getBranches());
      }
      return branches;
    } catch (Exception e) {
      log.warn("[CustomDeploymentMetadataMigration] [runId={}] Failed to list branches for repo [{}] in account [{}]",
          runId, repoName, accountId, e);
      return new ArrayList<>();
    }
  }

  private String getDefaultBranchName(
      String runId, String accountId, String orgId, String projectId, String connectorRef, String repoName) {
    try {
      GitBranchesResponseDTO response = scmFacilitatorService.listBranchesV2(accountId, orgId, projectId, connectorRef,
          repoName, io.harness.ng.beans.PageRequest.builder().pageSize(1).build(),
          BranchFilterParameters.builder().build(), null, false);
      if (response != null && response.getDefaultBranch() != null) {
        return response.getDefaultBranch().getName();
      }
    } catch (Exception e) {
      log.warn(
          "[CustomDeploymentMetadataMigration] [runId={}] Failed to get default branch for repo [{}] in account [{}]",
          runId, repoName, accountId, e);
    }
    return null;
  }

  private Set<String> buildFallbackBranchSet(
      String defaultBranchName, String entityFallBackBranch, Collection<String> additionalBranches) {
    Set<String> branchSet = new LinkedHashSet<>();
    if (StringUtils.isNotBlank(defaultBranchName)) {
      branchSet.add(defaultBranchName);
    }
    if (StringUtils.isNotBlank(entityFallBackBranch)) {
      branchSet.add(entityFallBackBranch);
    }
    if (additionalBranches != null) {
      additionalBranches.stream().filter(StringUtils::isNotBlank).forEach(branchSet::add);
    }
    return branchSet;
  }

  private String buildRepoKey(String orgId, String projectId, String repoName, String connectorRef) {
    return StringUtils.defaultString(orgId) + "|" + StringUtils.defaultString(projectId) + "|"
        + StringUtils.defaultString(repoName) + "|" + StringUtils.defaultString(connectorRef);
  }
}

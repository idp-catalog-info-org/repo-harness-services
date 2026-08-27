/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.blockExecutionMetadata;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.manager.InterruptManager;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.BlockExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.SystemIssuer;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.blockexecution.BlockExecutionMetadataRepository;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
public class BlockExecutionMetadataServiceImpl implements BlockExecutionMetadataService {
  @Inject BlockExecutionMetadataRepository blockExecutionMetadataRepository;
  @Inject private InterruptManager interruptManager;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  public LoadingCache<String, Boolean> accountIdCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(3, TimeUnit.MINUTES).build(new CacheLoader<>() {
        @Override
        public Boolean load(@NotNull String accountId) throws IOException {
          return blockExecutionMetadataRepository.existsByAccountId(accountId);
        }
      });

  public boolean shouldBlock(
      String accountId, String orgId, String projectId, String pipelineId, String parentUniqueId) {
    try {
      if (!accountIdCache.get(accountId)) {
        return false;
      }
      List<BlockExecutionMetadata> blockExecutionMetadataList = blockExecutionMetadataRepository.findAll(accountId);
      Map<ScopeLevel, String> parentUniqueIdsMap =
          scopeResolutionHelper.getUniqueIdsIncludingParentScopes(ScopeInfo.builder()
                                                                      .accountIdentifier(accountId)
                                                                      .orgIdentifier(orgId)
                                                                      .projectIdentifier(projectId)
                                                                      .scopeType(ScopeLevel.PROJECT)
                                                                      .uniqueId(parentUniqueId)
                                                                      .build());
      for (BlockExecutionMetadata metadata : blockExecutionMetadataList) {
        String blockParentUniqueId = metadata.getParentUniqueId();
        String blockPipelineId = metadata.getPipelineId();
        // if blockPipelineId is blank, check if any higher or equal Scope block policy is present for the pipeline.
        if (EmptyPredicate.isEmpty(blockPipelineId) && parentUniqueIdsMap.containsValue(blockParentUniqueId)) {
          return true;
        }
        // if blockPipelineId is not blank, check if it's the exact pipeline being blocked. Match parentUniqueIds
        if (!EmptyPredicate.isEmpty(blockPipelineId) && blockPipelineId.equals(pipelineId)
            && blockParentUniqueId.equals(parentUniqueId)) {
          return true;
        }
      }
      return false;
    } catch (Exception ex) {
      log.error("Exception occurred while calculating block on execution", ex);
      return false;
    }
  }

  @Override
  public boolean validate(Ambiance ambiance) {
    if (shouldBlock(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getPipelineIdentifier(ambiance),
            AmbianceUtils.getParentUniqueIdentifier(ambiance))) {
      try {
        interruptManager.register(
            InterruptPackage.builder()
                .planExecutionId(ambiance.getPlanExecutionId())
                .interruptType(InterruptType.ABORT_ALL)
                .interruptConfig(
                    InterruptConfig.newBuilder()
                        .setIssuedBy(IssuedBy.newBuilder().setSystemIssuer(SystemIssuer.newBuilder().build()).build())
                        .build())
                .build());
      } catch (InvalidRequestException exception) {
        // do Nothing as execution should already be aborted.
      }
      log.error("Blocking the execution as it was requested by Harness Admin");
      return true;
    }
    return false;
  }

  @Override
  public BlockExecutionMetadata block(String accountId, String orgId, String projectId, String pipelineIdentifier) {
    Optional<ScopeInfo> scopeInfo = scopeResolutionHelper.getScopeInfoOptional(accountId, orgId, projectId);
    String parentUniqueId = null;
    if (scopeInfo.isPresent()) {
      parentUniqueId = scopeInfo.get().getUniqueId();
    }
    BlockExecutionMetadata blockExecutionMetadata =
        blockExecutionMetadataRepository.save(BlockExecutionMetadata.builder()
                                                  .accountId(accountId)
                                                  .orgId(orgId)
                                                  .projectId(projectId)
                                                  .parentUniqueId(parentUniqueId)
                                                  .uniqueId(generateUuid())
                                                  .pipelineId(pipelineIdentifier)
                                                  .build());
    accountIdCache.refresh(accountId);
    return blockExecutionMetadata;
  }

  @Override
  public boolean shouldAllowRun(
      String accountId, String orgId, String projectId, String pipelineId, ScopeInfo scopeInfo) {
    if (shouldBlock(accountId, orgId, projectId, pipelineId, scopeInfo != null ? scopeInfo.getUniqueId() : null)) {
      throw new InvalidRequestException("Execution for this pipeline is blocked by Harness. Please contact Harness "
          + "System Admin for more information.");
    }
    return false;
  }

  public DeleteResult unblock(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<ScopeInfo> scopeInfo =
        scopeResolutionHelper.getScopeInfoOptional(accountIdentifier, orgIdentifier, projectIdentifier);
    return blockExecutionMetadataRepository.delete(
        pipelineIdentifier, scopeInfo.map(ScopeInfo::getUniqueId).orElse(null));
  }
}

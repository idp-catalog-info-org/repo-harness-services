/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGDateUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent.PipelineValidationEventBuilder;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.handler.PipelineAsyncValidationHandler;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.repositories.pipeline.validation.async.PipelineValidationEventRepository;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineAsyncValidationServiceImpl implements PipelineAsyncValidationService {
  public static final int MAX_TIME_FOR_PIPELINE_VALIDATION = 15;
  private final PipelineValidationEventRepository pipelineValidationEventRepository;
  private final Executor executor;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineGovernanceService pipelineGovernanceService;
  private final PipelineRefreshService pipelineRefreshService;
  private final PipelineValidationService pipelineValidationService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final PipelineOpaStatusHandler pipelineOpaStatusHandler;

  @Inject
  public PipelineAsyncValidationServiceImpl(PipelineValidationEventRepository pipelineValidationEventRepository,
      @Named("PipelineAsyncValidationExecutorService") Executor executor,
      PMSPipelineTemplateHelper pipelineTemplateHelper, PipelineGovernanceService pipelineGovernanceService,
      PipelineRefreshService pipelineRefreshService, PipelineValidationService pipelineValidationService,
      PmsFeatureFlagService pmsFeatureFlagService, PipelineOpaStatusHandler pipelineOpaStatusHandler) {
    this.pipelineValidationEventRepository = pipelineValidationEventRepository;
    this.executor = executor;
    this.pipelineTemplateHelper = pipelineTemplateHelper;
    this.pipelineGovernanceService = pipelineGovernanceService;
    this.pipelineRefreshService = pipelineRefreshService;
    this.pipelineValidationService = pipelineValidationService;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.pipelineOpaStatusHandler = pipelineOpaStatusHandler;
  }

  @Override
  public PipelineValidationEvent startEvent(PipelineEntity entity, String branch, Action action, boolean loadFromCache,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String fqn = PipelineAsyncValidationHelper.buildFQN(entity, branch, isParentIdQueryingEnabled);
    ScmGitMetaData scm = GitAwareContextHelper.getScmGitMetaData();
    String commitId = scm != null ? scm.getCommitId() : null;
    PipelineValidationEvent pipelineValidationEvent =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.INITIATED)
            .fqn(fqn)
            .action(action)
            .params(ValidationParams.builder().pipelineEntity(entity).commitId(commitId).build())
            .result(ValidationResult.builder().build())
            .startTs(System.currentTimeMillis())
            .build();
    PipelineValidationEvent savedPipelineValidationEvent =
        pipelineValidationEventRepository.save(pipelineValidationEvent);

    executor.execute(new PipelineAsyncValidationHandler(savedPipelineValidationEvent, loadFromCache, scopeInfo,
        isParentIdQueryingEnabled, this, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler));
    return savedPipelineValidationEvent;
  }

  @Override
  public PipelineValidationEvent createRecordForSuccessfulSyncValidation(PipelineEntity pipelineEntity, String branch,
      GovernanceMetadata governanceMetadata, Action action, boolean isParentIdQueryingEnabled) {
    String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, branch, isParentIdQueryingEnabled);
    ScmGitMetaData scm = GitAwareContextHelper.getScmGitMetaData();
    String commitId = scm != null ? scm.getCommitId() : null;
    ValidationResult validationResult =
        ValidationResult.builder()
            .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
            .governanceMetadata(governanceMetadata)
            .build();
    validationResult = PipelineAsyncValidationHandler.captureOpaEvalFields(
        pipelineEntity, governanceMetadata, validationResult, commitId, pipelineOpaStatusHandler);
    PipelineValidationEventBuilder pipelineValidationEventBuilder =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.SUCCESS)
            .fqn(fqn)
            .action(action)
            .params(ValidationParams.builder().pipelineEntity(pipelineEntity).commitId(commitId).build())
            .result(validationResult)
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis());

    return pipelineValidationEventRepository.save(
        pipelineValidationEventBuilder.pipelineUniqueId(pipelineEntity.getUniqueId()).build());
  }

  @Override
  public PipelineValidationEvent updateEvent(String uuid, ValidationStatus status, ValidationResult result) {
    Criteria criteria = PipelineAsyncValidationHelper.getCriteriaForUpdate(uuid);
    Update updateOperations = PipelineAsyncValidationHelper.getUpdateOperations(status, result);
    return pipelineValidationEventRepository.update(criteria, updateOperations);
  }

  @Override
  public Optional<PipelineValidationEvent> getLatestEventByFQNAndAction(String fqn, Action action) {
    return pipelineValidationEventRepository.findLatestValidEvent(fqn, action);
  }

  @Override
  public Optional<PipelineValidationEvent> getEventByUuid(String uuid) {
    Optional<PipelineValidationEvent> eventByUuid = pipelineValidationEventRepository.findById(uuid);
    if (eventByUuid.isEmpty()) {
      throw new EntityNotFoundException("No Pipeline Validation Event found for uuid " + uuid);
    }
    PipelineValidationEvent pipelineValidationEvent = eventByUuid.get();

    Long currentTs = System.currentTimeMillis();
    if (!ValidationStatus.isFinalStatus(pipelineValidationEvent.getStatus())
        && NGDateUtils.getDiffOfTimeStampsInMinutes(currentTs, pipelineValidationEvent.getStartTs())
            > MAX_TIME_FOR_PIPELINE_VALIDATION) {
      try {
        return Optional.of(updateEvent(
            pipelineValidationEvent.getUuid(), ValidationStatus.TERMINATED, pipelineValidationEvent.getResult()));
      } catch (Exception ex) {
        log.error(
            String.format("Could terminate the PipelineValidationEvent with id: %s", pipelineValidationEvent.getUuid()),
            ex);
      }
    }
    return eventByUuid;
  }
}

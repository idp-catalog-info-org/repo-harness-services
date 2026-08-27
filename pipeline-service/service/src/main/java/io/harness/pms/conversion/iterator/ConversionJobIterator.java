/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.iterator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.EntityNotFoundException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.goconvert.EntityType;
import io.harness.goconvert.GoConvertServiceClient;
import io.harness.goconvert.proto.ConversionReport;
import io.harness.goconvert.proto.ConvertResponse;
import io.harness.goconvert.proto.ConverterMessage;
import io.harness.goconvert.proto.ExpressionEntry;
import io.harness.goconvert.proto.Severity;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.manage.GlobalContextManager;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.conversion.beans.ConversionActionType;
import io.harness.pms.conversion.beans.ConversionChecksum;
import io.harness.pms.conversion.beans.ConversionErrorDetail;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobEntity.ConversionJobEntityKeys;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionNodeSummary;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.pms.conversion.beans.EntityIdentifierDTO;
import io.harness.pms.conversion.beans.EntityMetadata;
import io.harness.pms.conversion.beans.ErrorSeverity;
import io.harness.pms.conversion.beans.PipelineConversionMetricsDTO;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.ExtractedReferences;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.PipelineChainReference;
import io.harness.pms.conversion.helper.YamlEntityReferenceExtractor.TemplateReference;
import io.harness.pms.conversion.service.ConversionJobService;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.conversion.ConversionChecksumRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitUpdateDetails;
import io.harness.spec.server.template.v1.model.TemplateCreateRequestBody;
import io.harness.spec.server.template.v1.model.TemplateUpdateRequestBody;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Iterator for processing V0 to V1 conversion jobs.
 *
 * Uses the Harness iterator framework (Redis batch mode) to poll for jobs
 * with nextIteration <= now and status in {QUEUED, IN_PROGRESS}.
 *
 * Processing flow:
 *   QUEUED jobs      → Phase 1: expand (fetch YAML, discover deps, create children, sleep)
 *   IN_PROGRESS jobs → Phase 2: check children, convert, finalize
 *
 * Distributed locking (per job UUID) prevents concurrent processing across pods.
 */
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class ConversionJobIterator extends IteratorLoopModeHandler implements Handler<ConversionJobEntity> {
  private static final Duration MIN_TARGET_INTERVAL = Duration.ofSeconds(30);
  private static final long[] RETRY_BACKOFF_MINUTES = {1, 5, 10};
  private static final long MAX_SLEEP_MILLIS = Duration.ofMinutes(30).toMillis();
  private static final String ACCOUNT_SCOPE_PREFIX = "account.";
  private static final String ORG_SCOPE_PREFIX = "org.";
  private static final int MAX_IDENTIFIER_LENGTH = 128;
  private static final int V1_SUFFIX_LENGTH = 5; // "_" + 4 hex chars
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PersistentLocker persistentLocker;
  @Inject private ConversionJobService conversionJobService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PMSPipelineService pmsPipelineService;
  @Inject private PMSInputSetService pmsInputSetService;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private GoConvertServiceClient goConvertServiceClient;
  @Inject private ConversionChecksumRepository conversionChecksumRepository;
  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private NGTriggerService ngTriggerService;
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ConversionJobIterator";
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    // Not used — we use Redis batch mode
    log.error("createAndStartIterator should not be called for ConversionJobIterator");
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    if (targetInterval.compareTo(MIN_TARGET_INTERVAL) < 0) {
      log.warn("[CONVERSION]: Target interval {}s is below minimum {}s, using minimum", targetInterval.getSeconds(),
          MIN_TARGET_INTERVAL.getSeconds());
      targetInterval = MIN_TARGET_INTERVAL;
    }
    iterator = (MongoPersistenceIterator<ConversionJobEntity, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       ConversionJobEntity.class,
                       MongoPersistenceIterator.<ConversionJobEntity, SpringFilterExpander>builder()
                           .clazz(ConversionJobEntity.class)
                           .fieldName(ConversionJobEntityKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(30))
                           .acceptableExecutionTime(ofSeconds(30))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .filterExpander(q
                               -> q.addCriteria(where(ConversionJobEntityKeys.status)
                                                    .in(ConversionStatus.QUEUED, ConversionStatus.IN_PROGRESS))));
  }

  @Override
  public void handle(ConversionJobEntity entity) {
    if (!pmsFeatureFlagService.isEnabled(entity.getAccountId(), FeatureName.PIPE_V0_TO_V1_CONVERSION)) {
      log.info("[CONVERSION]: Feature flag disabled for account {}, skipping job {}", entity.getAccountId(),
          entity.getUuid());
      return;
    }

    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      Principal principal = entity.getTriggerPrincipal() != null
          ? entity.getTriggerPrincipal()
          : new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId());
      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      SecurityContextBuilder.setContext(principal);

      String lockKey = buildConversionEntityLockKey(entity);
      try (AcquiredLock<?> lock =
               persistentLocker.waitToAcquireLockOptional(lockKey, Duration.ofMinutes(5), Duration.ofSeconds(10))) {
        if (lock == null) {
          log.warn("[CONVERSION]: Failed to acquire entity lock {} for job {}, will retry", lockKey, entity.getUuid());
          return;
        }

        log.info("[CONVERSION]: Processing job {}, actionType={}, entityType={}, status={}, expanded={}",
            entity.getUuid(), entity.getActionType(), entity.getEntityType(), entity.getStatus(), entity.getExpanded());

        try {
          switch (entity.getStatus()) {
            case QUEUED:
              handleQueued(entity);
              break;
            case IN_PROGRESS:
              handleInProgress(entity);
              break;
            default:
              log.warn("[CONVERSION]: Unexpected status {} for job {}, skipping", entity.getStatus(), entity.getUuid());
              break;
          }
        } catch (Exception ex) {
          log.error("[CONVERSION]: Error processing job {}", entity.getUuid(), ex);
          handleJobError(entity, ex);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Status routing
  // ---------------------------------------------------------------------------

  /**
   * Phase 1: QUEUED → IN_PROGRESS.
   * Mark in-progress, then route to action-type-specific expansion logic.
   */
  private void handleQueued(ConversionJobEntity entity) {
    ConversionJobMetricsDTO initialMetrics = entity.getConversionMetrics();
    if (entity.getActionType() == ConversionActionType.SINGLE
        && (initialMetrics == null || initialMetrics.getTotalEntities() == 0)) {
      initialMetrics = buildMetrics(1, 0, 0, 0, 0, 0);
    }
    conversionJobService.updateJobStatus(entity.getUuid(), ConversionStatus.IN_PROGRESS, initialMetrics);

    switch (entity.getActionType()) {
      case SINGLE:
        handleSinglePhase1(entity);
        break;
      case BATCH:
        handleBatchExpand(entity);
        break;
      case PROJECT:
        handleProjectExpand(entity);
        break;
      default:
        throw new IllegalStateException("Unknown action type: " + entity.getActionType());
    }
  }

  /**
   * Phase 2: IN_PROGRESS — check expansion / children / convert.
   */
  private void handleInProgress(ConversionJobEntity entity) {
    switch (entity.getActionType()) {
      case SINGLE:
        handleSinglePhase2(entity);
        break;
      case BATCH:
      case PROJECT:
        handleAggregatorCheck(entity);
        break;
      default:
        throw new IllegalStateException("Unknown action type: " + entity.getActionType());
    }
  }

  // ---------------------------------------------------------------------------
  // SINGLE — two-phase processing
  // ---------------------------------------------------------------------------

  /**
   * SINGLE Phase 1: fetch YAML, discover dependencies (templates + pipeline chains only),
   * create child jobs, sleep or proceed.
   *
   * Input set children are NOT created here — they are created after self-conversion
   * in Phase 2, because input sets should only convert after the pipeline is converted.
   */
  private void handleSinglePhase1(ConversionJobEntity entity) {
    log.info("[CONVERSION]: SINGLE Phase 1 for job {}, entityType={}", entity.getUuid(), entity.getEntityType());

    // Step 1: Fetch the entity YAML and metadata
    EntityMetadata metadata = fetchEntityMetadata(entity);
    if (metadata == null || metadata.getYaml() == null) {
      String errorMsg = metadata == null
          ? String.format(
                "Entity %s type of %s not found or inaccessible", entity.getEntityIdentifier(), entity.getEntityType())
          : String.format("YAML is null for entity %s of type=%s storeType %s. For remote entities, ensure the branch "
                    + "is specified or the default branch is accessible.",
                entity.getEntityIdentifier(), entity.getEntityType(), metadata.getStoreType());
      log.error("[CONVERSION]: Job {} — {}", entity.getUuid(), errorMsg);
      Update errorUpdate = new Update().set(ConversionJobEntityKeys.errorMessage, errorMsg);
      updateJobFields(entity.getUuid(), errorUpdate);
      markJobComplete(entity, ConversionStatus.FAILED, buildMetrics(1, 1, 0, 0, 1, 100));
      return;
    }

    // Step 1b: Skip if entity is already V1
    if (HarnessYamlVersion.V1.equals(metadata.getHarnessVersion())) {
      log.info(
          "[CONVERSION]: Job {} — entity {} is already V1, skipping", entity.getUuid(), entity.getEntityIdentifier());
      markJobComplete(entity, ConversionStatus.SKIPPED, buildMetrics(1, 1, 0, 1, 0, 100));
      return;
    }

    // Step 2: Cache entityMetadata early so routing fields (storeType, repo, branch) survive
    // even if the job is SKIPPED below — parent's lookupChecksum needs these fields.
    Update cacheUpdate =
        new Update().set(ConversionJobEntityKeys.entityMetadata, metadata).set(ConversionJobEntityKeys.expanded, true);
    updateJobFields(entity.getUuid(), cacheUpdate);
    entity.setEntityMetadata(metadata);

    String v0Yaml = metadata.getYaml();

    // Step 3: Skip detection — bypass when forceReconvert is set
    Optional<ConversionChecksum> storedChecksum = lookupChecksum(entity);
    if (!Boolean.TRUE.equals(entity.getForceReconvert()) && storedChecksum.isPresent()) {
      String currentChecksum = goConvertServiceClient.getChecksum(v0Yaml);
      if (currentChecksum.equals(storedChecksum.get().getChecksum())) {
        log.info(
            "[CONVERSION]: Job {} — checksum unchanged ({}), skipping conversion", entity.getUuid(), currentChecksum);
        markJobComplete(entity, ConversionStatus.SKIPPED, buildMetrics(1, 1, 0, 1, 0, 100));
        return;
      }
    }

    // Step 4: Discover dependencies (templates + pipeline chains) — NOT input sets
    List<ConversionJobEntity> childJobs = new ArrayList<>();
    if (entity.getEntityType() == EntityType.PIPELINE || entity.getEntityType() == EntityType.TEMPLATE) {
      ExtractedReferences refs = YamlEntityReferenceExtractor.extractReferences(v0Yaml);
      childJobs.addAll(createChildJobsFromTemplateRefs(entity, refs.getTemplateReferences()));
      childJobs.addAll(createChildJobsFromPipelineChainRefs(entity, refs.getPipelineChainReferences()));
    }

    // Step 5: Save children and update parent
    for (ConversionJobEntity child : childJobs) {
      conversionJobService.createJob(child);
    }

    Update childCountUpdate = new Update().set(ConversionJobEntityKeys.totalChildJobs, childJobs.size());
    updateJobFields(entity.getUuid(), childCountUpdate);

    if (childJobs.isEmpty()) {
      // No dependencies — schedule Phase 2 immediately (iterator re-reads entity from DB)
      wakeJob(entity.getUuid());
      log.info(
          "[CONVERSION]: SINGLE Phase 1 complete for job {}, no children, proceeding to Phase 2", entity.getUuid());
    } else {
      // Has children — sleep until children wake us
      sleepJob(entity.getUuid(), "created " + childJobs.size() + " child jobs");
    }
  }

  /**
   * SINGLE Phase 2: convert this entity, then handle input sets for pipelines.
   *
   * Two sub-phases controlled by yamlConverted flag:
   *   yamlConverted=false → wait for template children, convert self, create input set children
   *   yamlConverted=true  → wait for input set children, finalize
   */
  private void handleSinglePhase2(ConversionJobEntity entity) {
    log.info("[CONVERSION]: SINGLE Phase 2 for job {}, yamlConverted={}", entity.getUuid(), entity.getYamlConverted());

    if (Boolean.FALSE.equals(entity.getExpanded())) {
      log.warn("[CONVERSION]: Job {} is IN_PROGRESS but not expanded, re-running Phase 1", entity.getUuid());
      handleSinglePhase1(entity);
      return;
    }

    if (Boolean.TRUE.equals(entity.getYamlConverted())) {
      // Sub-phase 2b: self already converted, waiting for input set children
      handleInputSetChildrenCheck(entity);
    } else {
      // Sub-phase 2a: convert self first
      handleYamlConversion(entity);
    }
  }

  /**
   * Phase 2a: wait for template/chain children → convert self → create input set children.
   * Entity-scoped lock is already held by the handle() method.
   */
  private void handleYamlConversion(ConversionJobEntity entity) {
    // Step 1: Check if all template/chain children are done
    List<ConversionJobEntity> children = conversionJobService.getChildJobs(entity.getUuid());
    if (!allChildrenDone(entity, children)) {
      return; // sleeping or marked FAILED
    }

    EntityMetadata metadata = entity.getEntityMetadata();
    if (metadata == null || metadata.getYaml() == null) {
      String errorMsg = "No cached entityMetadata/YAML available in Phase 2 for entity " + entity.getEntityIdentifier()
          + " type=" + entity.getEntityType();
      log.error("[CONVERSION]: Job {} — {}", entity.getUuid(), errorMsg);
      Update errorUpdate = new Update().set(ConversionJobEntityKeys.errorMessage, errorMsg);
      updateJobFields(entity.getUuid(), errorUpdate);
      markJobComplete(entity, ConversionStatus.FAILED, buildMetrics(1, 1, 0, 0, 1, 100));
      return;
    }
    String v0Yaml = metadata.getYaml();

    // Skip overlay input sets — no YAML conversion needed, save directly with updated pipelineIdentifier
    if (entity.getEntityType() == EntityType.INPUT_SET
        && InputSetEntityType.OVERLAY_INPUT_SET.name().equals(metadata.getInputSetEntityType())) {
      String v1Identifier = entity.getEntityIdentifier();
      saveInputSetV1Yaml(entity, v0Yaml, v1Identifier, false);
      upsertConversionChecksum(entity, goConvertServiceClient.getChecksum(v0Yaml), v1Identifier);
      log.info("[CONVERSION]: Job {} — overlay input set saved directly (no YAML conversion)", entity.getUuid());
      markJobComplete(entity, ConversionStatus.SUCCESS, buildMetrics(1, 1, 1, 0, 0, 100));
      return;
    }

    // Step 2: Double-check checksum — skip if already converted (lock held at handle level)
    if (!Boolean.TRUE.equals(entity.getForceReconvert())) {
      Optional<ConversionChecksum> lockedChecksum = lookupChecksum(entity);
      if (lockedChecksum.isPresent()) {
        String currentChecksum = goConvertServiceClient.getChecksum(v0Yaml);
        if (currentChecksum.equals(lockedChecksum.get().getChecksum())) {
          String existingV1Id = lockedChecksum.get().getV1Identifier();
          Update skipUpdate = new Update().set(ConversionJobEntityKeys.v1Identifier, existingV1Id);
          updateJobFields(entity.getUuid(), skipUpdate);
          entity.setV1Identifier(existingV1Id);
          log.info(
              "[CONVERSION]: Job {} — entity already converted (v1Id={}), skipping", entity.getUuid(), existingV1Id);
          markJobComplete(entity, ConversionStatus.SKIPPED, buildMetrics(1, 1, 0, 1, 0, 100));
          return;
        }
      }
    }

    // Step 3: Convert — entity lock held at handle level, safe from duplicate conversions
    executeConversion(entity, children, v0Yaml, metadata);
  }

  /**
   * Builds a distributed lock key scoped by the entity's resolved account/org/project/identifier.
   * For TEMPLATE entities, the lock does NOT include versionLabel — all versions of the same
   * template share a single lock so they serialize and naturally share the same V1 identifier.
   */
  private String buildConversionEntityLockKey(ConversionJobEntity entity) {
    if (entity.getActionType() != ConversionActionType.SINGLE) {
      return String.format("ConversionJob-%s", entity.getUuid());
    }

    String identifier = entity.getEntityIdentifier();
    String orgId = entity.getOrgId();
    String projectId = entity.getProjectId();

    if (entity.getEntityType() == EntityType.TEMPLATE) {
      String[] scope = resolveTemplateScope(identifier, orgId, projectId);
      identifier = scope[0];
      orgId = scope[1];
      projectId = scope[2];
    }

    return String.format("ConversionEntity-%s/%s/%s/%s/%s", entity.getAccountId(), orgId != null ? orgId : "",
        projectId != null ? projectId : "", entity.getEntityType(), identifier);
  }

  private void executeConversion(
      ConversionJobEntity entity, List<ConversionJobEntity> children, String v0Yaml, EntityMetadata metadata) {
    String v1Identifier = resolveV1Identifier(entity);

    Map<String, String> templateRefMapping = buildTemplateRefMapping(children);
    Map<String, String> pipelineRefMapping = buildPipelineRefMapping(children);

    if (entity.getEntityType() == EntityType.PIPELINE) {
      pipelineRefMapping.put(entity.getEntityIdentifier(), v1Identifier);
    }

    if (entity.getEntityType() == EntityType.TRIGGER && entity.getPipelineIdentifier() != null
        && entity.getV1Identifier() != null) {
      pipelineRefMapping.put(entity.getPipelineIdentifier(), entity.getV1Identifier());
    }

    log.info("[CONVERSION]: Job {} — calling go-convert for entityType={}, templateRefs={}, pipelineRefs={}",
        entity.getUuid(), entity.getEntityType(), templateRefMapping.size(), pipelineRefMapping.size());

    ConvertResponse convertResponse = goConvertServiceClient.convert(
        entity.getEntityType(), v0Yaml, templateRefMapping, pipelineRefMapping, metadata.getContextPipelineYaml());

    String v1Yaml = convertResponse.getYaml();
    if (v1Yaml == null || v1Yaml.isEmpty()) {
      String errorMsg = "go-convert returned empty YAML for entity " + entity.getEntityIdentifier()
          + " type=" + entity.getEntityType();
      log.error("[CONVERSION]: Job {} — {}", entity.getUuid(), errorMsg);
      Update errorUpdate = new Update().set(ConversionJobEntityKeys.errorMessage, errorMsg);
      updateJobFields(entity.getUuid(), errorUpdate);
      markJobComplete(entity, ConversionStatus.FAILED, buildMetrics(1, 1, 0, 0, 1, 100));
      return;
    }

    List<ConversionErrorDetail> conversionErrors = parseConversionReport(convertResponse, entity);
    if (!conversionErrors.isEmpty()) {
      updateJobFields(entity.getUuid(), new Update().set(ConversionJobEntityKeys.conversionErrors, conversionErrors));
      entity.setConversionErrors(conversionErrors);
    }

    saveV1Yaml(entity, v1Yaml, v1Identifier);
    upsertConversionChecksum(entity, convertResponse.getChecksum(), v1Identifier);
    log.info("[CONVERSION]: Job {} — V0→V1 conversion successful, checksum={}", entity.getUuid(),
        convertResponse.getChecksum());

    Update yamlConvertedUpdate = new Update()
                                     .set(ConversionJobEntityKeys.yamlConverted, true)
                                     .set(ConversionJobEntityKeys.v0YamlChecksum, convertResponse.getChecksum())
                                     .set(ConversionJobEntityKeys.v1Identifier, v1Identifier);
    updateJobFields(entity.getUuid(), yamlConvertedUpdate);
    entity.setV1Identifier(v1Identifier);

    if (entity.getEntityType() == EntityType.PIPELINE) {
      List<ConversionJobEntity> inputSetChildren = createChildJobsForInputSets(entity, v1Identifier, v0Yaml);
      List<ConversionJobEntity> triggerChildren = createChildJobsForTriggers(entity, v1Identifier, v0Yaml);

      List<ConversionJobEntity> postChildren = new ArrayList<>();
      postChildren.addAll(inputSetChildren);
      postChildren.addAll(triggerChildren);

      for (ConversionJobEntity child : postChildren) {
        conversionJobService.createJob(child);
      }

      if (!postChildren.isEmpty()) {
        sleepJob(entity.getUuid(),
            "yaml converted, created " + postChildren.size() + " post-conversion children (IS + triggers)");
        return;
      }
    }

    markJobComplete(entity, ConversionStatus.SUCCESS, buildMetrics(1, 1, 1, 0, 0, 100));
    log.info("[CONVERSION]: SINGLE Phase 2 complete for job {}", entity.getUuid());
  }

  /**
   * Phase 2b: pipeline already converted, wait for post-conversion children (input sets + triggers) to finish.
   */
  private void handleInputSetChildrenCheck(ConversionJobEntity entity) {
    List<ConversionJobEntity> children = conversionJobService.getChildJobs(entity.getUuid());

    // Filter to post-conversion children (input sets + triggers)
    List<ConversionJobEntity> postChildren =
        children.stream()
            .filter(c -> c.getEntityType() == EntityType.INPUT_SET || c.getEntityType() == EntityType.TRIGGER)
            .collect(Collectors.toList());

    if (postChildren.isEmpty()) {
      markJobComplete(entity, ConversionStatus.SUCCESS, buildMetrics(1, 1, 1, 0, 0, 100));
      return;
    }

    long stillRunning = postChildren.stream().filter(c -> !ConversionStatus.isFinalStatus(c.getStatus())).count();
    if (stillRunning > 0) {
      long doneCount = postChildren.size() - stillRunning;
      int progress = postChildren.size() > 0 ? (int) ((doneCount * 100) / postChildren.size()) : 0;

      Update progressUpdate =
          new Update().set(ConversionJobEntityKeys.conversionMetrics, buildMetrics(1, 0, 0, 0, 0, progress));
      updateJobFields(entity.getUuid(), progressUpdate);
      sleepJob(entity.getUuid(), stillRunning + " post-conversion children still running");
      return;
    }

    long failedCount = postChildren.stream().filter(c -> c.getStatus() == ConversionStatus.FAILED).count();
    ConversionStatus finalStatus = failedCount > 0 ? ConversionStatus.PARTIAL_SUCCESS : ConversionStatus.SUCCESS;
    ConversionJobMetricsDTO finalMetrics = buildMetrics(1, 1, failedCount > 0 ? 0 : 1, 0, failedCount > 0 ? 1 : 0, 100);

    markJobComplete(entity, finalStatus, finalMetrics);
    log.info("[CONVERSION]: SINGLE Phase 2 complete for job {}, failedChildren={}", entity.getUuid(), failedCount);
  }

  /**
   * Check if all children are in final status. If not, sleep or mark FAILED.
   * Returns true if all children are done and none failed.
   */
  private boolean allChildrenDone(ConversionJobEntity entity, List<ConversionJobEntity> children) {
    if (children.isEmpty()) {
      return true;
    }

    long stillRunning = children.stream().filter(c -> !ConversionStatus.isFinalStatus(c.getStatus())).count();
    long failedCount = children.stream().filter(c -> c.getStatus() == ConversionStatus.FAILED).count();

    if (stillRunning > 0) {
      long doneCount = children.size() - stillRunning;
      int progress = (int) ((doneCount * 100) / children.size());
      updateJobFields(entity.getUuid(),
          new Update().set(ConversionJobEntityKeys.conversionMetrics, buildMetrics(1, 0, 0, 0, 0, progress)));
      sleepJob(entity.getUuid(), stillRunning + " children still running");
      return false;
    }
    if (failedCount > 0) {
      String errorMsg = failedCount + " child job(s) failed for entity " + entity.getEntityIdentifier()
          + " type=" + entity.getEntityType();
      log.warn("[CONVERSION]: Job {} — {}", entity.getUuid(), errorMsg);
      Update errorUpdate = new Update().set(ConversionJobEntityKeys.errorMessage, errorMsg);
      updateJobFields(entity.getUuid(), errorUpdate);
      markJobComplete(entity, ConversionStatus.FAILED, buildMetrics(1, 1, 0, 0, 1, 100));
      return false;
    }

    return true;
  }

  // ---------------------------------------------------------------------------
  // BATCH / PROJECT — expand then aggregate
  // ---------------------------------------------------------------------------

  /**
   * BATCH expansion: create SINGLE child jobs for each entityReference.
   */
  private void handleBatchExpand(ConversionJobEntity entity) {
    log.info("[CONVERSION]: BATCH expand for job {}", entity.getUuid());

    List<EntityIdentifierDTO> refs = entity.getEntityReferences();
    if (refs == null || refs.isEmpty()) {
      log.warn("[CONVERSION]: BATCH job {} has no entity references, marking SUCCESS", entity.getUuid());
      markJobComplete(entity, ConversionStatus.SUCCESS, buildMetrics(0, 0, 0, 0, 0, 100));
      return;
    }

    int childCount = 0;
    String rootId = resolveRootJobId(entity);
    for (EntityIdentifierDTO ref : refs) {
      ConversionJobEntity child = ConversionJobEntity.builder()
                                      .status(ConversionStatus.QUEUED)
                                      .accountId(entity.getAccountId())
                                      .orgId(entity.getOrgId())
                                      .projectId(entity.getProjectId())
                                      .actionType(ConversionActionType.SINGLE)
                                      .entityType(ref.getEntityType())
                                      .entityIdentifier(ref.getEntityId())
                                      .entityReference(ref)
                                      .forceReconvert(entity.getForceReconvert())
                                      .triggerPrincipal(entity.getTriggerPrincipal())
                                      .parentJobId(entity.getUuid())
                                      .rootJobId(rootId)
                                      .depth(entity.getDepth() + 1)
                                      .nextIteration(System.currentTimeMillis())
                                      .createdAt(System.currentTimeMillis())
                                      .build();
      conversionJobService.createJob(child);
      childCount++;
    }

    Update update = new Update()
                        .set(ConversionJobEntityKeys.expanded, true)
                        .set(ConversionJobEntityKeys.totalChildJobs, childCount)
                        .set(ConversionJobEntityKeys.conversionMetrics, buildMetrics(childCount, 0, 0, 0, 0, 0))
                        .set(ConversionJobEntityKeys.nextIteration, System.currentTimeMillis() + MAX_SLEEP_MILLIS);
    updateJobFields(entity.getUuid(), update);

    log.info("[CONVERSION]: BATCH expand complete for job {}, created {} child jobs", entity.getUuid(), childCount);
  }

  /**
   * PROJECT expansion: query all pipeline identifiers in project atomically, create SINGLE child jobs.
   * Uses listAllIdentifiers (projection query) instead of paginated list to avoid
   * pagination drift if pipelines are added/removed during expansion.
   */
  private void handleProjectExpand(ConversionJobEntity entity) {
    log.info("[CONVERSION]: PROJECT expand for job {}", entity.getUuid());

    // Key on parentUniqueId (movement-stable scope id); pipeline docs' orgId/projectId strings can be stale after a
    // project move, so raw scope-string matching would discover zero pipelines.
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
    Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                            .is(entity.getAccountId())
                            .and(PipelineEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(PipelineEntityKeys.deleted)
                            .is(false);

    List<String> pipelineIdentifiers = pmsPipelineService.listAllIdentifiers(criteria);

    int childCount = 0;
    String rootId = resolveRootJobId(entity);
    for (String pipelineId : pipelineIdentifiers) {
      EntityIdentifierDTO entityRef =
          EntityIdentifierDTO.builder().entityId(pipelineId).entityType(EntityType.PIPELINE).build();
      ConversionJobEntity child = ConversionJobEntity.builder()
                                      .status(ConversionStatus.QUEUED)
                                      .accountId(entity.getAccountId())
                                      .orgId(entity.getOrgId())
                                      .projectId(entity.getProjectId())
                                      .actionType(ConversionActionType.SINGLE)
                                      .entityType(EntityType.PIPELINE)
                                      .entityIdentifier(pipelineId)
                                      .entityReference(entityRef)
                                      .forceReconvert(entity.getForceReconvert())
                                      .triggerPrincipal(entity.getTriggerPrincipal())
                                      .parentJobId(entity.getUuid())
                                      .rootJobId(rootId)
                                      .depth(entity.getDepth() + 1)
                                      .nextIteration(System.currentTimeMillis())
                                      .createdAt(System.currentTimeMillis())
                                      .build();
      conversionJobService.createJob(child);
      childCount++;
    }

    Update update = new Update()
                        .set(ConversionJobEntityKeys.expanded, true)
                        .set(ConversionJobEntityKeys.totalChildJobs, childCount)
                        .set(ConversionJobEntityKeys.conversionMetrics, buildMetrics(childCount, 0, 0, 0, 0, 0))
                        .set(ConversionJobEntityKeys.nextIteration, System.currentTimeMillis() + MAX_SLEEP_MILLIS);
    updateJobFields(entity.getUuid(), update);

    log.info("[CONVERSION]: PROJECT expand complete for job {}, created {} child jobs from pipelines", entity.getUuid(),
        childCount);
  }

  /**
   * Aggregator check for BATCH/PROJECT: verify all children are done, compute final status.
   */
  private void handleAggregatorCheck(ConversionJobEntity entity) {
    log.info("[CONVERSION]: Aggregator check for {} job {}", entity.getActionType(), entity.getUuid());

    if (Boolean.FALSE.equals(entity.getExpanded())) {
      log.warn("[CONVERSION]: {} job {} is IN_PROGRESS but not expanded, re-expanding", entity.getActionType(),
          entity.getUuid());
      if (entity.getActionType() == ConversionActionType.BATCH) {
        handleBatchExpand(entity);
      } else {
        handleProjectExpand(entity);
      }
      return;
    }

    List<ConversionJobEntity> children = conversionJobService.getChildJobs(entity.getUuid());

    long totalChildren = children.size();
    long successCount = children.stream().filter(c -> c.getStatus() == ConversionStatus.SUCCESS).count();
    long partialCount = children.stream().filter(c -> c.getStatus() == ConversionStatus.PARTIAL_SUCCESS).count();
    long failedCount = children.stream().filter(c -> c.getStatus() == ConversionStatus.FAILED).count();
    long skippedCount = children.stream().filter(c -> c.getStatus() == ConversionStatus.SKIPPED).count();
    long stillRunning = totalChildren - successCount - partialCount - failedCount - skippedCount;
    long convertedCount = successCount + partialCount;

    if (stillRunning > 0) {
      long doneCount = totalChildren - stillRunning;
      int progress = totalChildren > 0 ? (int) ((doneCount * 100) / totalChildren) : 0;
      updateJobFields(entity.getUuid(),
          new Update().set(ConversionJobEntityKeys.conversionMetrics,
              buildMetrics(totalChildren, doneCount, convertedCount, skippedCount, failedCount, progress)));
      sleepJob(entity.getUuid(), stillRunning + " children still running");
      return;
    }

    // All children in final status — compute aggregate
    long processedCount = totalChildren;
    int progressPercentage = totalChildren > 0 ? 100 : 0;

    ConversionStatus finalStatus;
    if (failedCount == 0) {
      finalStatus = ConversionStatus.SUCCESS;
    } else if (convertedCount == 0 && skippedCount == 0) {
      finalStatus = ConversionStatus.FAILED;
    } else {
      finalStatus = ConversionStatus.PARTIAL_SUCCESS;
    }

    if (finalStatus == ConversionStatus.FAILED || finalStatus == ConversionStatus.PARTIAL_SUCCESS) {
      String errorMsg = failedCount + "/" + totalChildren + " child job(s) failed for " + entity.getActionType()
          + " job " + entity.getUuid();
      Update errorUpdate = new Update().set(ConversionJobEntityKeys.errorMessage, errorMsg);
      updateJobFields(entity.getUuid(), errorUpdate);
    }

    ConversionJobMetricsDTO metrics =
        buildMetrics(totalChildren, processedCount, convertedCount, skippedCount, failedCount, progressPercentage);

    markJobComplete(entity, finalStatus, metrics);
    log.info("[CONVERSION]: {} job {} aggregation complete — status={}, success={}, failed={}, skipped={}",
        entity.getActionType(), entity.getUuid(), finalStatus, successCount, failedCount, skippedCount);
  }

  // ---------------------------------------------------------------------------
  // YAML fetching & child job creation
  // ---------------------------------------------------------------------------

  /**
   * Fetch the V0 entity and build EntityMetadata (yaml + name + versionLabel).
   */
  private EntityMetadata fetchEntityMetadata(ConversionJobEntity entity) {
    try {
      switch (entity.getEntityType()) {
        case PIPELINE:
          return fetchPipelineMetadata(entity);
        case TEMPLATE:
          return fetchTemplateMetadata(entity);
        case INPUT_SET:
          return fetchInputSetMetadata(entity);
        case TRIGGER:
          return entity.getEntityMetadata();
        default:
          log.error("[CONVERSION]: Unknown entity type {} for job {}", entity.getEntityType(), entity.getUuid());
          return null;
      }
    } catch (Exception ex) {
      log.error("[CONVERSION]: Failed to fetch metadata for job {}", entity.getUuid(), ex);
      return null;
    }
  }

  private EntityMetadata fetchPipelineMetadata(ConversionJobEntity entity) {
    // Populate git context so getPipeline fetches from the correct branch for remote entities
    EntityIdentifierDTO ref = entity.getEntityReference();
    if (ref != null && ref.getBranch() != null) {
      GitAwareContextHelper.populateGitDetails(
          GitEntityInfo.builder().branch(ref.getBranch()).storeType(StoreType.REMOTE).build());
    }
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
    Optional<PipelineEntity> pipelineOpt = pmsPipelineService.getPipeline(entity.getAccountId(), entity.getOrgId(),
        entity.getProjectId(), entity.getEntityIdentifier(), false, false, false, false, scopeInfo, true);
    if (pipelineOpt.isEmpty()) {
      log.warn("[CONVERSION]: Pipeline {} not found for job {}", entity.getEntityIdentifier(), entity.getUuid());
      return null;
    }
    PipelineEntity pipeline = pipelineOpt.get();
    String branch = pipeline.getBranch();
    if (branch == null && pipeline.getStoreType() == StoreType.REMOTE) {
      branch = GitAwareContextHelper.getScmGitMetaData().getBranchName();
    }
    return EntityMetadata.builder()
        .yaml(pipeline.getYaml())
        .name(pipeline.getName())
        .harnessVersion(pipeline.getHarnessVersion())
        .connectorRef(pipeline.getConnectorRef())
        .repo(pipeline.getRepo())
        .filePath(pipeline.getFilePath())
        .branch(branch)
        .storeType(pipeline.getStoreType())
        .build();
  }

  private EntityMetadata fetchTemplateMetadata(ConversionJobEntity entity) {
    String templateIdentifier = entity.getEntityIdentifier();
    String[] scope = resolveTemplateScope(templateIdentifier, entity.getOrgId(), entity.getProjectId());
    String resolvedIdentifier = scope[0];
    String orgId = scope[1];
    String projectId = scope[2];

    // Use versionLabel from entityReference if available
    String versionLabel = null;
    String gitBranch = null;
    if (entity.getEntityReference() != null) {
      versionLabel = entity.getEntityReference().getVersionLabel();
      gitBranch = entity.getEntityReference().getBranch();
    }

    TemplateResponseDTO response =
        NGRestUtils.getResponseWithErrorDetails(templateResourceClient.get(resolvedIdentifier, entity.getAccountId(),
            orgId, projectId, null, versionLabel, false, gitBranch, null, "false"));

    if (response == null) {
      log.warn("[CONVERSION]: Template {} not found for job {}", templateIdentifier, entity.getUuid());
      return null;
    }
    EntityMetadata.EntityMetadataBuilder metadataBuilder = EntityMetadata.builder()
                                                               .yaml(response.getYaml())
                                                               .name(response.getName())
                                                               .versionLabel(response.getVersionLabel())
                                                               .stableVersion(response.isStableTemplate())
                                                               .harnessVersion(response.getYamlVersion())
                                                               .connectorRef(response.getConnectorRef())
                                                               .storeType(response.getStoreType());
    if (response.getGitDetails() != null) {
      metadataBuilder.repo(response.getGitDetails().getRepoName())
          .filePath(response.getGitDetails().getFilePath())
          .branch(response.getGitDetails().getBranch());
    }
    return metadataBuilder.build();
  }

  private EntityMetadata fetchInputSetMetadata(ConversionJobEntity entity) {
    String contextYaml =
        entity.getEntityMetadata() != null ? entity.getEntityMetadata().getContextPipelineYaml() : null;

    // For REMOTE input sets, populate Git context so the service fetches YAML from Git
    EntityIdentifierDTO ref = entity.getEntityReference();
    if (ref != null && ref.getBranch() != null) {
      GitAwareContextHelper.populateGitDetails(
          GitEntityInfo.builder().branch(ref.getBranch()).storeType(StoreType.REMOTE).build());
    }

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());

    // isParentIdQueryingEnabled=true so the fetch keys on parentUniqueId (movement-stable), matching how the
    // pipeline is fetched. The input set doc's orgId/projectId strings can be stale after a project move.
    Optional<InputSetEntity> inputSetOpt = pmsInputSetService.getWithoutValidations(
        scopeInfo, entity.getPipelineIdentifier(), entity.getEntityIdentifier(), false, false, false, true);
    if (inputSetOpt.isEmpty()) {
      log.warn("[CONVERSION]: InputSet {} not found for job {}", entity.getEntityIdentifier(), entity.getUuid());
      return null;
    }
    InputSetEntity inputSet = inputSetOpt.get();
    return EntityMetadata.builder()
        .yaml(inputSet.getYaml())
        .name(inputSet.getName())
        .harnessVersion(inputSet.getHarnessVersion())
        .inputSetEntityType(inputSet.getInputSetEntityType() != null ? inputSet.getInputSetEntityType().name() : null)
        .contextPipelineYaml(contextYaml)
        .connectorRef(inputSet.getConnectorRef())
        .repo(inputSet.getRepo())
        .filePath(inputSet.getFilePath())
        .branch(inputSet.getBranch())
        .storeType(inputSet.getStoreType())
        .build();
  }

  // ---------------------------------------------------------------------------
  // V1 YAML save & template ref mapping
  // ---------------------------------------------------------------------------

  /**
   * Build a templateRef mapping from ConversionChecksum records for successfully
   * converted TEMPLATE child jobs.
   * Maps V0 templateRef identifier → V1 identifier (from checksum table).
   */
  private Map<String, String> buildTemplateRefMapping(List<ConversionJobEntity> children) {
    Map<String, String> mapping = new HashMap<>();
    for (ConversionJobEntity child : children) {
      if (child.getEntityType() != EntityType.TEMPLATE
          || (child.getStatus() != ConversionStatus.SUCCESS && child.getStatus() != ConversionStatus.SKIPPED)) {
        continue;
      }
      try {
        Optional<ConversionChecksum> checksumOpt = lookupChecksum(child);
        if (checksumOpt.isPresent()) {
          mapping.put(child.getEntityIdentifier(), checksumOpt.get().getV1Identifier());
        } else {
          mapping.put(child.getEntityIdentifier(), child.getEntityIdentifier());
        }
      } catch (Exception ex) {
        log.warn("[CONVERSION]: Failed to look up checksum for template child {}, using identity mapping",
            child.getEntityIdentifier(), ex);
        mapping.put(child.getEntityIdentifier(), child.getEntityIdentifier());
      }
    }
    return mapping;
  }

  /**
   * Build a pipeline ref mapping from ConversionChecksum records for successfully
   * converted PIPELINE child jobs (chained pipelines).
   * Maps V0 pipelineId → "org/project/v1PipelineId" format.
   */
  private Map<String, String> buildPipelineRefMapping(List<ConversionJobEntity> children) {
    Map<String, String> mapping = new HashMap<>();
    for (ConversionJobEntity child : children) {
      if (child.getEntityType() != EntityType.PIPELINE
          || (child.getStatus() != ConversionStatus.SUCCESS && child.getStatus() != ConversionStatus.SKIPPED)) {
        continue;
      }
      try {
        Optional<ConversionChecksum> checksumOpt = lookupChecksum(child);
        String v1Id = checksumOpt.isPresent() ? checksumOpt.get().getV1Identifier() : child.getEntityIdentifier();
        String orgId = child.getOrgId() != null ? child.getOrgId() : "";
        String projectId = child.getProjectId() != null ? child.getProjectId() : "";
        mapping.put(child.getEntityIdentifier(), orgId + "/" + projectId + "/" + v1Id);
      } catch (Exception ex) {
        log.warn("[CONVERSION]: Failed to look up checksum for pipeline child {}, using identity mapping",
            child.getEntityIdentifier(), ex);
        String orgId = child.getOrgId() != null ? child.getOrgId() : "";
        String projectId = child.getProjectId() != null ? child.getProjectId() : "";
        mapping.put(child.getEntityIdentifier(), orgId + "/" + projectId + "/" + child.getEntityIdentifier());
      }
    }
    return mapping;
  }

  private String resolveRootJobId(ConversionJobEntity parent) {
    return parent.getRootJobId() != null ? parent.getRootJobId() : parent.getUuid();
  }

  /**
   * Look up the ConversionChecksum for an entity (handles INLINE vs REMOTE).
   * Uses EntityMetadata for git details; falls back to EntityIdentifierDTO branch.
   */
  private Optional<ConversionChecksum> lookupChecksum(ConversionJobEntity entity) {
    String entityIdentifier = entity.getEntityIdentifier();
    String orgId = entity.getOrgId();
    String projectId = entity.getProjectId();

    if (entity.getEntityType() == EntityType.TEMPLATE) {
      String[] scope = resolveTemplateScope(entityIdentifier, orgId, projectId);
      entityIdentifier = scope[0];
      orgId = scope[1];
      projectId = scope[2];
    }

    // Resolve versionLabel for TEMPLATE entities
    String versionLabel = null;
    if (entity.getEntityType() == EntityType.TEMPLATE) {
      if (entity.getEntityMetadata() != null && entity.getEntityMetadata().getVersionLabel() != null) {
        versionLabel = entity.getEntityMetadata().getVersionLabel();
      } else if (entity.getEntityReference() != null) {
        versionLabel = entity.getEntityReference().getVersionLabel();
      }
    }

    // Resolve parentUniqueId (containing scope's uniqueId) from the current scope. This is stable across project
    // movement, so a checksum written before a move is still found after it (orgId/projectId strings would not match).
    String parentUniqueId = scopeResolutionHelper.getScopeInfo(entity.getAccountId(), orgId, projectId).getUniqueId();

    EntityMetadata metadata = entity.getEntityMetadata();
    if (metadata != null && StoreType.REMOTE == metadata.getStoreType()) {
      return conversionChecksumRepository.findByRemoteEntity(entity.getAccountId(), parentUniqueId, orgId, projectId,
          entityIdentifier, metadata.getRepo(), resolveBranch(entity), versionLabel);
    }
    return conversionChecksumRepository.findByInlineEntity(entity.getAccountId(), parentUniqueId, orgId, projectId,
        entityIdentifier, entity.getEntityType(), versionLabel);
  }

  /**
   * Save converted V1 YAML as a NEW entity (not replacing the V0 entity).
   * Generates a new V1 identifier by appending a random suffix to the original ID.
   *
   * Returns the generated V1 identifier for checksum tracking.
   * Checksum upsert is handled separately by the caller.
   */
  private String resolveV1Identifier(ConversionJobEntity entity) {
    boolean keepOriginalIdentifier =
        entity.getEntityType() == EntityType.INPUT_SET || entity.getEntityType() == EntityType.TRIGGER;
    if (keepOriginalIdentifier) {
      return entity.getEntityIdentifier();
    }
    // For forceReconvert, reuse the existing V1 identifier from this version's checksum
    Optional<ConversionChecksum> existingChecksum = lookupChecksum(entity);
    if (Boolean.TRUE.equals(entity.getForceReconvert()) && existingChecksum.isPresent()
        && existingChecksum.get().getV1Identifier() != null) {
      return existingChecksum.get().getV1Identifier();
    }
    // For templates: check if ANY version of this template has already been converted (shared V1 identifier)
    if (entity.getEntityType() == EntityType.TEMPLATE) {
      String entityIdentifier = entity.getEntityIdentifier();
      String orgId = entity.getOrgId();
      String projectId = entity.getProjectId();
      String[] scope = resolveTemplateScope(entityIdentifier, orgId, projectId);
      entityIdentifier = scope[0];
      orgId = scope[1];
      projectId = scope[2];
      String parentUniqueId = scopeResolutionHelper.getScopeInfo(entity.getAccountId(), orgId, projectId).getUniqueId();
      Optional<ConversionChecksum> anyVersionChecksum = conversionChecksumRepository.findAnyByEntity(
          entity.getAccountId(), parentUniqueId, orgId, projectId, entityIdentifier, entity.getEntityType());
      if (anyVersionChecksum.isPresent() && anyVersionChecksum.get().getV1Identifier() != null) {
        return anyVersionChecksum.get().getV1Identifier();
      }
    }
    return generateV1Identifier(entity.getEntityIdentifier());
  }

  private void saveV1Yaml(ConversionJobEntity entity, String v1Yaml, String v1Identifier) {
    boolean isUpdate = false;
    if (Boolean.TRUE.equals(entity.getForceReconvert())) {
      Optional<ConversionChecksum> existingChecksum = lookupChecksum(entity);
      isUpdate = existingChecksum.isPresent() && existingChecksum.get().getV1Identifier() != null;
    }

    switch (entity.getEntityType()) {
      case PIPELINE:
        savePipelineV1Yaml(entity, v1Yaml, v1Identifier, isUpdate);
        break;
      case INPUT_SET:
        saveInputSetV1Yaml(entity, v1Yaml, v1Identifier, isUpdate);
        break;
      case TEMPLATE:
        saveTemplateV1Yaml(entity, v1Yaml, v1Identifier, isUpdate);
        break;
      case TRIGGER:
        saveTriggerV1Yaml(entity, v1Yaml, v1Identifier, isUpdate);
        break;
      default:
        log.warn("[CONVERSION]: Job {} — Unknown entity type {} for save", entity.getUuid(), entity.getEntityType());
        break;
    }
  }

  /**
   * Generate a new identifier by appending a random 4-char suffix to the original entity ID.
   * 4 hex chars = 65,536 combinations, well above the 10,000 pipeline-per-project limit.
   * Example: "my_pipeline" → "my_pipeline_a3f2"
   */
  private String generateV1Identifier(String originalIdentifier) {
    String baseIdentifier = originalIdentifier;
    String prefix = "";
    if (baseIdentifier.startsWith(ACCOUNT_SCOPE_PREFIX)) {
      prefix = ACCOUNT_SCOPE_PREFIX;
      baseIdentifier = baseIdentifier.substring(ACCOUNT_SCOPE_PREFIX.length());
    } else if (baseIdentifier.startsWith(ORG_SCOPE_PREFIX)) {
      prefix = ORG_SCOPE_PREFIX;
      baseIdentifier = baseIdentifier.substring(ORG_SCOPE_PREFIX.length());
    }
    String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4);
    int maxBaseLength = MAX_IDENTIFIER_LENGTH - V1_SUFFIX_LENGTH;
    if (baseIdentifier.length() > maxBaseLength) {
      baseIdentifier = baseIdentifier.substring(0, maxBaseLength);
    }
    return prefix + baseIdentifier + "_" + suffix;
  }

  /**
   * Resolve the scope and strip the prefix for a scoped template identifier.
   * Returns [strippedIdentifier, orgId, projectId] with nulls set according to scope.
   */
  private String[] resolveTemplateScope(String identifier, String orgId, String projectId) {
    if (identifier.startsWith(ACCOUNT_SCOPE_PREFIX)) {
      return new String[] {identifier.substring(ACCOUNT_SCOPE_PREFIX.length()), null, null};
    } else if (identifier.startsWith(ORG_SCOPE_PREFIX)) {
      return new String[] {identifier.substring(ORG_SCOPE_PREFIX.length()), orgId, null};
    }
    return new String[] {identifier, orgId, projectId};
  }

  private void savePipelineV1Yaml(ConversionJobEntity entity, String v1Yaml, String v1Identifier, boolean isUpdate) {
    EntityMetadata metadata = entity.getEntityMetadata();
    boolean isRemote = metadata != null && StoreType.REMOTE == metadata.getStoreType();

    String pipelineName = metadata != null && metadata.getName() != null ? metadata.getName() : v1Identifier;

    PipelineEntity.PipelineEntityBuilder builder = PipelineEntity.builder()
                                                       .accountId(entity.getAccountId())
                                                       .orgIdentifier(entity.getOrgId())
                                                       .projectIdentifier(entity.getProjectId())
                                                       .identifier(v1Identifier)
                                                       .name(pipelineName)
                                                       .yaml(v1Yaml)
                                                       .harnessVersion(HarnessYamlVersion.V1)
                                                       .convertedFromPipelineId(entity.getEntityIdentifier());

    if (isRemote) {
      String branch = resolveBranch(entity);
      String v1FilePath = generatePipelineV1FilePath(metadata.getFilePath(), v1Identifier);
      builder.storeType(StoreType.REMOTE)
          .connectorRef(metadata.getConnectorRef())
          .repo(metadata.getRepo())
          .filePath(v1FilePath);
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                   .branch(branch)
                                                   .isNewBranch(false)
                                                   .connectorRef(metadata.getConnectorRef())
                                                   .repoName(metadata.getRepo())
                                                   .storeType(StoreType.REMOTE)
                                                   .filePath(v1FilePath)
                                                   .commitMsg("V0 to V1 auto-conversion: " + v1Identifier)
                                                   .build());
    }

    PipelineEntity v1Pipeline = builder.build();

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
    v1Pipeline.setParentUniqueId(scopeInfo.getUniqueId());

    if (isUpdate) {
      try {
        // Remote: getMetadataOnly=false so the head blob/commit load into thread-local SCM metadata for the base SHA.
        Optional<PipelineEntity> existing = pmsPipelineService.getPipeline(entity.getAccountId(), entity.getOrgId(),
            entity.getProjectId(), v1Identifier, false, !isRemote, false, false, scopeInfo, true);
        if (existing.isPresent()) {
          ChangeType changeType = isRemote ? ChangeType.MODIFY : ChangeType.NONE;
          if (isRemote) {
            seedRemoteUpdateBaseSha();
          }
          pmsPipelineService.validateAndUpdatePipeline(v1Pipeline, changeType, false, false, scopeInfo, true);
          log.info("[CONVERSION]: Job {} — updated V1 pipeline {} (remote={}) from {}", entity.getUuid(), v1Identifier,
              isRemote, entity.getEntityIdentifier());
          return;
        }
      } catch (EntityNotFoundException ex) {
        log.info("[CONVERSION]: Job {} — V1 pipeline {} not found, creating new", entity.getUuid(), v1Identifier);
      }
    }

    pmsPipelineService.validateAndCreatePipeline(v1Pipeline, false, scopeInfo, true);
    log.info("[CONVERSION]: Job {} — created V1 pipeline {} (remote={}) from {}", entity.getUuid(), v1Identifier,
        isRemote, entity.getEntityIdentifier());
  }

  /**
   * Copy the fetched file's head blob/commit into the git context as the next SCM update's optimistic-lock base SHA.
   */
  private void seedRemoteUpdateBaseSha() {
    ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
    if (scmGitMetaData == null) {
      return;
    }
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    gitEntityInfo.setLastObjectId(scmGitMetaData.getBlobId());
    gitEntityInfo.setLastCommitId(scmGitMetaData.getCommitId());
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
  }

  private String generatePipelineV1FilePath(String originalFilePath, String v1Identifier) {
    if (originalFilePath == null) {
      return ".harness/pipelines/" + v1Identifier + ".yaml";
    }
    int lastSlash = originalFilePath.lastIndexOf('/');
    String directory = lastSlash >= 0 ? originalFilePath.substring(0, lastSlash + 1) : "";
    return directory + v1Identifier + ".yaml";
  }

  /**
   * Resolve V1 file path for input sets.
   * Path pattern: .harness/.../pipelines/{pipelineId}/input_sets/{inputSetId}.yaml
   */
  private String generateInputSetV1FilePath(String originalFilePath, String v1PipelineId, String v1Identifier) {
    if (originalFilePath == null) {
      return ".harness/pipelines/" + v1PipelineId + "/input_sets/" + v1Identifier + ".yaml";
    }
    int idx = originalFilePath.indexOf("/pipelines/");
    if (idx < 0) {
      return ".harness/pipelines/" + v1PipelineId + "/input_sets/" + v1Identifier + ".yaml";
    }
    return originalFilePath.substring(0, idx) + "/pipelines/" + v1PipelineId + "/input_sets/" + v1Identifier + ".yaml";
  }

  /**
   * Resolve V1 file path for templates.
   * Path pattern: .harness/.../templates/{templateId}/{versionLabel}.yaml
   */
  private String generateTemplateV1FilePath(String originalFilePath, String v1Identifier, String versionLabel) {
    if (originalFilePath == null) {
      return ".harness/templates/" + v1Identifier + "/" + versionLabel + ".yaml";
    }
    int idx = originalFilePath.indexOf("/templates/");
    if (idx < 0) {
      return ".harness/templates/" + v1Identifier + "/" + versionLabel + ".yaml";
    }
    return originalFilePath.substring(0, idx) + "/templates/" + v1Identifier + "/" + versionLabel + ".yaml";
  }

  private TemplateResponseDTO fetchExistingTemplate(
      String accountId, String orgId, String projectId, String identifier, String versionLabel, String branch) {
    try {
      return NGRestUtils.getResponse(templateResourceClient.get(
          identifier, accountId, orgId, projectId, null, versionLabel, false, branch, null, "false"));
    } catch (Exception ex) {
      return null;
    }
  }

  private void saveTemplateV1Yaml(ConversionJobEntity entity, String v1Yaml, String v1Identifier, boolean isUpdate) {
    String[] scope = resolveTemplateScope(v1Identifier, entity.getOrgId(), entity.getProjectId());
    v1Identifier = scope[0];
    String orgId = scope[1];
    String projectId = scope[2];

    EntityMetadata metadata = entity.getEntityMetadata();
    boolean isRemote = metadata != null && StoreType.REMOTE == metadata.getStoreType();

    String templateName = metadata != null && metadata.getName() != null ? metadata.getName() : v1Identifier;
    String versionLabel = metadata != null && metadata.getVersionLabel() != null ? metadata.getVersionLabel() : "1";
    String branch = isRemote ? resolveBranch(entity) : null;

    if (isUpdate) {
      TemplateResponseDTO existing =
          fetchExistingTemplate(entity.getAccountId(), orgId, projectId, v1Identifier, versionLabel, branch);
      if (existing != null) {
        TemplateUpdateRequestBody updateBody = new TemplateUpdateRequestBody();
        updateBody.setTemplateYaml(v1Yaml);
        updateBody.setYamlVersion("1");
        updateBody.setIdentifier(v1Identifier);
        updateBody.setName(templateName);
        updateBody.setLabel(versionLabel);
        updateBody.setComments("V0 to V1 force re-conversion");
        updateBody.setTags(java.util.Collections.emptyMap());

        if (isRemote && metadata != null) {
          GitUpdateDetails gitDetails = new GitUpdateDetails();
          gitDetails.setBranchName(branch);
          gitDetails.setCommitMessage("V0 to V1 force re-conversion: " + v1Identifier);
          gitDetails.setConnectorRef(metadata.getConnectorRef());
          gitDetails.setFilePath(generateTemplateV1FilePath(metadata.getFilePath(), v1Identifier, versionLabel));
          gitDetails.setRepoName(metadata.getRepo());
          gitDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.REMOTE);
          // Base SHA for the SCM optimistic-lock, from the fetched template's git details.
          if (existing.getGitDetails() != null) {
            gitDetails.setLastObjectId(existing.getGitDetails().getObjectId());
            gitDetails.setLastCommitId(existing.getGitDetails().getCommitId());
          }
          updateBody.setGitDetails(gitDetails);
        }

        if (orgId == null && projectId == null) {
          NGRestUtils.getResponseWithErrorDetails(templateResourceClient.updateV1AccountTemplate(
              entity.getAccountId(), v1Identifier, versionLabel, updateBody));
        } else if (projectId == null) {
          NGRestUtils.getResponseWithErrorDetails(templateResourceClient.updateV1OrgTemplate(
              entity.getAccountId(), orgId, v1Identifier, versionLabel, updateBody));
        } else {
          NGRestUtils.getResponseWithErrorDetails(templateResourceClient.updateV1Template(
              entity.getAccountId(), orgId, projectId, v1Identifier, versionLabel, updateBody));
        }

        log.info("[CONVERSION]: Job {} — updated V1 template {} (remote={}) from {} via template-service v1 API",
            entity.getUuid(), v1Identifier, isRemote, entity.getEntityIdentifier());
        return;
      }
    }

    TemplateCreateRequestBody createBody = new TemplateCreateRequestBody();
    createBody.setTemplateYaml(v1Yaml);
    createBody.setYamlVersion("1");
    createBody.setIdentifier(v1Identifier);
    createBody.setName(templateName);
    createBody.setLabel(versionLabel);
    boolean shouldBeStable = metadata == null || !Boolean.FALSE.equals(metadata.getStableVersion());
    createBody.setIsStable(shouldBeStable);
    createBody.setComments("V0 to V1 auto-conversion");
    createBody.setTags(java.util.Collections.emptyMap());
    // TODO (PIPE): Populate semanticLabels from the V0 source template's label mappings.
    // Query GET /templates/{id}/labels?versionLabel=<versionLabel> before this create call
    // and forward the resolved labels so that newly created V1 templates carry label associations.
    createBody.setSemanticLabels(java.util.Collections.emptyList());

    if (isRemote && metadata != null) {
      GitCreateDetails gitDetails = new GitCreateDetails();
      gitDetails.setBranchName(branch);
      gitDetails.setFilePath(generateTemplateV1FilePath(metadata.getFilePath(), v1Identifier, versionLabel));
      gitDetails.setCommitMessage("V0 to V1 auto-conversion: " + v1Identifier);
      gitDetails.setConnectorRef(metadata.getConnectorRef());
      gitDetails.setStoreType(GitCreateDetails.StoreTypeEnum.REMOTE);
      gitDetails.setRepoName(metadata.getRepo());
      createBody.setGitDetails(gitDetails);
    }

    if (orgId == null && projectId == null) {
      NGRestUtils.getResponseWithErrorDetails(
          templateResourceClient.createV1AccountTemplate(entity.getAccountId(), createBody));
    } else if (projectId == null) {
      NGRestUtils.getResponseWithErrorDetails(
          templateResourceClient.createV1OrgTemplate(entity.getAccountId(), orgId, createBody));
    } else {
      NGRestUtils.getResponseWithErrorDetails(
          templateResourceClient.createV1Template(entity.getAccountId(), orgId, projectId, createBody));
    }

    log.info("[CONVERSION]: Job {} — created V1 template {} (remote={}) from {} via template-service v1 API",
        entity.getUuid(), v1Identifier, isRemote, entity.getEntityIdentifier());
  }

  private void saveInputSetV1Yaml(ConversionJobEntity entity, String v1Yaml, String v1Identifier, boolean isUpdate) {
    EntityMetadata metadata = entity.getEntityMetadata();
    boolean isRemote = metadata != null && StoreType.REMOTE == metadata.getStoreType();

    String inputSetName = metadata != null && metadata.getName() != null ? metadata.getName() : v1Identifier;
    String v1PipelineId = entity.getV1Identifier() != null ? entity.getV1Identifier() : entity.getPipelineIdentifier();

    InputSetEntityType entityType = metadata != null && metadata.getInputSetEntityType() != null
        ? InputSetEntityType.valueOf(metadata.getInputSetEntityType())
        : InputSetEntityType.INPUT_SET;
    InputSetEntity.InputSetEntityBuilder builder = InputSetEntity.builder()
                                                       .accountId(entity.getAccountId())
                                                       .orgIdentifier(entity.getOrgId())
                                                       .projectIdentifier(entity.getProjectId())
                                                       .pipelineIdentifier(v1PipelineId)
                                                       .identifier(v1Identifier)
                                                       .name(inputSetName)
                                                       .yaml(v1Yaml)
                                                       .inputSetEntityType(entityType)
                                                       .harnessVersion(HarnessYamlVersion.V1);

    if (isRemote) {
      String branch = resolveBranch(entity);
      builder.storeType(StoreType.REMOTE).connectorRef(metadata.getConnectorRef()).repo(metadata.getRepo());
      GitAwareContextHelper.populateGitDetails(
          GitEntityInfo.builder()
              .branch(branch)
              .isNewBranch(false)
              .connectorRef(metadata.getConnectorRef())
              .repoName(metadata.getRepo())
              .storeType(StoreType.REMOTE)
              .filePath(generateInputSetV1FilePath(metadata.getFilePath(), v1PipelineId, v1Identifier))
              .commitMsg("V0 to V1 auto-conversion: " + v1Identifier)
              .build());
    } else {
      builder.storeType(StoreType.INLINE);
    }

    InputSetEntity v1InputSet = builder.build();

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
    v1InputSet.setParentUniqueId(scopeInfo.getUniqueId());

    if (isUpdate) {
      // getWithoutValidations loads the full remote file, stashing its head blob/commit for the base SHA.
      // isParentIdQueryingEnabled=true keys on parentUniqueId (movement-stable), matching the metadata fetch.
      Optional<InputSetEntity> existing =
          pmsInputSetService.getWithoutValidations(scopeInfo, v1PipelineId, v1Identifier, false, false, false, true);
      if (existing.isPresent()) {
        ChangeType changeType = isRemote ? ChangeType.MODIFY : ChangeType.NONE;
        if (isRemote) {
          seedRemoteUpdateBaseSha();
        }
        pmsInputSetService.update(changeType, v1InputSet, false);
        log.info("[CONVERSION]: Job {} — updated V1 input set {} (remote={}) from {}", entity.getUuid(), v1Identifier,
            isRemote, entity.getEntityIdentifier());
        return;
      }
    }

    // isParentIdQueryingEnabled(ForPipeline)=true so create keys on parentUniqueId (movement-stable), consistent with
    // the metadata fetch and update paths. The input set doc's orgId/projectId strings can be stale after a move.
    pmsInputSetService.create(v1InputSet, false, scopeInfo, true, true);
    log.info("[CONVERSION]: Job {} — created V1 input set {} (remote={}) from {}", entity.getUuid(), v1Identifier,
        isRemote, entity.getEntityIdentifier());
  }

  /**
   * Upsert a ConversionChecksum record to track the V0→V1 mapping.
   * Checksum comes from the go-convert service response (not self-computed).
   *
   * For in-place conversion, v1Identifier == entityIdentifier.
   */
  private void upsertConversionChecksum(ConversionJobEntity entity, String responseChecksum, String v1Identifier) {
    try {
      EntityMetadata metadata = entity.getEntityMetadata();
      StoreType storeType =
          (metadata != null && StoreType.REMOTE == metadata.getStoreType()) ? StoreType.REMOTE : StoreType.INLINE;

      String entityIdentifier = entity.getEntityIdentifier();
      String orgId = entity.getOrgId();
      String projectId = entity.getProjectId();

      if (entity.getEntityType() == EntityType.TEMPLATE) {
        String[] scope = resolveTemplateScope(entityIdentifier, orgId, projectId);
        entityIdentifier = scope[0];
        orgId = scope[1];
        projectId = scope[2];
      }

      // parentUniqueId (containing scope's uniqueId) is the movement-stable lookup key for checksum records.
      String parentUniqueId = scopeResolutionHelper.getScopeInfo(entity.getAccountId(), orgId, projectId).getUniqueId();

      ConversionChecksum checksum = ConversionChecksum.builder()
                                        .accountId(entity.getAccountId())
                                        .orgId(orgId)
                                        .projectId(projectId)
                                        .entityId(entityIdentifier)
                                        .entityType(entity.getEntityType())
                                        .versionLabel(metadata != null ? metadata.getVersionLabel() : null)
                                        .storeType(storeType)
                                        .repoURL(metadata != null ? metadata.getRepo() : null)
                                        .filePath(metadata != null ? metadata.getFilePath() : null)
                                        .branch(storeType == StoreType.REMOTE ? resolveBranch(entity) : null)
                                        .checksum(responseChecksum)
                                        .v1Identifier(v1Identifier)
                                        .build();
      checksum.setParentUniqueId(parentUniqueId);

      conversionChecksumRepository.upsert(checksum);
      log.info("[CONVERSION]: Job {} — upserted checksum record for {} {}", entity.getUuid(), entity.getEntityType(),
          entity.getEntityIdentifier());
    } catch (Exception ex) {
      log.error("[CONVERSION]: Job {} — failed to upsert checksum record", entity.getUuid(), ex);
    }
  }

  /**
   * Create child SINGLE jobs for each discovered template reference.
   */
  private List<ConversionJobEntity> createChildJobsFromTemplateRefs(
      ConversionJobEntity parent, List<TemplateReference> templateRefs) {
    List<ConversionJobEntity> children = new ArrayList<>();
    String rootId = resolveRootJobId(parent);
    for (TemplateReference ref : templateRefs) {
      EntityIdentifierDTO entityRef = EntityIdentifierDTO.builder()
                                          .entityId(ref.getTemplateRef())
                                          .entityType(EntityType.TEMPLATE)
                                          .versionLabel(ref.getVersionLabel())
                                          .branch(ref.getGitBranch())
                                          .build();
      children.add(ConversionJobEntity.builder()
                       .status(ConversionStatus.QUEUED)
                       .accountId(parent.getAccountId())
                       .orgId(parent.getOrgId())
                       .projectId(parent.getProjectId())
                       .actionType(ConversionActionType.SINGLE)
                       .entityType(EntityType.TEMPLATE)
                       .entityIdentifier(ref.getTemplateRef())
                       .entityReference(entityRef)
                       .forceReconvert(parent.getForceReconvert())
                       .triggerPrincipal(parent.getTriggerPrincipal())
                       .parentJobId(parent.getUuid())
                       .rootJobId(rootId)
                       .depth(parent.getDepth() + 1)
                       .nextIteration(System.currentTimeMillis())
                       .createdAt(System.currentTimeMillis())
                       .build());
    }
    return children;
  }

  /**
   * Create child SINGLE jobs for each discovered chained pipeline reference.
   */
  private List<ConversionJobEntity> createChildJobsFromPipelineChainRefs(
      ConversionJobEntity parent, List<PipelineChainReference> pipelineChainRefs) {
    List<ConversionJobEntity> children = new ArrayList<>();
    String rootId = resolveRootJobId(parent);
    for (PipelineChainReference ref : pipelineChainRefs) {
      String childOrgId = ref.getOrgIdentifier() != null ? ref.getOrgIdentifier() : parent.getOrgId();
      String childProjectId = ref.getProjectIdentifier() != null ? ref.getProjectIdentifier() : parent.getProjectId();
      EntityIdentifierDTO entityRef =
          EntityIdentifierDTO.builder().entityId(ref.getPipelineIdentifier()).entityType(EntityType.PIPELINE).build();
      children.add(ConversionJobEntity.builder()
                       .status(ConversionStatus.QUEUED)
                       .accountId(parent.getAccountId())
                       .orgId(childOrgId)
                       .projectId(childProjectId)
                       .actionType(ConversionActionType.SINGLE)
                       .entityType(EntityType.PIPELINE)
                       .entityIdentifier(ref.getPipelineIdentifier())
                       .entityReference(entityRef)
                       .forceReconvert(parent.getForceReconvert())
                       .triggerPrincipal(parent.getTriggerPrincipal())
                       .parentJobId(parent.getUuid())
                       .rootJobId(rootId)
                       .depth(parent.getDepth() + 1)
                       .nextIteration(System.currentTimeMillis())
                       .createdAt(System.currentTimeMillis())
                       .build());
    }
    return children;
  }

  /**
   * Create child SINGLE jobs for all input sets belonging to this pipeline.
   */
  private List<ConversionJobEntity> createChildJobsForInputSets(
      ConversionJobEntity parent, String v1PipelineIdentifier, String pipelineV0Yaml) {
    List<ConversionJobEntity> children = new ArrayList<>();
    String rootId = resolveRootJobId(parent);
    // Key on parentUniqueId (containing scope's uniqueId), stable across project movement. Raw orgId/projectId
    // strings on the job are the scope conversion was requested against and stop matching once the project moves.
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(parent.getAccountId(), parent.getOrgId(), parent.getProjectId());
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(parent.getAccountId())
                            .and(InputSetEntityKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(InputSetEntityKeys.pipelineIdentifier)
                            .is(parent.getEntityIdentifier())
                            .and(InputSetEntityKeys.deleted)
                            .is(false);

    List<InputSetEntity> inputSets = pmsInputSetService.list(criteria);
    for (InputSetEntity inputSet : inputSets) {
      EntityIdentifierDTO entityRef = EntityIdentifierDTO.builder()
                                          .entityId(inputSet.getIdentifier())
                                          .entityType(EntityType.INPUT_SET)
                                          .branch(inputSet.getBranch())
                                          .build();
      // Inherit the parent pipeline job's scope (the resolvable current org/project). The input set doc's own
      // orgId/projectId strings can be stale after a project move; only parentUniqueId is authoritative.
      children.add(ConversionJobEntity.builder()
                       .status(ConversionStatus.QUEUED)
                       .accountId(parent.getAccountId())
                       .orgId(parent.getOrgId())
                       .projectId(parent.getProjectId())
                       .actionType(ConversionActionType.SINGLE)
                       .entityType(EntityType.INPUT_SET)
                       .entityIdentifier(inputSet.getIdentifier())
                       .entityReference(entityRef)
                       .pipelineIdentifier(parent.getEntityIdentifier())
                       .v1Identifier(v1PipelineIdentifier)
                       .entityMetadata(EntityMetadata.builder().contextPipelineYaml(pipelineV0Yaml).build())
                       .forceReconvert(parent.getForceReconvert())
                       .triggerPrincipal(parent.getTriggerPrincipal())
                       .parentJobId(parent.getUuid())
                       .rootJobId(rootId)
                       .depth(parent.getDepth() + 1)
                       .nextIteration(System.currentTimeMillis())
                       .createdAt(System.currentTimeMillis())
                       .build());
    }
    return children;
  }

  private ConversionJobMetricsDTO buildMetrics(
      long total, long processed, long converted, long skipped, long failed, int progressPercentage) {
    return ConversionJobMetricsDTO.builder()
        .totalEntities((int) total)
        .processedEntities((int) processed)
        .convertedEntities((int) converted)
        .skippedEntities((int) skipped)
        .failedEntities((int) failed)
        .progressPercentage(progressPercentage)
        .build();
  }

  // ---------------------------------------------------------------------------
  // Error handling & retry
  // ---------------------------------------------------------------------------

  /**
   * Handle error during job processing. Implements retry with exponential backoff.
   */
  private void handleJobError(ConversionJobEntity entity, Exception ex) {
    int retryCount = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
    int maxRetries = entity.getMaxRetries() != null ? entity.getMaxRetries() : ConversionJobEntity.DEFAULT_MAX_RETRIES;

    // Some exceptions (e.g. NPE, empty-message InvalidRequestException) return a null/blank getMessage(); fall back
    // to ex.toString() (exception class + message) so the persisted reason is never blank. Full stack goes to logs.
    String failureReason = resolveFailureReason(ex);

    if (retryCount < maxRetries) {
      // Retry with exponential backoff
      long backoffMinutes = retryCount < RETRY_BACKOFF_MINUTES.length
          ? RETRY_BACKOFF_MINUTES[retryCount]
          : RETRY_BACKOFF_MINUTES[RETRY_BACKOFF_MINUTES.length - 1];

      long nextRetryTime = System.currentTimeMillis() + (backoffMinutes * 60 * 1000);

      Update update = new Update()
                          .set(ConversionJobEntityKeys.retryCount, retryCount + 1)
                          .set(ConversionJobEntityKeys.lastFailureReason, failureReason)
                          .set(ConversionJobEntityKeys.nextIteration, nextRetryTime);

      updateJobFields(entity.getUuid(), update);

      log.warn("[CONVERSION]: Job {} failed (attempt {}/{}), scheduling retry in {} minutes", entity.getUuid(),
          retryCount + 1, maxRetries, backoffMinutes);
    } else {
      // Max retries exhausted — mark as FAILED
      log.error("[CONVERSION]: Job {} failed after {} retries, marking as FAILED", entity.getUuid(), maxRetries);
      ConversionJobMetricsDTO currentMetrics = entity.getConversionMetrics() != null
          ? entity.getConversionMetrics()
          : ConversionJobMetricsDTO.builder().build();
      Update failUpdate = new Update()
                              .set(ConversionJobEntityKeys.status, ConversionStatus.FAILED)
                              .set(ConversionJobEntityKeys.endTs, System.currentTimeMillis())
                              .set(ConversionJobEntityKeys.errorMessage, failureReason)
                              .set(ConversionJobEntityKeys.lastFailureReason, failureReason)
                              .unset(ConversionJobEntityKeys.entityMetadata);

      updateJobFields(entity.getUuid(), failUpdate);

      // Wake parent if this is a child job
      wakeParent(entity);
    }
  }

  /**
   * Resolve a non-blank failure reason for persistence. Prefers {@code ex.getMessage()}; falls back to
   * {@code ex.toString()} (exception class + message) when the message is null/blank. This is a short summary,
   * not the stack trace — the full stack is logged separately.
   */
  private String resolveFailureReason(Exception ex) {
    if (ex == null) {
      return "Unknown error";
    }
    String message = ex.getMessage();
    return (message != null && !message.trim().isEmpty()) ? message : ex.toString();
  }

  // ---------------------------------------------------------------------------
  // Shared helpers
  // ---------------------------------------------------------------------------

  /**
   * Resolve the branch for a remote entity: prefer entityReference branch (user-provided),
   * fall back to metadata branch (populated from entity/SCM at fetch time),
   * then fetch the repo's default branch from SCM as last resort.
   */
  private String resolveBranch(ConversionJobEntity entity) {
    EntityIdentifierDTO ref = entity.getEntityReference();
    if (ref != null && ref.getBranch() != null) {
      return ref.getBranch();
    }
    EntityMetadata metadata = entity.getEntityMetadata();
    if (metadata != null && metadata.getBranch() != null) {
      return metadata.getBranch();
    }
    // Fetch default branch from SCM using connector + repo from metadata
    if (metadata != null && metadata.getConnectorRef() != null && metadata.getRepo() != null) {
      try {
        String orgId = entity.getOrgId();
        String projectId = entity.getProjectId();
        if (entity.getEntityType() == EntityType.TEMPLATE) {
          String[] scope = resolveTemplateScope(entity.getEntityIdentifier(), orgId, projectId);
          orgId = scope[1];
          projectId = scope[2];
        }
        ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(entity.getAccountId(), orgId, projectId);
        io.harness.EntityType harnessEntityType = entity.getEntityType() == EntityType.TEMPLATE
            ? io.harness.EntityType.TEMPLATE
            : io.harness.EntityType.PIPELINES;
        String defaultBranch = gitAwareEntityHelper.getDefaultBranch(
            metadata.getConnectorRef(), metadata.getRepo(), scopeInfo, true, harnessEntityType);
        log.info("[CONVERSION]: Job {} — resolved default branch '{}' from SCM for entity {}", entity.getUuid(),
            defaultBranch, entity.getEntityIdentifier());
        return defaultBranch;
      } catch (Exception ex) {
        log.warn("[CONVERSION]: Job {} — failed to fetch default branch from SCM for entity {}", entity.getUuid(),
            entity.getEntityIdentifier(), ex);
      }
    }
    return null;
  }

  /**
   * Mark a job as complete (final status), clear entityMetadata, cache result tree on root, and wake parent.
   */
  private void markJobComplete(ConversionJobEntity entity, ConversionStatus status, ConversionJobMetricsDTO metrics) {
    conversionJobService.updateJobStatus(entity.getUuid(), status, metrics);
    entity.setStatus(status);

    // Cache the result tree on root jobs (no parent) when they reach final status
    if (entity.getParentJobId() == null) {
      try {
        List<ConversionNodeSummary> conversionResults = buildConversionResults(entity);
        updateJobFields(
            entity.getUuid(), new Update().set(ConversionJobEntityKeys.conversionResults, conversionResults));
      } catch (Exception ex) {
        log.warn("[CONVERSION]: Job {} — failed to cache result tree", entity.getUuid(), ex);
      }
    }

    wakeParent(entity);
  }

  /**
   * Wake the parent job and update its summary progressively based on current child statuses.
   */
  private void wakeParent(ConversionJobEntity entity) {
    if (entity.getParentJobId() == null) {
      return;
    }

    try {
      Optional<ConversionJobEntity> parentOpt = conversionJobService.getJobByUuid(entity.getParentJobId());
      if (parentOpt.isEmpty()) {
        return;
      }
      ConversionJobEntity parent = parentOpt.get();

      if (ConversionStatus.isFinalStatus(parent.getStatus())) {
        return;
      }

      Update update = new Update().set(ConversionJobEntityKeys.nextIteration, System.currentTimeMillis());

      if (parent.getActionType() == ConversionActionType.BATCH
          || parent.getActionType() == ConversionActionType.PROJECT) {
        // BATCH/PROJECT: count pipeline children as entities
        List<ConversionJobEntity> siblings = conversionJobService.getChildJobs(entity.getParentJobId());
        long totalChildren = siblings.size();
        long successCount = siblings.stream().filter(c -> c.getStatus() == ConversionStatus.SUCCESS).count();
        long partialCount = siblings.stream().filter(c -> c.getStatus() == ConversionStatus.PARTIAL_SUCCESS).count();
        long failedCount = siblings.stream().filter(c -> c.getStatus() == ConversionStatus.FAILED).count();
        long skippedCount = siblings.stream().filter(c -> c.getStatus() == ConversionStatus.SKIPPED).count();
        long doneCount = successCount + partialCount + failedCount + skippedCount;
        long convertedCount = successCount + partialCount;
        int progress = totalChildren > 0 ? (int) ((doneCount * 100) / totalChildren) : 0;

        update.set(ConversionJobEntityKeys.conversionMetrics,
            buildMetrics(totalChildren, doneCount, convertedCount, skippedCount, failedCount, progress));
      }
      // SINGLE parents: don't update metrics here — handleInputSetChildrenCheck manages them
      // (1 pipeline = 1 entity, children are sub-entities tracked in pipelineMetrics)

      updateJobFields(entity.getParentJobId(), update);

      log.info("[CONVERSION]: Woke parent job {} after child {} completed", entity.getParentJobId(), entity.getUuid());
    } catch (Exception ex) {
      log.error(
          "[CONVERSION]: Failed to wake parent job {} for child {}", entity.getParentJobId(), entity.getUuid(), ex);
    }
  }

  /**
   * Put job to sleep by setting nextIteration to now + MAX_SLEEP_MILLIS.
   * If a child wakes the parent sooner, it overwrites nextIteration to now.
   * If no child wakes it within 30 min, the iterator picks it up for a re-check:
   *   - children done → proceed normally
   *   - children still running → re-sleep
   *   - children stuck/evicted → mark FAILED
   */
  private void sleepJob(String jobUuid, String reason) {
    log.info("[CONVERSION]: Job {} sleeping — {}", jobUuid, reason);
    long wakeupDeadline = System.currentTimeMillis() + MAX_SLEEP_MILLIS;
    Update sleepUpdate = new Update().set(ConversionJobEntityKeys.nextIteration, wakeupDeadline);
    updateJobFields(jobUuid, sleepUpdate);
  }

  /**
   * Wake a job by setting nextIteration to now. The iterator will pick it up
   * on the next cycle with a fresh DB read.
   */
  private void wakeJob(String jobUuid) {
    Update update = new Update().set(ConversionJobEntityKeys.nextIteration, System.currentTimeMillis());
    updateJobFields(jobUuid, update);
  }

  /**
   * Update specific fields on a job entity.
   */
  private void updateJobFields(String uuid, Update update) {
    Criteria criteria = Criteria.where(ConversionJobEntityKeys.uuid).is(uuid);
    mongoTemplate.updateFirst(
        org.springframework.data.mongodb.core.query.Query.query(criteria), update, ConversionJobEntity.class);
  }

  // ---------------------------------------------------------------------------
  // Trigger conversion support
  // ---------------------------------------------------------------------------

  /**
   * Create child SINGLE jobs for all V0 triggers targeting this pipeline.
   */
  private List<ConversionJobEntity> createChildJobsForTriggers(
      ConversionJobEntity parent, String v1PipelineIdentifier, String pipelineV0Yaml) {
    List<ConversionJobEntity> children = new ArrayList<>();
    String rootId = resolveRootJobId(parent);
    try {
      // Key on parentUniqueId (movement-stable scope id), not raw orgId/projectId which drift after a project move.
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(parent.getAccountId(), parent.getOrgId(), parent.getProjectId());
      Criteria criteria = Criteria.where(NGTriggerEntityKeys.accountId)
                              .is(parent.getAccountId())
                              .and(NGTriggerEntityKeys.parentUniqueId)
                              .is(scopeInfo.getUniqueId())
                              .and(NGTriggerEntityKeys.targetIdentifier)
                              .is(parent.getEntityIdentifier())
                              .and(NGTriggerEntityKeys.deleted)
                              .is(false);

      List<NGTriggerEntity> triggers = ngTriggerService.findTriggersByCriteria(criteria);

      for (NGTriggerEntity trigger : triggers) {
        if (HarnessYamlVersion.V1.equals(trigger.getHarnessVersion())) {
          continue;
        }
        if (trigger.getYaml() == null || trigger.getYaml().isEmpty()) {
          log.warn(
              "[CONVERSION]: Job {} — trigger {} has no YAML, skipping", parent.getUuid(), trigger.getIdentifier());
          continue;
        }

        EntityIdentifierDTO entityRef =
            EntityIdentifierDTO.builder().entityId(trigger.getIdentifier()).entityType(EntityType.TRIGGER).build();

        EntityMetadata triggerMetadata = EntityMetadata.builder()
                                             .yaml(trigger.getYaml())
                                             .name(trigger.getName())
                                             .harnessVersion(trigger.getHarnessVersion())
                                             .contextPipelineYaml(pipelineV0Yaml)
                                             .build();

        // Inherit the parent pipeline job's scope (resolvable current org/project); the trigger doc's own
        // orgId/projectId can be stale after a project move.
        children.add(ConversionJobEntity.builder()
                         .status(ConversionStatus.QUEUED)
                         .accountId(parent.getAccountId())
                         .orgId(parent.getOrgId())
                         .projectId(parent.getProjectId())
                         .actionType(ConversionActionType.SINGLE)
                         .entityType(EntityType.TRIGGER)
                         .entityIdentifier(trigger.getIdentifier())
                         .entityReference(entityRef)
                         .pipelineIdentifier(parent.getEntityIdentifier())
                         .v1Identifier(v1PipelineIdentifier)
                         .entityMetadata(triggerMetadata)
                         .forceReconvert(parent.getForceReconvert())
                         .triggerPrincipal(parent.getTriggerPrincipal())
                         .parentJobId(parent.getUuid())
                         .rootJobId(rootId)
                         .depth(parent.getDepth() + 1)
                         .nextIteration(System.currentTimeMillis())
                         .createdAt(System.currentTimeMillis())
                         .build());
      }

      log.info("[CONVERSION]: Job {} — discovered {} triggers for pipeline {}", parent.getUuid(), children.size(),
          parent.getEntityIdentifier());
    } catch (Exception ex) {
      log.error("[CONVERSION]: Job {} — failed to discover triggers for pipeline {}", parent.getUuid(),
          parent.getEntityIdentifier(), ex);
    }
    return children;
  }

  /**
   * Create a new trigger targeting the V1 pipeline using the converted YAML.
   * Uses NGTriggerElementMapper to build a fully-formed entity (including metadata)
   * so that ngTriggerService.create doesn't NPE on metadata fields.
   */
  private void saveTriggerV1Yaml(ConversionJobEntity entity, String v1Yaml, String v1Identifier, boolean isUpdate) {
    NGTriggerEntity newTrigger = ngTriggerElementMapper.toTriggerEntity(
        entity.getAccountId(), entity.getOrgId(), entity.getProjectId(), entity.getEntityIdentifier(), v1Yaml, false);

    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
    newTrigger.setParentUniqueId(scopeInfo.getUniqueId());

    if (isUpdate) {
      // isParentIdQueryingEnabled=true so lookup/update key on parentUniqueId (movement-stable), matching the
      // pipeline and input set save paths.
      Optional<NGTriggerEntity> existing = ngTriggerService.get(entity.getAccountId(), entity.getOrgId(),
          entity.getProjectId(), newTrigger.getTargetIdentifier(), entity.getEntityIdentifier(), scopeInfo, true);
      if (existing.isPresent()) {
        ngTriggerService.update(newTrigger, existing.get(), scopeInfo, true);
        log.info("[CONVERSION]: Job {} — updated trigger {} for V1 pipeline {}", entity.getUuid(),
            entity.getEntityIdentifier(), newTrigger.getTargetIdentifier());
        return;
      }
    }

    ngTriggerService.create(newTrigger, scopeInfo, true);
    log.info("[CONVERSION]: Job {} — created trigger {} for V1 pipeline {}", entity.getUuid(),
        entity.getEntityIdentifier(), newTrigger.getTargetIdentifier());
  }

  // ---------------------------------------------------------------------------
  // ConversionReport parsing
  // ---------------------------------------------------------------------------

  /**
   * Parse the ConversionReport from go-convert response into flat error details.
   */
  private List<ConversionErrorDetail> parseConversionReport(ConvertResponse response, ConversionJobEntity entity) {
    if (!response.hasReport()) {
      return java.util.Collections.emptyList();
    }
    ConversionReport report = response.getReport();
    List<ConversionErrorDetail> errors = new ArrayList<>();

    for (ConverterMessage msg : report.getMessagesList()) {
      errors.add(ConversionErrorDetail.builder()
                     .code(msg.getCode())
                     .message(msg.getMessage())
                     .severity(mapProtoSeverity(msg.getSeverity()))
                     .entityIdentifier(entity.getEntityIdentifier())
                     .entityType(entity.getEntityType())
                     .context(msg.getContextMap())
                     .build());
    }

    for (String field : report.getUnrecognizedFieldsList()) {
      errors.add(ConversionErrorDetail.builder()
                     .code("UNRECOGNIZED_FIELD")
                     .message("Unrecognized field: " + field)
                     .severity(ErrorSeverity.WARNING)
                     .entityIdentifier(entity.getEntityIdentifier())
                     .entityType(entity.getEntityType())
                     .context(Map.of("field", field))
                     .build());
    }

    for (ExpressionEntry expr : report.getExpressionsList()) {
      if (expr.getStatus() == io.harness.goconvert.proto.ConversionStatus.NOT_CONVERTED) {
        errors.add(ConversionErrorDetail.builder()
                       .code("EXPRESSION_NOT_CONVERTED")
                       .message("Expression not converted: " + expr.getOriginal())
                       .severity(ErrorSeverity.WARNING)
                       .entityIdentifier(entity.getEntityIdentifier())
                       .entityType(entity.getEntityType())
                       .context(Map.of("original", expr.getOriginal()))
                       .build());
      }
    }

    return errors;
  }

  private ErrorSeverity mapProtoSeverity(Severity severity) {
    switch (severity) {
      case ERROR:
        return ErrorSeverity.ERROR;
      case WARNING:
        return ErrorSeverity.WARNING;
      default:
        return ErrorSeverity.INFO;
    }
  }

  // ---------------------------------------------------------------------------
  // Pipeline metrics builder
  // ---------------------------------------------------------------------------

  private PipelineConversionMetricsDTO buildPipelineMetrics(List<ConversionJobEntity> children) {
    int totalIS = 0, convertedIS = 0, skippedIS = 0, failedIS = 0;
    int totalTpl = 0, convertedTpl = 0, skippedTpl = 0, failedTpl = 0;
    int totalTrg = 0, convertedTrg = 0, skippedTrg = 0, failedTrg = 0;

    for (ConversionJobEntity c : children) {
      ConversionStatus s = c.getStatus();
      switch (c.getEntityType()) {
        case INPUT_SET:
          totalIS++;
          if (s == ConversionStatus.SUCCESS)
            convertedIS++;
          else if (s == ConversionStatus.SKIPPED)
            skippedIS++;
          else if (s == ConversionStatus.FAILED)
            failedIS++;
          break;
        case TEMPLATE:
          totalTpl++;
          if (s == ConversionStatus.SUCCESS)
            convertedTpl++;
          else if (s == ConversionStatus.SKIPPED)
            skippedTpl++;
          else if (s == ConversionStatus.FAILED)
            failedTpl++;
          break;
        case TRIGGER:
          totalTrg++;
          if (s == ConversionStatus.SUCCESS)
            convertedTrg++;
          else if (s == ConversionStatus.SKIPPED)
            skippedTrg++;
          else if (s == ConversionStatus.FAILED)
            failedTrg++;
          break;
        default:
          break;
      }
    }

    return PipelineConversionMetricsDTO.builder()
        .totalInputSets(totalIS)
        .convertedInputSets(convertedIS)
        .skippedInputSets(skippedIS)
        .failedInputSets(failedIS)
        .totalTemplates(totalTpl)
        .convertedTemplates(convertedTpl)
        .skippedTemplates(skippedTpl)
        .failedTemplates(failedTpl)
        .totalTriggers(totalTrg)
        .convertedTriggers(convertedTrg)
        .skippedTriggers(skippedTrg)
        .failedTriggers(failedTrg)
        .build();
  }

  // ---------------------------------------------------------------------------
  // Result tree builder
  // ---------------------------------------------------------------------------

  /**
   * Build conversion results — one tree per direct child (pipeline) of the root job.
   * For SINGLE jobs, returns a single-element list representing the pipeline itself.
   * For BATCH/PROJECT, returns one tree per pipeline child.
   */
  private List<ConversionNodeSummary> buildConversionResults(ConversionJobEntity entity) {
    if (entity.getActionType() == ConversionActionType.SINGLE) {
      return List.of(buildNodeSummary(entity));
    }

    // BATCH/PROJECT — each child is a top-level result entry
    List<ConversionJobEntity> children = conversionJobService.getChildJobs(entity.getUuid());
    return children.stream().map(this::buildNodeSummary).collect(Collectors.toList());
  }

  private ConversionNodeSummary buildNodeSummary(ConversionJobEntity entity) {
    List<ConversionNodeSummary> childNodes = null;
    PipelineConversionMetricsDTO pipelineMetricsDTO = null;

    if (entity.getEntityType() == EntityType.PIPELINE || entity.getEntityType() == EntityType.TEMPLATE) {
      List<ConversionJobEntity> children = conversionJobService.getChildJobs(entity.getUuid());
      if (!children.isEmpty()) {
        childNodes = children.stream().map(this::buildNodeSummary).collect(Collectors.toList());
        if (entity.getEntityType() == EntityType.PIPELINE) {
          pipelineMetricsDTO = buildPipelineMetrics(children);
        }
      }
    }

    String versionLabel = entity.getEntityReference() != null ? entity.getEntityReference().getVersionLabel() : null;

    return ConversionNodeSummary.builder()
        .entityIdentifier(entity.getEntityIdentifier())
        .versionLabel(versionLabel)
        .v1Identifier(entity.getV1Identifier())
        .entityType(entity.getEntityType())
        .status(entity.getStatus())
        .errorMessage(entity.getErrorMessage())
        .pipelineMetrics(pipelineMetricsDTO)
        .errors(entity.getConversionErrors())
        .children(childNodes)
        .build();
  }
}

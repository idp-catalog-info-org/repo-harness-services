/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.processor.ApiSpecGitRefresher;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.iterators.config.ApiEndpointRefreshIteratorConfig;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Backstop iterator for OpenAPI endpoint extraction. Catches what the Redis-stream path misses:
 * GitX webhook syncs, FF-flipped-on accounts, transient publish failures, specs that drift without
 * a catalog edit.
 *
 * <p>Each fire fetches one page of never-checked or stale (older than {@code recencyWindow}) API
 * entities, sorted by {@code lastCheckedAt} ASC, and processes up to {@code maxEntitiesPerFire} of
 * them across a fixed pool of {@code parallelism} workers, stopping early on the per-fire deadline.
 * Recency and hash-skip ({@link ApiEndpointProcessor}) short-circuit unchanged content. Per-entity
 * exceptions are isolated so one bad entity does not abort the page.
 *
 * <p>Scheduling is REGULAR over a single trigger doc: exactly one pod runs each fire (atomic claim
 * on {@code nextIteration}), and parallelism is within that pod. This holds only while a fire
 * completes within the interval — see {@link #ACCEPTABLE_EXECUTION_TIME}.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class ApiEndpointRefreshHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  /** Must match the {@code .name} field of the trigger doc in {@code iterators.json}. */
  static final String ITERATOR_NAME = "ApiEndpointRefreshHandler";

  /** Defaults applied when the corresponding config value is omitted or <= 0. */
  static final int DEFAULT_PAGE_SIZE = 1000;
  static final int DEFAULT_MAX_PROCESS_CALLS_PER_FIRE = 1000;
  static final int DEFAULT_PARALLELISM = 5;
  static final long DEFAULT_RECENCY_WINDOW_SECONDS = 21600; // 6 hours

  /**
   * Unbounded accumulated-endpoints blob, excluded from the page fetch to bound per-doc heap. Safe
   * to drop because {@link ApiEndpointProcessor#processEntity} re-reads the entity fresh under lock
   * before saving; do NOT remove that re-read assuming this field is on the page entity.
   */
  private static final String APIS_PATHS_PATH = "decorator._processed_data.metadata.apis.paths";

  /** Wall-time budget per fire. Must stay below the iterator interval to prevent cross-pod overlap. */
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(1200);

  /** Cushion so the submit loop stops before the budget elapses. */
  private static final Duration DEADLINE_SAFETY_MARGIN = ofSeconds(120);

  private static final String LAST_CHECKED_AT_PATH = "decorator._processed_data.metadata.apis.lastCheckedAt";

  private static final String PROCESSED_DATA_KEY = "_processed_data";
  private static final String METADATA_KEY = "metadata";
  private static final String APIS_KEY = "apis";
  private static final String LAST_CHECKED_AT_FIELD = "lastCheckedAt";

  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MongoTemplate mongoTemplate;
  private final ApiEndpointProcessor apiEndpointProcessor;
  private final IdpCommonService idpCommonService;
  private final IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  private final ApiSpecGitRefresher apiSpecGitRefresher;

  /** Set in {@link #registerIterators}; defaults to 6h until then. */
  private volatile Duration recencyWindow = ofSeconds(DEFAULT_RECENCY_WINDOW_SECONDS);

  /** Set in {@link #registerIterators}; defaults until then. */
  private volatile int pageSize = DEFAULT_PAGE_SIZE;
  private volatile int maxProcessCallsPerFire = DEFAULT_MAX_PROCESS_CALLS_PER_FIRE;
  private volatile int parallelism = DEFAULT_PARALLELISM;

  @Inject
  public ApiEndpointRefreshHandler(PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate,
      ApiEndpointProcessor apiEndpointProcessor, IdpCommonService idpCommonService,
      IdpIteratorMetricRecorder idpIteratorMetricRecorder, ApiSpecGitRefresher apiSpecGitRefresher) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
    this.apiEndpointProcessor = apiEndpointProcessor;
    this.idpCommonService = idpCommonService;
    this.idpIteratorMetricRecorder = idpIteratorMetricRecorder;
    this.apiSpecGitRefresher = apiSpecGitRefresher;
  }

  @Override
  public void handle(IteratorEntity entity) {
    log.info("Starting {} iteration: querying up to {} API entities ordered by stale-first", ITERATOR_NAME, pageSize);
    long fireStartMillis = System.currentTimeMillis();
    List<CatalogEntity> page = fetchStaleApiEntitiesPage();
    if (page.isEmpty()) {
      log.info("{}: no API entities to process this fire", ITERATOR_NAME);
      return;
    }

    Map<String, Boolean> ffByAccount = resolveFeatureFlags(distinctApiAccountIds(page));

    long recencyCutoff = fireStartMillis - recencyWindow.toMillis();
    long deadlineMillis = fireStartMillis + ACCEPTABLE_EXECUTION_TIME.toMillis() - DEADLINE_SAFETY_MARGIN.toMillis();

    // attempted and skip counters live on the submitting thread only; processed/errors are bumped
    // by worker threads and so must be atomic.
    int attempted = 0;
    int skippedNoFf = 0;
    int skippedNonApi = 0;
    int skippedRecency = 0;
    AtomicInteger processed = new AtomicInteger();
    AtomicInteger errors = new AtomicInteger();
    boolean capReached = false;
    boolean deadlineReached = false;

    int poolSize = Math.max(1, parallelism);
    ExecutorService executor = Executors.newFixedThreadPool(
        poolSize, new ThreadFactoryBuilder().setNameFormat(ITERATOR_NAME + "-worker-%d").build());
    try {
      for (CatalogEntity ce : page) {
        if (!Constants.API_KIND.equalsIgnoreCase(ce.getKind())) {
          skippedNonApi++;
          continue;
        }
        Boolean ffOn = ffByAccount.get(ce.getAccountIdentifier());
        if (ffOn == null || !ffOn) {
          skippedNoFf++;
          continue;
        }
        Long lastCheckedAt = readLastCheckedAt(ce);
        if (lastCheckedAt != null && lastCheckedAt > recencyCutoff) {
          skippedRecency++;
          continue;
        }
        if (attempted >= maxProcessCallsPerFire) {
          capReached = true;
          break;
        }
        if (System.currentTimeMillis() >= deadlineMillis) {
          deadlineReached = true;
          break;
        }
        attempted++;
        executor.submit(() -> processOne(ce, processed, errors));
      }
    } finally {
      executor.shutdown();
      awaitCompletion(executor, fireStartMillis);
    }

    log.info("{} fire complete: attempted={} processed={} skippedRecency={} skippedNoFf={} skippedNonApi={} "
            + "errors={} pageSize={} parallelism={} capReached={} deadlineReached={}",
        ITERATOR_NAME, attempted, processed.get(), skippedRecency, skippedNoFf, skippedNonApi, errors.get(),
        page.size(), poolSize, capReached, deadlineReached);
  }

  /**
   * Distinct account ids of API-kind entities in the page. FF is resolved once per account (not per
   * entity), and non-API pages never touch the FF service.
   */
  private Set<String> distinctApiAccountIds(List<CatalogEntity> page) {
    Set<String> accountIds = new HashSet<>();
    for (CatalogEntity ce : page) {
      if (Constants.API_KIND.equalsIgnoreCase(ce.getKind()) && ce.getAccountIdentifier() != null) {
        accountIds.add(ce.getAccountIdentifier());
      }
    }
    return accountIds;
  }

  private void processOne(CatalogEntity ce, AtomicInteger processed, AtomicInteger errors) {
    try {
      refreshGitPlaceholderSpec(ce);
      ProcessingOutcome outcome = apiEndpointProcessor.processEntity(ce);
      log.info("{} outcome for entityRef {} (account {}): status={} oldKeys={} newKeys={} warnings={}", ITERATOR_NAME,
          ce.getQueryableEntityRef(), ce.getAccountIdentifier(), outcome.getStatus(), outcome.getOldKeys().size(),
          outcome.getNewKeys().size(), outcome.getWarnings().size());
      idpIteratorMetricRecorder.recordSuccess(ITERATOR_NAME, ce.getAccountIdentifier());
      processed.incrementAndGet();
    } catch (Exception ex) {
      errors.incrementAndGet();
      idpIteratorMetricRecorder.recordFailure(ITERATOR_NAME, ce.getAccountIdentifier());
      log.warn("{}: error processing entity {} in account {}: {}", ITERATOR_NAME, ce.getIdentifier(),
          ce.getAccountIdentifier(), ex.getMessage(), ex);
    }
  }

  /**
   * Waits for in-flight tasks, bounded so total fire wall-time never exceeds the framework budget
   * (which must stay below the iterator interval, else another pod re-claims and overlaps). On
   * timeout the remaining tasks are cancelled; finished work is durable and the rest drains next
   * fire.
   */
  private void awaitCompletion(ExecutorService executor, long fireStartMillis) {
    long remainingMillis = fireStartMillis + ACCEPTABLE_EXECUTION_TIME.toMillis() - System.currentTimeMillis();
    try {
      if (remainingMillis <= 0 || !executor.awaitTermination(remainingMillis, TimeUnit.MILLISECONDS)) {
        log.warn("{}: workers exceeded the {}s fire budget; cancelling remaining tasks", ITERATOR_NAME,
            ACCEPTABLE_EXECUTION_TIME.getSeconds());
        executor.shutdownNow();
      }
    } catch (InterruptedException ie) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  static Long readLastCheckedAt(CatalogEntity entity) {
    Map<String, Object> decorator = entity.getDecorator();
    if (decorator == null) {
      return null;
    }
    Object processed = decorator.get(PROCESSED_DATA_KEY);
    if (!(processed instanceof Map)) {
      return null;
    }
    Object metadata = ((Map<?, ?>) processed).get(METADATA_KEY);
    if (!(metadata instanceof Map)) {
      return null;
    }
    Object apis = ((Map<?, ?>) metadata).get(APIS_KEY);
    if (!(apis instanceof Map)) {
      return null;
    }
    Object value = ((Map<?, ?>) apis).get(LAST_CHECKED_AT_FIELD);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return null;
  }

  /**
   * Re-fetches Git placeholder content into the decorator (same path as entity create/update),
   * via the shared {@link ApiSpecGitRefresher}. Uses an IDP service principal because no
   * request-scoped principal exists in the iterator. Fetch failures are swallowed by the
   * refresher ({@code propagateErrors=false}) so the last-good decorator is kept.
   */
  private void refreshGitPlaceholderSpec(CatalogEntity entity) {
    if (!ApiSpecGitRefresher.hasGitPlaceholderDefinition(entity)) {
      return;
    }
    Principal previousPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    try {
      apiSpecGitRefresher.refresh(entity, false);
    } finally {
      SourcePrincipalContextBuilder.setSourcePrincipal(previousPrincipal);
    }
  }

  /**
   * Fetches never-checked or stale-enough API entities, sorted by {@code lastCheckedAt} ASC.
   * The {@code $or} branches are served by the {@code (kind, lastCheckedAt)} compound index; the
   * {@code $lt} branch is a bounded range scan and the {@code $exists:false} branch matches the
   * head of the index. Avoids the 32MB in-memory sort cap.
   */
  protected List<CatalogEntity> fetchStaleApiEntitiesPage() {
    long cutoffMillis = System.currentTimeMillis() - recencyWindow.toMillis();
    Criteria criteria = new Criteria().andOperator(where(CatalogEntity.CatalogKeys.kind).is(Constants.API_KIND),
        new Criteria().orOperator(
            where(LAST_CHECKED_AT_PATH).exists(false), where(LAST_CHECKED_AT_PATH).lt(cutoffMillis)));
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, LAST_CHECKED_AT_PATH)).limit(pageSize);
    // Drop only the unbounded accumulated-endpoints blob; every other field (including _class for
    // subtype resolution, git-reference fields, spec, yaml, decorator.spec, lastCheckedAt) is kept.
    query.fields().exclude(APIS_PATHS_PATH);
    return mongoTemplate.find(query, CatalogEntity.class);
  }

  /** FF failure is treated as "off" for that account only, so a hiccup never blocks the page. */
  protected Map<String, Boolean> resolveFeatureFlags(Set<String> accountIds) {
    Map<String, Boolean> result = new HashMap<>();
    for (String accountId : accountIds) {
      try {
        result.put(accountId, idpCommonService.idpApiEndpointExtractionEnabled(accountId));
      } catch (Exception ex) {
        log.warn("{}: failed to resolve IDP_API_ENDPOINT_EXTRACTION FF for account {} — treating as off. Error = {}",
            ITERATOR_NAME, accountId, ex.getMessage());
        result.put(accountId, false);
      }
    }
    return result;
  }

  public void registerIterators(ApiEndpointRefreshIteratorConfig config) {
    long recencySeconds =
        config.getRecencyWindowInSeconds() > 0 ? config.getRecencyWindowInSeconds() : DEFAULT_RECENCY_WINDOW_SECONDS;
    this.recencyWindow = ofSeconds(recencySeconds);
    this.pageSize = config.getPageSize() > 0 ? config.getPageSize() : DEFAULT_PAGE_SIZE;
    this.maxProcessCallsPerFire =
        config.getMaxEntitiesPerFire() > 0 ? config.getMaxEntitiesPerFire() : DEFAULT_MAX_PROCESS_CALLS_PER_FIRE;
    this.parallelism = config.getParallelism() > 0 ? config.getParallelism() : DEFAULT_PARALLELISM;
    log.info("Registering {} with targetInterval={}s, recencyWindow={}s, pageSize={}, maxEntitiesPerFire={}, "
            + "parallelism={}",
        ITERATOR_NAME, config.getTargetIntervalInSeconds(), recencySeconds, pageSize, maxProcessCallsPerFire,
        parallelism);
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(ITERATOR_NAME)
            .poolSize(1)
            .interval(ofSeconds(15))
            .build(),
        ApiEndpointRefreshHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(ITERATOR_NAME)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(config.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}

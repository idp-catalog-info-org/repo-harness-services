/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor.api;

import static io.harness.idp.common.Constants.PROCESSED_DATA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.processor.api.EndpointExtractor.ExtractionResult;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Extracts OpenAPI endpoints into {@code metadata.apis} on an API entity's decorator. Load-bearing
 * invariants: yaml is never modified, direct repository save (no publish→extract loop), wholesale
 * replace of {@code metadata.apis}, {@code specHash} from spec bytes only, hash-skip on unchanged
 * content, score recompute only on endpoint key-set changes.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointProcessor {
  private static final String LOCK_PREFIX = "API_ENDPOINT_PROCESSOR_";
  private static final long LOCK_TIMEOUT_MINUTES = 5;

  private static final String METADATA_KEY = "metadata";
  private static final String APIS_KEY = "apis";
  private static final String PATHS_KEY = "paths";
  private static final String ENRICHMENTS_KEY = "enrichments";
  private static final String SPEC_KEY = "spec";

  private final SpecSourceResolver specSourceResolver;
  private final OpenApiSpecParser openApiSpecParser;
  private final EndpointExtractor endpointExtractor;
  private final Swagger2EndpointExtractor swagger2EndpointExtractor;
  private final CatalogEntityRepository catalogEntityRepository;
  private final ResourceLocker resourceLocker;
  private final CatalogServiceHelper catalogServiceHelper;

  @Inject
  public ApiEndpointProcessor(SpecSourceResolver specSourceResolver, OpenApiSpecParser openApiSpecParser,
      EndpointExtractor endpointExtractor, Swagger2EndpointExtractor swagger2EndpointExtractor,
      CatalogEntityRepository catalogEntityRepository, ResourceLocker resourceLocker,
      CatalogServiceHelper catalogServiceHelper) {
    this.specSourceResolver = specSourceResolver;
    this.openApiSpecParser = openApiSpecParser;
    this.endpointExtractor = endpointExtractor;
    this.swagger2EndpointExtractor = swagger2EndpointExtractor;
    this.catalogEntityRepository = catalogEntityRepository;
    this.resourceLocker = resourceLocker;
    this.catalogServiceHelper = catalogServiceHelper;
  }

  public ProcessingOutcome processEntity(CatalogEntity entity) {
    Objects.requireNonNull(entity, "entity must not be null");
    String lockName = lockNameFor(entity);

    AcquiredLock<?> lock = null;
    try {
      lock = resourceLocker.acquireLock(lockName, LOCK_TIMEOUT_MINUTES);
      if (lock == null) {
        log.info("Skipping API endpoint extraction for entity {} — lock held by another worker; "
                + "will retry on next delivery.",
            safeId(entity));
        return ProcessingOutcome.lockSkipped();
      }
      CatalogEntity fresh = null;
      try {
        // Re-read under lock so we don't save a stale snapshot over concurrent CCP enrichments.
        fresh = reloadFreshEntity(entity);
        if (fresh == null) {
          log.info("Skipping API endpoint extraction for entity {} — entity no longer exists.", safeId(entity));
          return ProcessingOutcome.failure("Entity no longer exists");
        }
        // Preserve caller's refreshed decorator.spec (e.g. Git placeholder) on the fresh document.
        transferSpecDecorator(entity, fresh);
        return runUnderLock(fresh);
      } catch (RuntimeException ex) {
        // Convert to FAILED — otherwise consumer NACKs (poison message) and iterator re-churns.
        log.error(
            "Unexpected error during API endpoint extraction for entity {}: {}", safeId(entity), ex.getMessage(), ex);
        try {
          return persistFailure(fresh != null ? fresh : entity, "Unexpected processor error: " + ex.getMessage());
        } catch (RuntimeException persistEx) {
          log.error("Failed to persist FAILED status for entity {} after processor error: {}", safeId(entity),
              persistEx.getMessage(), persistEx);
          return ProcessingOutcome.failure("Unexpected processor error: " + ex.getMessage());
        }
      }
    } finally {
      if (lock != null) {
        resourceLocker.releaseLock(lock);
      }
    }
  }

  /** Loads the entity fresh from MongoDB; returns {@code null} if deleted meanwhile. */
  private CatalogEntity reloadFreshEntity(CatalogEntity stale) {
    try {
      return catalogEntityRepository
          .findByParentUniqueIdAndKindAndIdentifier(stale.getParentUniqueId(), stale.getKind(), stale.getIdentifier())
          .orElse(null);
    } catch (Exception ex) {
      log.warn(
          "Failed to re-read entity {} under lock for API endpoint extraction: {}", safeId(stale), ex.getMessage());
      return null;
    }
  }

  /** Copies {@code decorator.spec} from {@code from} onto {@code to} when present. */
  @SuppressWarnings("unchecked")
  static void transferSpecDecorator(CatalogEntity from, CatalogEntity to) {
    if (from == null || to == null || from == to) {
      return;
    }
    Map<String, Object> fromDecorator = from.getDecorator();
    if (fromDecorator == null) {
      return;
    }
    Object spec = fromDecorator.get(SPEC_KEY);
    if (!(spec instanceof Map)) {
      return;
    }
    Map<String, Object> toDecorator = to.getDecorator() == null ? new HashMap<>() : new HashMap<>(to.getDecorator());
    toDecorator.put(SPEC_KEY, new LinkedHashMap<>((Map<String, Object>) spec));
    to.setDecorator(toDecorator);
  }

  private ProcessingOutcome runUnderLock(CatalogEntity entity) {
    List<String> oldKeys = snapshotEndpointKeys(entity);

    String resolvedContent;
    try {
      resolvedContent = specSourceResolver.resolve(entity);
    } catch (SpecResolutionException | SpecFetchException ex) {
      log.warn("Spec resolution failed for entity {}: {}", safeId(entity), ex.getMessage());
      return persistFailure(entity, ex.getMessage());
    }

    // Hash-skip loop-breaker: only skips when the prior extraction was success/partial.
    String newHash = SpecHasher.hash(resolvedContent);
    if (isUnchangedSpec(entity, newHash)) {
      log.debug("Spec unchanged for entity {} (hash {}); stamping lastCheckedAt only.", safeId(entity), newHash);
      stampLastCheckedAt(entity);
      catalogEntityRepository.save(entity);
      return ProcessingOutcome.hashSkipped(oldKeys, readExistingWarnings(entity));
    }

    ExtractionResult extraction;
    try {
      if (isSwaggerV2(resolvedContent)) {
        extraction = swagger2EndpointExtractor.extract(resolvedContent);
      } else {
        OpenAPI parsed = openApiSpecParser.parse(resolvedContent);
        extraction = endpointExtractor.extract(parsed);
      }
    } catch (OpenApiParseException ex) {
      log.warn("Spec parse failed for entity {}: {}", safeId(entity), ex.getMessage());
      return persistFailure(entity, ex.getMessage());
    }

    Map<String, Map<String, Object>> newPaths = (Map) extraction.getApis().get(PATHS_KEY);
    Map<String, Map<String, Object>> oldPaths = readExistingPaths(entity);
    Map<String, Map<String, Object>> mergedPaths = mergePaths(oldPaths, newPaths);

    // "partial" is reserved for genuine degradation (e.g. unresolved server template vars);
    // benign warnings (truncation, asymmetric basePaths) stay in extractionWarnings.
    long now = System.currentTimeMillis();
    Map<String, Object> apis = new LinkedHashMap<>(extraction.getApis());
    apis.put(PATHS_KEY, mergedPaths);
    apis.put("specHash", newHash);
    apis.put("extractedAt", now);
    apis.put("lastCheckedAt", now);
    apis.put("extractionStatus", extraction.isDegraded() ? "partial" : "success");
    if (!extraction.getWarnings().isEmpty()) {
      apis.put("extractionWarnings", new ArrayList<>(extraction.getWarnings()));
    } else {
      apis.remove("extractionWarnings");
    }
    apis.remove("lastError");

    writeApisToDecorator(entity, apis);
    // Direct repo save — DO NOT use catalogService.updateEntity() (publish→extract loop).
    catalogEntityRepository.save(entity);

    List<String> newKeys = new ArrayList<>(mergedPaths.keySet());

    // Score recompute fires only on key-set changes; nothing else covers it since we save directly.
    if (keySetChanged(oldKeys, newKeys)) {
      try {
        catalogServiceHelper.publishAsyncComputationEvent(
            safeAccountId(entity), null, CatalogUtils.getEntityUUId(entity));
      } catch (Exception ex) {
        log.warn("Failed to publish async score recompute for entity {} after key-set change: {}", safeId(entity),
            ex.getMessage());
      }
    }

    return ProcessingOutcome.success(
        extraction.isDegraded(), oldKeys, newKeys, new ArrayList<>(extraction.getWarnings()));
  }

  static boolean keySetChanged(List<String> oldKeys, List<String> newKeys) {
    Set<String> oldSet = oldKeys == null ? Collections.emptySet() : new HashSet<>(oldKeys);
    Set<String> newSet = newKeys == null ? Collections.emptySet() : new HashSet<>(newKeys);
    return !oldSet.equals(newSet);
  }

  /** Persists a failure without touching previously-extracted paths or the yaml field. */
  private ProcessingOutcome persistFailure(CatalogEntity entity, String errorMessage) {
    Map<String, Object> existingApis = readExistingApis(entity);
    Map<String, Object> apis = existingApis == null ? new LinkedHashMap<>() : existingApis;
    apis.put("extractionStatus", "failed");
    apis.put("lastError", errorMessage);
    apis.put("lastCheckedAt", System.currentTimeMillis());

    writeApisToDecorator(entity, apis);
    catalogEntityRepository.save(entity);

    return ProcessingOutcome.failure(errorMessage);
  }

  /** Carries {@code enrichments} forward from old paths; drops keys not in the new spec. */
  @SuppressWarnings("unchecked")
  static Map<String, Map<String, Object>> mergePaths(
      Map<String, Map<String, Object>> oldPaths, Map<String, Map<String, Object>> newPaths) {
    Map<String, Map<String, Object>> result = new java.util.TreeMap<>();
    if (newPaths == null) {
      return result;
    }
    Map<String, Map<String, Object>> safeOld = oldPaths == null ? Collections.emptyMap() : oldPaths;
    for (Map.Entry<String, Map<String, Object>> entry : newPaths.entrySet()) {
      String key = entry.getKey();
      Map<String, Object> freshEndpoint = new LinkedHashMap<>(entry.getValue());
      Object previousRaw = safeOld.get(key);
      if (previousRaw instanceof Map) {
        Map<String, Object> previous = (Map<String, Object>) previousRaw;
        Object preservedEnrichments = previous.get(ENRICHMENTS_KEY);
        if (preservedEnrichments instanceof Map) {
          freshEndpoint.put(ENRICHMENTS_KEY, new LinkedHashMap<>((Map<String, Object>) preservedEnrichments));
        }
      }
      result.put(key, freshEndpoint);
    }
    return result;
  }

  static void writeApisToDecorator(CatalogEntity entity, Map<String, Object> apis) {
    Map<String, Object> decorator =
        entity.getDecorator() == null ? new HashMap<>() : new HashMap<>(entity.getDecorator());

    @SuppressWarnings("unchecked")
    Map<String, Object> processedData = decorator.get(PROCESSED_DATA) instanceof Map
        ? new LinkedHashMap<>((Map<String, Object>) decorator.get(PROCESSED_DATA))
        : new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = processedData.get(METADATA_KEY) instanceof Map
        ? new LinkedHashMap<>((Map<String, Object>) processedData.get(METADATA_KEY))
        : new LinkedHashMap<>();

    metadata.put(APIS_KEY, apis);
    processedData.put(METADATA_KEY, metadata);
    decorator.put(PROCESSED_DATA, processedData);
    entity.setDecorator(decorator);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> readExistingPaths(CatalogEntity entity) {
    Map<String, Object> apis = readExistingApis(entity);
    if (apis == null) {
      return Collections.emptyMap();
    }
    Object paths = apis.get(PATHS_KEY);
    if (paths instanceof Map) {
      return (Map<String, Map<String, Object>>) paths;
    }
    return Collections.emptyMap();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readExistingApis(CatalogEntity entity) {
    if (entity.getDecorator() == null) {
      return null;
    }
    Object processed = entity.getDecorator().get(PROCESSED_DATA);
    if (!(processed instanceof Map)) {
      return null;
    }
    Object metadata = ((Map<String, Object>) processed).get(METADATA_KEY);
    if (!(metadata instanceof Map)) {
      return null;
    }
    Object apis = ((Map<String, Object>) metadata).get(APIS_KEY);
    if (!(apis instanceof Map)) {
      return null;
    }
    return (Map<String, Object>) apis;
  }

  private static List<String> snapshotEndpointKeys(CatalogEntity entity) {
    Map<String, Map<String, Object>> existing = readExistingPaths(entity);
    return new ArrayList<>(existing.keySet());
  }

  /** Previously-persisted extraction warnings (e.g. from a prior partial extraction), if any. */
  @SuppressWarnings("unchecked")
  private static List<String> readExistingWarnings(CatalogEntity entity) {
    Map<String, Object> apis = readExistingApis(entity);
    if (apis == null) {
      return Collections.emptyList();
    }
    Object warnings = apis.get("extractionWarnings");
    if (warnings instanceof List) {
      return new ArrayList<>((List<String>) warnings);
    }
    return Collections.emptyList();
  }

  private static boolean isUnchangedSpec(CatalogEntity entity, String newHash) {
    Map<String, Object> existing = readExistingApis(entity);
    if (existing == null) {
      return false;
    }
    Object prevStatus = existing.get("extractionStatus");
    if (!"success".equals(prevStatus) && !"partial".equals(prevStatus)) {
      return false;
    }
    Object prevHash = existing.get("specHash");
    return prevHash instanceof String && prevHash.equals(newHash);
  }

  private static void stampLastCheckedAt(CatalogEntity entity) {
    Map<String, Object> existing = readExistingApis(entity);
    Map<String, Object> apis = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    apis.put("lastCheckedAt", System.currentTimeMillis());
    writeApisToDecorator(entity, apis);
  }

  private static String safeId(CatalogEntity entity) {
    return Optional.ofNullable(entity.getId()).orElse("unknown");
  }

  private static String safeAccountId(CatalogEntity entity) {
    return Optional.ofNullable(entity.getAccountIdentifier()).orElse("unknown");
  }

  /** Shared lock key between processor and CCP writers; both MUST acquire to avoid lost updates. */
  public static String lockNameFor(CatalogEntity entity) {
    return LOCK_PREFIX + safeAccountId(entity) + "_" + safeId(entity);
  }

  public static final class ProcessingOutcome {
    public enum Status { SUCCESS, PARTIAL, FAILED, LOCK_SKIPPED, HASH_SKIPPED }

    private final Status status;
    private final String errorMessage;
    private final List<String> oldKeys;
    private final List<String> newKeys;
    private final List<String> warnings;

    private ProcessingOutcome(
        Status status, String errorMessage, List<String> oldKeys, List<String> newKeys, List<String> warnings) {
      this.status = status;
      this.errorMessage = errorMessage;
      this.oldKeys = oldKeys;
      this.newKeys = newKeys;
      this.warnings = warnings;
    }

    public static ProcessingOutcome success(
        boolean degraded, List<String> oldKeys, List<String> newKeys, List<String> warnings) {
      return new ProcessingOutcome(degraded ? Status.PARTIAL : Status.SUCCESS, null, oldKeys, newKeys, warnings);
    }

    public static ProcessingOutcome failure(String errorMessage) {
      return new ProcessingOutcome(
          Status.FAILED, errorMessage, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static ProcessingOutcome lockSkipped() {
      return new ProcessingOutcome(
          Status.LOCK_SKIPPED, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Spec byte-identical to the prior extraction; no re-parse/re-write. {@code newKeys} is empty.
     * {@code warnings} carries the previously-persisted extraction warnings so callers can still
     * surface a degraded/partial state even though this run made no change.
     */
    public static ProcessingOutcome hashSkipped(List<String> oldKeys, List<String> warnings) {
      return new ProcessingOutcome(Status.HASH_SKIPPED, null, oldKeys, Collections.emptyList(), warnings);
    }

    public Status getStatus() {
      return status;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public List<String> getOldKeys() {
      return oldKeys;
    }

    public List<String> getNewKeys() {
      return newKeys;
    }

    public List<String> getWarnings() {
      return warnings;
    }
  }

  @SuppressWarnings("unchecked")
  static boolean isSwaggerV2(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    try {
      Object loaded = new Yaml().load(content);
      if (!(loaded instanceof Map)) {
        return false;
      }
      Object swagger = ((Map<String, Object>) loaded).get("swagger");
      return swagger != null && swagger.toString().trim().startsWith("2");
    } catch (Exception ex) {
      return false;
    }
  }
}

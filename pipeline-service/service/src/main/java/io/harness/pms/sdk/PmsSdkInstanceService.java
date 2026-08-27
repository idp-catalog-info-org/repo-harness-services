/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.pms.sdk.SdkStepHelper.SDK_STEP_SET_NAME;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.datastructures.EphemeralCacheService;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.contracts.plan.InitializeSdkRequest;
import io.harness.pms.contracts.plan.InitializeSdkResponse;
import io.harness.pms.contracts.plan.PmsServiceGrpc.PmsServiceImplBase;
import io.harness.pms.contracts.plan.Types;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.exception.InitializeSdkException;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.repositories.sdk.PmsSdkInstanceRepository;
import io.harness.springdata.TransactionHelper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class PmsSdkInstanceService extends PmsServiceImplBase {
  private static final String LOCK_NAME_PREFIX = "PmsSdkInstanceService-";
  private final PmsSdkInstanceRepository pmsSdkInstanceRepository;
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate secondaryMongoTemplate;
  private final PersistentLocker persistentLocker;
  private final SchemaFetcher schemaFetcher;
  private final Cache<String, PmsSdkInstance> instanceCache;
  TransactionHelper transactionHelper;
  public boolean shouldUseInstanceCache;
  private final boolean skipSdkMongoRegistration;
  private final EphemeralCacheService ephemeralCacheService;

  @Inject
  public PmsSdkInstanceService(PmsSdkInstanceRepository pmsSdkInstanceRepository, MongoTemplate mongoTemplate,
      PersistentLocker persistentLocker, SchemaFetcher schemaFetcher,
      @Named("shouldUseInstanceCache") boolean shouldUseInstanceCache,
      @Named("skipSdkMongoRegistration") boolean skipSdkMongoRegistration, TransactionHelper transactionHelper,
      EphemeralCacheService ephemeralCacheService, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.pmsSdkInstanceRepository = pmsSdkInstanceRepository;
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.schemaFetcher = schemaFetcher;
    this.shouldUseInstanceCache = shouldUseInstanceCache;
    this.skipSdkMongoRegistration = skipSdkMongoRegistration;
    this.transactionHelper = transactionHelper;
    this.ephemeralCacheService = ephemeralCacheService;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.instanceCache = Caffeine.newBuilder().maximumSize(30).expireAfterWrite(Duration.ofMinutes(5)).build();
  }

  @VisibleForTesting
  Cache<String, PmsSdkInstance> getInstanceCache() {
    return instanceCache;
  }

  @Override
  public void initializeSdk(InitializeSdkRequest request, StreamObserver<InitializeSdkResponse> responseObserver) {
    if (EmptyPredicate.isEmpty(request.getName())) {
      throw new InvalidRequestException("Name is empty");
    }

    if (skipSdkMongoRegistration) {
      log.info("Skipping PMS SDK Mongo registration for service: {}", request.getName());
      responseObserver.onNext(InitializeSdkResponse.newBuilder().build());
      responseObserver.onCompleted();
      return;
    }

    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
             LOCK_NAME_PREFIX + request.getName(), Duration.ofMinutes(1), Duration.ofMinutes(2))) {
      if (lock == null) {
        throw new InitializeSdkException("Could not acquire lock");
      }
      saveSdkInstance(request);
      ephemeralCacheService.getDistributedSet(SDK_STEP_SET_NAME).clear();
    } catch (Exception ex) {
      log.error(String.format("Exception occurred while registering sdk with name: [%s]", request.getName()), ex);
      throw new InitializeSdkException(ex.getMessage());
    }
    responseObserver.onNext(InitializeSdkResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  protected void saveSdkInstance(InitializeSdkRequest request) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(request.getSupportedTypesMap())) {
      for (Map.Entry<String, Types> entry : request.getSupportedTypesMap().entrySet()) {
        if (EmptyPredicate.isEmpty(entry.getKey()) || EmptyPredicate.isEmpty(entry.getValue().getTypesList())) {
          continue;
        }
        supportedTypes.put(entry.getKey(),
            entry.getValue().getTypesList().stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toSet()));
      }
    }

    Query query = query(where(PmsSdkInstanceKeys.name).is(request.getName()));

    // New remote functors should not be registered in sdk.
    // Instead, they must be registered in PipelineService.
    // Migration of these functors will be addressed as part of the epic - PIPE-23893
    ensureNewFunctorsAreRegisteredOnlyInPipeline(request, query);

    boolean active = !request.getInactive();
    Update update =
        update(PmsSdkInstanceKeys.supportedTypes, supportedTypes)
            .set(PmsSdkInstanceKeys.supportedSdkSteps, request.getSupportedStepsList())
            .set(PmsSdkInstanceKeys.interruptConsumerConfig, request.getInterruptConsumerConfig())
            .set(PmsSdkInstanceKeys.staticAliases, request.getStaticAliasesMap())
            .set(PmsSdkInstanceKeys.staticAliasesUnified, request.getStaticAliasesUnifiedMap())
            .set(PmsSdkInstanceKeys.sdkFunctors, request.getSdkFunctorsList())
            .set(PmsSdkInstanceKeys.jsonExpansionInfo, request.getJsonExpansionInfoList())
            .set(PmsSdkInstanceKeys.inputsMetadataInfo, request.getInputsMetadataInfoList())
            .set(PmsSdkInstanceKeys.orchestrationEventConsumerConfig, request.getOrchestrationEventConsumerConfig())
            .set(PmsSdkInstanceKeys.active, active)
            .set(PmsSdkInstanceKeys.sdkModuleInfo, request.getSdkModuleInfo())
            .set(PmsSdkInstanceKeys.lastUpdatedAt, System.currentTimeMillis())
            .set(PmsSdkInstanceKeys.facilitatorEventConsumerConfig, request.getFacilitatorEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeStartEventConsumerConfig, request.getNodeStartEventConsumerConfig())
            .set(PmsSdkInstanceKeys.progressEventConsumerConfig, request.getProgressEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeAdviseEventConsumerConfig, request.getNodeAdviseEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeResumeEventConsumerConfig, request.getNodeResumeEventConsumerConfig())
            .set(PmsSdkInstanceKeys.startPlanCreationEventConsumerConfig, request.getPlanCreationEventConsumerConfig())
            .set(PmsSdkInstanceKeys.backfillOrchestrationEventConsumerConfig,
                request.getBackfillOrchestrationEventConsumerConfig())
            .set(PmsSdkInstanceKeys.modulePath, request.getModulePath());
    transactionHelper.performTransaction(() -> {
      PmsSdkInstance instance = mongoTemplate.findAndModify(
          query, update, new FindAndModifyOptions().upsert(true).returnNew(true), PmsSdkInstance.class);
      if (instance == null) {
        throw new InitializeSdkException(
            String.format("Update for PmsSdkInstance for module: [%s] Failed", request.getName()));
      }
      if (shouldUseInstanceCache) {
        log.info("Updating sdkInstanceCache for module {}", request.getName());
        instanceCache.put(request.getName(), instance);
        log.info("Updated sdkInstanceCache for module {}", request.getName());
      }
      return instance;
    });
  }

  @VisibleForTesting
  void ensureNewFunctorsAreRegisteredOnlyInPipeline(InitializeSdkRequest request, Query query) {
    // fetching db registered pmsSdkInstances
    PmsSdkInstance pmsSdkInstances = mongoTemplate.findOne(query, PmsSdkInstance.class);

    if (null != pmsSdkInstances) {
      List<String> dbRegisteredSdkFunctors = pmsSdkInstances.getSdkFunctors();
      ProtocolStringList sdkFunctors = request.getSdkFunctorsList();
      List<String> missingFunctors = new ArrayList<>();

      for (String sdkFunctor : sdkFunctors) {
        if (!dbRegisteredSdkFunctors.contains(sdkFunctor)) {
          missingFunctors.add(sdkFunctor);
        }
      }
      if (!missingFunctors.isEmpty()) {
        String message = "New functors detected that are not registered in the allowed list. "
            + "Ensure new functors are registered in PipelineService only. Unexpected functors in sdk: "
            + request.getName() + String.join(", ", missingFunctors);
        throw new InitializeSdkException(message);
      }
    }
  }

  public Map<String, Map<String, Set<String>>> getInstanceNameToSupportedTypes() {
    Map<String, Map<String, Set<String>>> instances = new HashMap<>();
    Map<String, PmsSdkInstance> cacheValueMap = getSdkInstanceCacheValue();
    for (Map.Entry<String, PmsSdkInstance> entry : cacheValueMap.entrySet()) {
      instances.put(entry.getKey(), entry.getValue().getSupportedTypes());
    }
    return instances;
  }

  public Map<String, StepPalleteInfo> getModuleNameToStepPalleteInfo() {
    Map<String, StepPalleteInfo> instances = new HashMap<>();
    Map<String, PmsSdkInstance> cacheValueMap = getSdkInstanceCacheValue();
    for (Map.Entry<String, PmsSdkInstance> entry : cacheValueMap.entrySet()) {
      List<StepInfo> stepTypes = new ArrayList<>();
      for (SdkStep sdkStep : entry.getValue().getSupportedSdkSteps()) {
        if (!sdkStep.getIsPartOfStepPallete()) {
          continue;
        }
        stepTypes.add(sdkStep.getStepInfo());
      }
      instances.put(entry.getKey(),
          StepPalleteInfo.builder()
              .moduleName(entry.getValue().getSdkModuleInfo().getDisplayName())
              .stepTypes(stepTypes)
              .build());
    }
    return instances;
  }

  public Map<String, Set<SdkStep>> getSdkSteps() {
    Map<String, PmsSdkInstance> sdkInstanceCacheValues = getSdkInstanceCacheValue();
    Map<String, Set<SdkStep>> cachedSdkSteps = new HashMap<>();
    for (String key : sdkInstanceCacheValues.keySet()) {
      cachedSdkSteps.put(key, new HashSet<>(sdkInstanceCacheValues.get(key).getSupportedSdkSteps()));
    }
    return cachedSdkSteps;
  }

  public Map<String, PmsSdkInstance> getSdkInstanceCacheValue() {
    if (!shouldUseInstanceCache) {
      Map<String, PmsSdkInstance> sdkSteps = new HashMap<>();
      pmsSdkInstanceRepository.findByActive(true).forEach(instance -> { sdkSteps.put(instance.getName(), instance); });
      return sdkSteps;
    }
    Map<String, PmsSdkInstance> cached = instanceCache.asMap();
    if (!cached.isEmpty()) {
      return new HashMap<>(cached);
    }
    // Cache miss — load from DB and populate cache
    Map<String, PmsSdkInstance> sdkSteps = new HashMap<>();
    pmsSdkInstanceRepository.findByActive(true).forEach(instance -> {
      sdkSteps.put(instance.getName(), instance);
      instanceCache.put(instance.getName(), instance);
    });
    return sdkSteps;
  }

  public Set<String> getActiveInstanceNames() {
    if (!shouldUseInstanceCache) {
      Set<String> instanceNames = new HashSet<>();
      pmsSdkInstanceRepository.findByActive(true).forEach(instance -> instanceNames.add(instance.getName()));
      return instanceNames;
    }
    return new HashSet<>(getSdkInstanceCacheValue().keySet());
  }

  public List<PmsSdkInstance> getActiveInstances() {
    return new ArrayList<>(getSdkInstanceCacheValue().values());
  }

  public List<PmsSdkInstance> getActiveInstancesFromDB() {
    return new ArrayList<>(pmsSdkInstanceRepository.findByActive(true));
  }

  public Map<String, PmsSdkInstance> getActiveSdkInstanceMapFromSecondary(List<String> includeFields) {
    Criteria criteria = Criteria.where(PmsSdkInstanceKeys.active).is(true);
    Query query = new Query(criteria);
    for (String field : includeFields) {
      query.fields().include(field);
    }
    query.fields().include(PmsSdkInstanceKeys.name);
    Map<String, PmsSdkInstance> sdkModulePaths = new HashMap<>();
    secondaryMongoTemplate.find(query, PmsSdkInstance.class)
        .forEach(instance -> sdkModulePaths.put(instance.getName(), instance));
    return sdkModulePaths;
  }
}

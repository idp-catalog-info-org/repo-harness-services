/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PARENT_UNIQUE_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorsTestRegisterResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.ManagedExecutorService;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.setupusage.PipelineSetupUsageHelper;
import io.harness.pms.preflight.PreFlightCause;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.connector.ConnectorCheckResponse.ConnectorCheckResponseKeys;
import io.harness.pms.preflight.connector.handler.ConnectorPreflightHandler;
import io.harness.pms.preflight.dto.PreFlightDTO;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.entity.PreFlightEntity.PreFlightEntityKeys;
import io.harness.pms.preflight.handler.AsyncPreFlightHandler;
import io.harness.pms.preflight.inputset.PipelineInputResponse;
import io.harness.pms.preflight.mapper.PreFlightMapper;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.preflight.PreFlightRepository;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PreflightServiceImpl implements PreflightService {
  private static final String PREFLIGHT_EVENT_NAME = "ng_preflight_execution";
  private final ExecutorService executorService = new ManagedExecutorService(Executors.newFixedThreadPool(1));
  @Inject @Named("preflightConnectorTimeoutSeconds") int CONNECTORS_TEST_TIMEOUT_SECONDS;
  @Inject PreFlightRepository preFlightRepository;
  @Inject ConnectorPreflightHandler connectorPreflightHandler;
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Inject PipelineRbacService pipelineRbacServiceImpl;
  @Inject PipelineTelemetryHelper pipelineTelemetryHelper;
  @Inject PmsFeatureFlagService featureFlagService;
  @Inject AsyncWaitEngine asyncWaitEngine;
  @Inject ScopeResolutionHelper scopeResolutionHelper;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject AccessControlClient accessControlClient;

  @Override
  public String startPreflightCheck(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String inputSetPipelineYaml,
      ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    scopeInfo =
        scopeInfo != null ? scopeInfo : scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
    sendPreflightTelemetryEvent(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        scopeInfo != null ? scopeInfo.getUniqueId() : null);
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.getPipeline(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, false, false, false, false, scopeInfo, isParentIdQueryingEnabled);
    if (pipelineEntity.isEmpty()) {
      throw new InvalidRequestException(format("The given pipeline id [%s] does not exist", pipelineIdentifier));
    }
    String pipelineYaml;
    if (isEmpty(inputSetPipelineYaml)) {
      pipelineYaml = pipelineEntity.get().getYaml();
    } else {
      pipelineYaml =
          InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.get().getYaml(), inputSetPipelineYaml, false);
    }
    List<EntityDetail> entityDetails = pipelineSetupUsageHelper.getReferencesOfPipeline(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, pipelineYaml, null, scopeInfo, isParentIdQueryingEnabled);
    pipelineRbacServiceImpl.validateStaticallyReferredEntities(entityDetails);

    Map<String, InputSetErrorResponseDTOPMS> errorResponseMap = isEmpty(inputSetPipelineYaml)
        ? null
        : InputSetErrorsHelper.getUuidToErrorResponseMap(pipelineEntity.get().getYaml(), inputSetPipelineYaml);
    PreFlightEntity preFlightEntitySaved;
    if (errorResponseMap == null) {
      preFlightEntitySaved = saveInitialPreflightEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineYaml, entityDetails, Collections.emptyList(), scopeInfo);
    } else {
      List<PipelineInputResponse> pipelineInputResponses = getPipelineInputResponses(errorResponseMap);
      preFlightEntitySaved = saveInitialPreflightEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineYaml, entityDetails, pipelineInputResponses, scopeInfo);
    }

    if (isNotEmpty(preFlightEntitySaved.getConnectorEntityDetails())) {
      registerCallbackForConnectorTest(
          accountId, orgIdentifier, projectIdentifier, entityDetails, preFlightEntitySaved);
      return preFlightEntitySaved.getUuid();
    }

    executorService.submit(AsyncPreFlightHandler.builder()
                               .entity(preFlightEntitySaved)
                               .entityDetails(entityDetails)
                               .preflightService(this)
                               .scopeInfoClient(scopeInfoClient)
                               .ngFeatureFlagHelperService(featureFlagService)
                               .build());

    return preFlightEntitySaved.getUuid();
  }

  private void registerCallbackForConnectorTest(String accountId, String orgIdentifier, String projectIdentifier,
      List<EntityDetail> entityDetails, PreFlightEntity preFlightEntitySaved) {
    updateStatus(preFlightEntitySaved.getUuid(), PreFlightStatus.IN_PROGRESS, null, null);
    ConnectorsTestRegisterResponse connectorsTestRegisterResponse =
        connectorPreflightHandler.registerConnectorsForTest(accountId, orgIdentifier, projectIdentifier,
            preFlightEntitySaved.getUuid(), preFlightEntitySaved.getConnectorEntityDetails());
    if (!connectorsTestRegisterResponse.isProceedWithoutWait()) {
      asyncWaitEngine.waitForAllOn(PreflightNotifyCallback.builder()
                                       .accountId(accountId)
                                       .orgId(orgIdentifier)
                                       .projectId(projectIdentifier)
                                       .preflightId(preFlightEntitySaved.getUuid())
                                       .build(),
          null, List.of(preFlightEntitySaved.getUuid()),
          Duration.ofSeconds(CONNECTORS_TEST_TIMEOUT_SECONDS).toMillis());
    } else {
      // We need not wait for callback. Proceed as usual
      executorService.submit(AsyncPreFlightHandler.builder()
                                 .entity(preFlightEntitySaved)
                                 .entityDetails(entityDetails)
                                 .preflightService(this)
                                 .scopeInfoClient(scopeInfoClient)
                                 .ngFeatureFlagHelperService(featureFlagService)
                                 .build());
    }
  }

  @Override
  public void schedulePreflightCheck(
      String accountId, String orgIdentifier, String projectIdentifier, String preflightCheckId) {
    final Optional<PreFlightEntity> preflightOpt = preFlightRepository.findById(preflightCheckId);
    if (preflightOpt.isEmpty()) {
      throw new InvalidRequestException(format("pre flight entity not found for %s", preflightCheckId));
    }

    final PreFlightEntity preFlightEntity = preflightOpt.get();

    executorService.submit(AsyncPreFlightHandler.builder()
                               .entity(preFlightEntity)
                               .entityDetails(preFlightEntity.getConnectorEntityDetails())
                               .preflightService(this)
                               .scopeInfoClient(scopeInfoClient)
                               .ngFeatureFlagHelperService(featureFlagService)

                               .build());
  }

  @Override
  public PreFlightEntity saveInitialPreflightEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineYaml, List<EntityDetail> entityDetails,
      List<PipelineInputResponse> pipelineInputResponses, ScopeInfo scopeInfo) {
    PreFlightEntity preFlightEntity = PreFlightMapper.toEmptyEntity(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml, scopeInfo);

    preFlightEntity.setPipelineInputResponse(pipelineInputResponses);

    List<EntityDetail> connectorUsages = entityDetails.stream()
                                             .filter(entityDetail -> entityDetail.getType() == EntityType.CONNECTORS)
                                             .collect(Collectors.toList());
    List<ConnectorCheckResponse> connectorTemplates =
        connectorPreflightHandler.getConnectorCheckResponseTemplate(connectorUsages);
    preFlightEntity.setConnectorCheckResponse(connectorTemplates);
    preFlightEntity.setConnectorEntityDetails(connectorUsages);

    if (isEmpty(preFlightEntity.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfoOptional =
          scopeResolutionHelper.getScopeInfoOptional(accountId, orgIdentifier, projectIdentifier);
      scopeInfoOptional.ifPresent(info -> preFlightEntity.setParentUniqueId(info.getUniqueId()));
    }

    return preFlightRepository.save(preFlightEntity);
  }

  @Override
  public void updateStatus(String id, PreFlightStatus overallStatus, PreFlightEntityErrorInfo errorInfo,
      PreFlightStatus allConnectorStatus) {
    Criteria criteria = Criteria.where(PreFlightEntityKeys.uuid).is(id);
    Update update = new Update();
    update.set(PreFlightEntityKeys.preFlightStatus, overallStatus);
    if (errorInfo != null) {
      update.set(PreFlightEntityKeys.errorInfo, errorInfo);
    }
    if (overallStatus == PreFlightStatus.SUCCESS) {
      update.set(
          PreFlightEntityKeys.validUntil, Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(5)).toInstant()));
    } else if (overallStatus == PreFlightStatus.FAILURE) {
      update.set(PreFlightEntityKeys.validUntil, Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()));
    }

    if (allConnectorStatus != null) {
      update.set(format("%s.$[].%s", PreFlightEntityKeys.connectorCheckResponse, ConnectorCheckResponseKeys.status),
          allConnectorStatus);
      update.set(format("%s.$[].%s", PreFlightEntityKeys.connectorCheckResponse, ConnectorCheckResponseKeys.errorInfo),
          errorInfo);
    }

    preFlightRepository.update(criteria, update);
  }

  @Override
  public List<ConnectorCheckResponse> updateConnectorCheckResponses(String accountId, String orgId, String projectId,
      String preflightEntityId, Map<String, Object> fqnToObjectMapMergedYaml, List<EntityDetail> connectorUsages) {
    List<ConnectorCheckResponse> connectorCheckResponses;
    try {
      connectorCheckResponses = connectorPreflightHandler.getConnectorCheckResponsesForReferredConnectors(
          accountId, orgId, projectId, fqnToObjectMapMergedYaml, connectorUsages);
    } catch (Exception exception) {
      log.error("Exception encountered while checking connector responses for preflightEntityId {}. {}",
          preflightEntityId, exception.getMessage());
      connectorCheckResponses = Collections.singletonList(
          ConnectorCheckResponse.builder()
              .status(PreFlightStatus.FAILURE)
              .errorInfo(
                  PreFlightEntityErrorInfo.builder()
                      .causes(Collections.singletonList(PreFlightCause.builder().cause(exception.getMessage()).build()))
                      .summary(format(
                          "Exception encountered while checking connector responses. %s", exception.getMessage()))
                      .build())
              .build());
    }
    if (isNotEmpty(connectorCheckResponses)) {
      Criteria criteria = Criteria.where(PreFlightEntityKeys.uuid).is(preflightEntityId);
      Update update = new Update();
      update.set(PreFlightEntityKeys.connectorCheckResponse, connectorCheckResponses);
      preFlightRepository.update(criteria, update);
    }
    return connectorCheckResponses;
  }

  @Override
  public PreFlightDTO getPreflightCheckResponse(String preflightCheckId) {
    Optional<PreFlightEntity> optionalPreFlightEntity = preFlightRepository.findById(preflightCheckId);
    if (optionalPreFlightEntity.isEmpty()) {
      throw new InvalidRequestException("Could not find pre flight check data corresponding to id:" + preflightCheckId);
    }
    PreFlightEntity preFlightEntity = optionalPreFlightEntity.get();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(preFlightEntity.getAccountIdentifier(), preFlightEntity.getOrgIdentifier(),
            preFlightEntity.getProjectIdentifier()),
        Resource.of("PIPELINE", preFlightEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_EXECUTE);
    return PreFlightMapper.toPreFlightDTO(preFlightEntity);
  }

  @Override
  public void deleteAllPreflightEntityForGivenPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String parentUniqueId) {
    Criteria criteria = Criteria.where(PreFlightEntityKeys.accountIdentifier)
                            .is(accountId)
                            .and(PreFlightEntityKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(PreFlightEntityKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);

    Query query = new Query(criteria);
    preFlightRepository.deleteAllPreflightForGivenParams(query);
  }

  @VisibleForTesting
  List<PipelineInputResponse> getPipelineInputResponses(Map<String, InputSetErrorResponseDTOPMS> errorResponseMap) {
    List<PipelineInputResponse> res = new ArrayList<>();
    errorResponseMap.keySet().forEach(key -> {
      List<InputSetErrorDTOPMS> errors = errorResponseMap.get(key).getErrors();
      List<PreFlightCause> preFlightCauses =
          errors.stream()
              .map(error -> PreFlightCause.builder().cause(error.getMessage()).build())
              .collect(Collectors.toList());
      PreFlightEntityErrorInfo errorInfo = PreFlightEntityErrorInfo.builder()
                                               .summary("Runtime value provided for " + key + " is wrong")
                                               .causes(preFlightCauses)
                                               .build();
      res.add(PipelineInputResponse.builder()
                  .success(false)
                  .errorInfo(errorInfo)
                  .fqn(key)
                  .stageName(YamlUtils.getStageIdentifierFromFqn(key))
                  .build());
    });
    return res;
  }

  private void sendPreflightTelemetryEvent(
      String accountId, String orgId, String projectId, String pipelineIdentifier, String parentUniqueId) {
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(PROJECT_IDENTIFIER, projectId);
    propertiesMap.put(ORG_IDENTIFIER, orgId);
    propertiesMap.put(PIPELINE_ID, pipelineIdentifier);
    propertiesMap.put(PARENT_UNIQUE_IDENTIFIER, parentUniqueId);
    pipelineTelemetryHelper.sendTelemetryEventWithAccountName(PREFLIGHT_EVENT_NAME, accountId, propertiesMap);
  }
}

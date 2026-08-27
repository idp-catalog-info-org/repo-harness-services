/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.approval.notification.stagemetadata;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.pms.approval.notification.stagemetadata.summary.CDStageSummary;
import io.harness.pms.approval.notification.stagemetadata.summary.GenericStageSummary;
import io.harness.pms.approval.notification.stagemetadata.summary.StageSummary;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class StageMetadataNotificationHelperImpl implements StageMetadataNotificationHelper {
  private final CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;

  @Inject
  public StageMetadataNotificationHelperImpl(CDNGStageSummaryResourceClient cdngStageSummaryResourceClient) {
    this.cdngStageSummaryResourceClient = cdngStageSummaryResourceClient;
  }

  @Override
  public void setFormattedSummaryOfFinishedStages(
      @NotNull Set<StageSummary> finishedStages, @NotNull Set<String> formattedFinishedStages, @NotNull Scope scope) {
    if (EmptyPredicate.isEmpty(finishedStages)) {
      log.warn("Missing finishedStages in setFormattedSummaryOfFinishedStages, returning");
      return;
    }
    if (isNull(formattedFinishedStages) || isNull(scope)) {
      throw new InvalidRequestException("Formatted finished stages and scope is required");
    }
    // IMP: changing the reference, as we will be removing stages from finishedStages
    Set<StageSummary> finishedStagesInternal = new LinkedHashSet<>(finishedStages);
    Map<String, String> formattedFinishedStagesIdentifierMap = new HashMap<>();

    handleFinishedStagesWithExecutionIdentifierAbsent(finishedStagesInternal, formattedFinishedStagesIdentifierMap);
    // now we have stages with stage execution identifiers
    handleCDFinishedStages(finishedStagesInternal, formattedFinishedStagesIdentifierMap, scope);
    handleGenericStages(finishedStagesInternal, formattedFinishedStagesIdentifierMap);

    if (!finishedStagesInternal.isEmpty()) {
      // this isn't expected
      log.error("Unknown error in setFormattedSummaryOfFinishedStages: unable to process [{}] stages",
          finishedStagesInternal);
      throw new IllegalStateException(String.format(
          "Error while formatting finished stages, unable to process [%s] stages", finishedStagesInternal));
    }
    finishedStages.forEach(stageSummary -> {
      Optional<String> optionalFormattedStage =
          Optional.ofNullable(formattedFinishedStagesIdentifierMap.get(stageSummary.getStageIdentifier()));

      formattedFinishedStages.add(optionalFormattedStage.orElseThrow(
          ()
              -> new IllegalStateException(
                  String.format("Error while formatting finished stages, unable to process %s stage",
                      stageSummary.getStageIdentifier()))));
    });
  }

  /**
   * removes stages with execution identifiers missing from stages set and
   * adds stage identifier to name or else identifier mapping in formattedFinishedStagesIdentifierMap
   */
  private void handleFinishedStagesWithExecutionIdentifierAbsent(
      @NotNull Set<StageSummary> stages, @NotNull Map<String, String> formattedFinishedStagesIdentifierMap) {
    Map<String, String> namesMapForStagesWithoutExecutionIds = new HashMap<>();
    Iterator<StageSummary> iterator = stages.iterator();
    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (StringUtils.isBlank(stageSummary.getStageExecutionIdentifier())) {
        iterator.remove(); // Remove the current element from the iterator
        namesMapForStagesWithoutExecutionIds.put(
            stageSummary.getStageIdentifier(), stageSummary.getFormattedEntityName());
      }
    }

    if (isNotEmpty(namesMapForStagesWithoutExecutionIds)) {
      log.warn("Stage Execution ids not found for [{}] stages, defaulting to stage names",
          namesMapForStagesWithoutExecutionIds.keySet());
      formattedFinishedStagesIdentifierMap.putAll(namesMapForStagesWithoutExecutionIds);
    }
  }

  /**
   * assumes that each stage in finalStages has a stage execution identifier
   * removes CD stages from finalStages and adds stage identifier to formatted summary mapping in
   * formattedFinishedStagesIdentifierMap
   */
  private void handleCDFinishedStages(@NotNull Set<StageSummary> finalStages,
      @NotNull Map<String, String> formattedFinishedStagesIdentifierMap, @NotNull Scope scope) {
    // execution identifier to cd stage summary map
    Map<String, CDStageSummary> cdStagesSummaryMap = new HashMap<>();
    Iterator<StageSummary> iterator = finalStages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof CDStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        CDStageSummary cdStageSummary = (CDStageSummary) stageSummary;
        cdStagesSummaryMap.put(cdStageSummary.getStageExecutionIdentifier(), cdStageSummary);
      }
    }
    if (cdStagesSummaryMap.isEmpty()) {
      // no CD finished stage found
      return;
    }

    Set<String> cdStageExecutionIdentifiers = cdStagesSummaryMap.keySet();

    Map<String, CDStageSummaryResponseDTO> cdFinishedFormattedSummary = new HashMap<>();
    try {
      cdFinishedFormattedSummary = getResponse(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          new ArrayList<>(cdStageExecutionIdentifiers), true));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, executionIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          cdStageExecutionIdentifiers, ex);
      log.warn("Defaulting to stage names for these CD stages: [{}]", cdStageExecutionIdentifiers);
    }
    if (isNull(cdFinishedFormattedSummary)) {
      log.warn(
          "Null response obtained while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, executionIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          cdStageExecutionIdentifiers);
      log.warn("Defaulting to stage names for these CD stages: [{}]", cdStageExecutionIdentifiers);
      cdFinishedFormattedSummary = new HashMap<>();
    }

    // add names for stages without summary
    SetView<String> stageExecutionIdsWithoutSummary =
        Sets.difference(cdStageExecutionIdentifiers, cdFinishedFormattedSummary.keySet());

    if (isNotEmpty(stageExecutionIdsWithoutSummary)) {
      log.warn(
          "Unable to fetch summary for {} via listStageExecutionFormattedSummary", stageExecutionIdsWithoutSummary);

      formattedFinishedStagesIdentifierMap.putAll(
          stageExecutionIdsWithoutSummary.stream()
              .map(cdStagesSummaryMap::get)
              .collect(Collectors.toMap(StageSummary::getStageIdentifier, StageSummary::getFormattedEntityName)));
    }

    // add metadata for stages with summary
    SetView<String> stageExecutionIdsWithSummary =
        Sets.intersection(cdStageExecutionIdentifiers, cdFinishedFormattedSummary.keySet());
    Map<String, CDStageSummaryResponseDTO> finalCdFinishedFormattedSummary = cdFinishedFormattedSummary;

    formattedFinishedStagesIdentifierMap.putAll(
        stageExecutionIdsWithSummary.stream().collect(Collectors.toMap(idWithSummary
            -> cdStagesSummaryMap.get(idWithSummary).getStageIdentifier(),
            idWithSummary
            -> StageMetadataNotificationHelper.formatCDStageMetadata(
                finalCdFinishedFormattedSummary.get(idWithSummary), cdStagesSummaryMap.get(idWithSummary)))));
  }

  /**
   * removes common/generic stages from stages set and adds identifier to names mapping in
   * formattedFinishedStagesIdentifierMap
   */
  private void handleGenericStages(
      @NotNull Set<StageSummary> stages, @NotNull Map<String, String> formattedFinishedStagesIdentifierMap) {
    Iterator<StageSummary> iterator = stages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof GenericStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        GenericStageSummary genericStageSummary = (GenericStageSummary) stageSummary;
        formattedFinishedStagesIdentifierMap.put(
            genericStageSummary.getStageIdentifier(), genericStageSummary.getFormattedEntityName());
      }
    }
  }

  @Override
  public void setFormattedSummaryOfRunningStages(@NotNull Set<StageSummary> runningStages,
      @NotNull Set<String> formattedRunningStages, @NotNull Scope scope, @NotNull String planExecutionId) {
    if (EmptyPredicate.isEmpty(runningStages)) {
      log.warn("Missing running stages in setFormattedSummaryOfRunningStages, returning");
      return;
    }
    if (isNull(formattedRunningStages) || isNull(scope) || StringUtils.isBlank(planExecutionId)) {
      throw new InvalidRequestException("Formatted running stages and scope and plan id is required");
    }

    // IMP: changing the reference, as we will be removing stages from runningStages
    Set<StageSummary> runningStagesInternal = new LinkedHashSet<>(runningStages);
    Map<String, String> formattedRunningStagesIdentifierMap = new HashMap<>();

    handleFinishedStagesWithExecutionIdentifierAbsent(runningStagesInternal, formattedRunningStagesIdentifierMap);
    // now we have stages with stage execution identifiers
    handleCDFinishedStages(runningStagesInternal, formattedRunningStagesIdentifierMap, scope);
    handleGenericStages(runningStagesInternal, formattedRunningStagesIdentifierMap);

    if (!runningStagesInternal.isEmpty()) {
      // this isn't expected
      log.error(
          "Unknown error in setFormattedSummaryOfRunningStages: unable to process [{}] stages", runningStagesInternal);
      throw new IllegalStateException(
          String.format("Error while formatting running stages, unable to process [%s] stages", runningStagesInternal));
    }
    runningStages.forEach(stageSummary -> {
      Optional<String> optionalFormattedStage =
          Optional.ofNullable(formattedRunningStagesIdentifierMap.get(stageSummary.getStageIdentifier()));

      formattedRunningStages.add(optionalFormattedStage.orElseThrow(
          ()
              -> new IllegalStateException(
                  String.format("Error while formatting running stages, unable to process %s stage",
                      stageSummary.getStageIdentifier()))));
    });
  }

  @Override
  public void setFormattedSummaryOfUpcomingStages(@NotNull Set<StageSummary> upcomingStages,
      @NotNull Set<String> formattedUpcomingStages, @NotNull Scope scope, @NotNull String planExecutionId) {
    if (EmptyPredicate.isEmpty(upcomingStages)) {
      log.warn("Missing upcoming stages in setFormattedSummaryOfUpcomingStages, returning");
      return;
    }
    if (isNull(formattedUpcomingStages) || isNull(scope) || StringUtils.isBlank(planExecutionId)) {
      throw new InvalidRequestException("Formatted Upcoming stages and scope and plan id is required");
    }

    // IMP: changing the reference, as we will be removing stages from stagesSummary
    Set<StageSummary> upcomingStagesInternal = new LinkedHashSet<>(upcomingStages);
    Map<String, String> formattedUpcomingStagesIdentifierMap = new HashMap<>();

    handleCDUpcomingFormattedStages(
        upcomingStagesInternal, formattedUpcomingStagesIdentifierMap, scope, planExecutionId);
    handleGenericStages(upcomingStagesInternal, formattedUpcomingStagesIdentifierMap);

    if (!upcomingStagesInternal.isEmpty()) {
      // this isn't expected
      log.error("Unknown error in setFormattedSummaryOfUpcomingStages: unable to process [{}] stages",
          upcomingStagesInternal);
      throw new IllegalStateException(String.format(
          "Error while formatting upcoming stages, unable to process [%s] stages", upcomingStagesInternal));
    }

    upcomingStages.forEach(stageSummary -> {
      Optional<String> optionalFormattedStage =
          Optional.ofNullable(formattedUpcomingStagesIdentifierMap.get(stageSummary.getStageIdentifier()));

      formattedUpcomingStages.add(optionalFormattedStage.orElseThrow(
          ()
              -> new IllegalStateException(
                  String.format("Error while formatting upcoming stages, unable to process %s stage",
                      stageSummary.getStageIdentifier()))));
    });
  }

  /**
   *
   * removes CD stages from upcomingStages set and
   * adds identifier to formatted summary mapping in formattedUpcomingStagesIdentifierMap
   */
  private void handleCDUpcomingFormattedStages(@NotNull Set<StageSummary> upcomingStages,
      @NotNull Map<String, String> formattedUpcomingStagesIdentifierMap, @NotNull Scope scope,
      @NotBlank String planExecutionId) {
    // stage identifier to cd stage summary map
    Map<String, CDStageSummary> cdStagesSummaryMap = new HashMap<>();
    Iterator<StageSummary> iterator = upcomingStages.iterator();

    while (iterator.hasNext()) {
      StageSummary stageSummary = iterator.next();

      if (stageSummary instanceof CDStageSummary) {
        iterator.remove(); // Remove the current element from the iterator
        CDStageSummary cdStageSummary = (CDStageSummary) stageSummary;
        cdStagesSummaryMap.put(cdStageSummary.getStageIdentifier(), cdStageSummary);
      }
    }

    if (cdStagesSummaryMap.isEmpty()) {
      // no CD upcoming stage found
      return;
    }

    Set<String> cdStageIdentifiers = cdStagesSummaryMap.keySet();

    Map<String, CDStageSummaryResponseDTO> cdUpcomingFormattedSummary = new HashMap<>();
    try {
      cdUpcomingFormattedSummary = getResponse(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          new ArrayList<>(cdStageIdentifiers), true));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStagePlanCreationFormattedSummary with accountId:{}, orgId:{}, projectId:{}, planId:{}, stageIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          cdStageIdentifiers, ex);
      log.warn("Defaulting to stage names for these upcoming CD stages: [{}]", cdStageIdentifiers);
    }
    if (isNull(cdUpcomingFormattedSummary)) {
      log.warn(
          "Null response obtained while listStagePlanCreationFormattedSummary with accountId:{}, orgId:{}, projectId:{}, planId:{}, stageIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          cdStageIdentifiers);
      log.warn("Defaulting to stage names for these upcoming CD stages: [{}]", cdStageIdentifiers);
      cdUpcomingFormattedSummary = new HashMap<>();
    }

    // add names for stages without summary
    SetView<String> stageIdsWithoutSummary = Sets.difference(cdStageIdentifiers, cdUpcomingFormattedSummary.keySet());

    if (isNotEmpty(stageIdsWithoutSummary)) {
      log.warn("Unable to fetch summary for {} via listStagePlanCreationFormattedSummary", stageIdsWithoutSummary);

      formattedUpcomingStagesIdentifierMap.putAll(
          stageIdsWithoutSummary.stream()
              .map(cdStagesSummaryMap::get)
              .collect(Collectors.toMap(StageSummary::getStageIdentifier, StageSummary::getFormattedEntityName)));
    }

    // add metadata for stages with summary
    SetView<String> stageIdsWithSummary = Sets.intersection(cdStageIdentifiers, cdUpcomingFormattedSummary.keySet());
    Map<String, CDStageSummaryResponseDTO> finalCdUpcomingFormattedSummary = cdUpcomingFormattedSummary;

    formattedUpcomingStagesIdentifierMap.putAll(
        stageIdsWithSummary.stream().collect(Collectors.toMap(Function.identity(),
            idWithSummary
            -> StageMetadataNotificationHelper.formatCDStageMetadata(
                finalCdUpcomingFormattedSummary.get(idWithSummary), cdStagesSummaryMap.get(idWithSummary)))));
  }

  public Map<String, CDStageSummaryResponseDTO> getCdFinishedFormattedSummary(
      Set<String> stageExecutionIdentifiers, Scope scope) {
    Map<String, CDStageSummaryResponseDTO> cdFinishedFormattedSummary = new HashMap<>();
    try {
      cdFinishedFormattedSummary = getResponse(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          new ArrayList<>(stageExecutionIdentifiers), false));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, executionIds:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
          stageExecutionIdentifiers, ex);
    }
    return cdFinishedFormattedSummary;
  }

  public Map<String, CDStageSummaryResponseDTO> getCdStagePlanCreationFormattedSummary(
      String planExecutionId, Set<String> stageIdentifiers, Scope scope) {
    Map<String, CDStageSummaryResponseDTO> cdFinishedFormattedSummary = new HashMap<>();
    try {
      cdFinishedFormattedSummary = getResponse(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), planExecutionId,
          new ArrayList<>(stageIdentifiers), false));
    } catch (Exception ex) {
      log.warn(
          "Error occurred while listStageExecutionFormattedSummary with accountId:{}, orgId:{}, projectId:{}, stageIdentifiers:[{}] ",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageIdentifiers, ex);
    }
    return cdFinishedFormattedSummary;
  }
}

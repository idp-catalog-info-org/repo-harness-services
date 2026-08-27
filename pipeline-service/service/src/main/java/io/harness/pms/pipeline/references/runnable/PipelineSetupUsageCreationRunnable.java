/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.references.runnable;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.references.filter.FilterCreationParams;
import io.harness.repositories.pipeline.PMSPipelineRepository;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineSetupUsageCreationRunnable implements Runnable {
  @Inject FilterCreatorMergeService filterCreatorMergeService;
  private FilterCreationParams filterCreationParams;
  @Inject PMSPipelineRepository pmsPipelineRepository;

  public PipelineSetupUsageCreationRunnable(FilterCreationParams filterCreationParams) {
    this.filterCreationParams = filterCreationParams;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore2 = new ResponseTimeRecorder("PipelineReferencesRunnable BG Task")) {
      String pipelineIdentifier = filterCreationParams.getPipelineEntity().getIdentifier();
      try {
        log.info(String.format("Calculating pipeline setup usage creation in the background for pipelineIdentifier: %s",
            pipelineIdentifier));
        //        The filter service is being called here as the references are calculated as part of filter creation
        PipelineEntity updatedPipelineEntity = updatePipelineInfo(filterCreationParams);
        pmsPipelineRepository.updatePipelineFilters(
            updatedPipelineEntity, updatedPipelineEntity.getUuid(), updatedPipelineEntity.getYamlHash());
      } catch (IOException e) {
        log.error(String.format(
            "Faced an IO exception while calculating setup usage creation for pipeline: %s.", pipelineIdentifier));
      } catch (Exception exception) {
        log.error("Faced exception while calculating setup usage creation for pipeline {} in BG THREAD : ",
            pipelineIdentifier, exception);
      }
    }
  }

  private PipelineEntity updatePipelineInfo(FilterCreationParams filterCreationParams) throws IOException {
    FilterCreatorMergeServiceResponse filtersAndStageCount =
        filterCreatorMergeService.getPipelineInfo(filterCreationParams);
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    PipelineEntity newEntity = pipelineEntity.withStageCount(filtersAndStageCount.getStageCount())
                                   .withStageNames(filtersAndStageCount.getStageNames());
    newEntity.getFilters().clear();
    try {
      if (isNotEmpty(filtersAndStageCount.getFilters())) {
        filtersAndStageCount.getFilters().forEach(
            (key, value)
                -> newEntity.getFilters().put(key, isNotEmpty(value) ? Document.parse(value) : Document.parse("{}")));
      }

      if (isNotEmpty(pipelineEntity.getTemplateModules())) {
        for (String module : pipelineEntity.getTemplateModules()) {
          if (!newEntity.getFilters().containsKey(module)) {
            newEntity.getFilters().put(module, Document.parse("{}"));
          }
        }
      }
    } catch (Exception e) {
      log.error("Unable to parse the Filter value", e);
    }
    return newEntity;
  }
}

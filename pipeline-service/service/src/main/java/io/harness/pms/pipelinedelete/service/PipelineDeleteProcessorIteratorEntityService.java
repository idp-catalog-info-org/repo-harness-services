/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinedelete.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity.PipelineDeleteProcessorIteratorEntityKeys;
import io.harness.repositories.pipelinedelete.PipelineDeleteProcessorIteratorEntityRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

/*
 * This service is used to do MongoDB operations for the PipelineDeleteProcessorIteratorEntity
 * Like save/update records in DB
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class PipelineDeleteProcessorIteratorEntityService {
  @Inject private PipelineDeleteProcessorIteratorEntityRepository deleteProcessorIteratorEntityRepository;

  /**
   * Saves the pipeline delete processor iterator entity in harness pms db
   * @param deleteProcessorIteratorEntity delete processor iterator entity to save
   * @return PipelineDeleteProcessorIteratorEntity the saved entity
   */
  public PipelineDeleteProcessorIteratorEntity save(
      PipelineDeleteProcessorIteratorEntity deleteProcessorIteratorEntity) {
    return deleteProcessorIteratorEntityRepository.save(deleteProcessorIteratorEntity);
  }

  public PipelineDeleteProcessorIteratorEntity updateNextIteration(String uuid, Long nextIteration) {
    Update updateOps = new Update();
    updateOps.set(PipelineDeleteProcessorIteratorEntityKeys.nextIteration, nextIteration);
    return deleteProcessorIteratorEntityRepository.update(uuid, updateOps);
  }

  public void deleteById(String uuid) {
    deleteProcessorIteratorEntityRepository.deleteById(uuid);
  }
}

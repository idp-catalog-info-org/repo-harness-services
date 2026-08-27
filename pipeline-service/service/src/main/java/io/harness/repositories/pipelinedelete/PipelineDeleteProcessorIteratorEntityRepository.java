/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipelinedelete;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;

import org.springframework.data.repository.CrudRepository;

/*
 * This repository is used to do MongoDB operations for the PipelineDeleteProcessorIteratorEntity
 * Like save/update records in DB
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@HarnessRepo
public interface PipelineDeleteProcessorIteratorEntityRepository
    extends CrudRepository<PipelineDeleteProcessorIteratorEntity, String>,
            PipelineDeleteProcessorIteratorEntityRepositoryCustom {}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.annotations;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.annotations.PipelineAnnotationEntity;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@Transactional
@OwnedBy(PIPELINE)
public interface PipelineAnnotationRepository
    extends Repository<PipelineAnnotationEntity, String>, PipelineAnnotationRepositoryCustom {}

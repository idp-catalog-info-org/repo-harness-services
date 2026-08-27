/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.KafkaOutboxEvent;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

@OwnedBy(PIPELINE)
@HarnessRepo
@Transactional
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface KafkaOutboxEventRepository
    extends PagingAndSortingRepository<KafkaOutboxEvent, String>, CrudRepository<KafkaOutboxEvent, String>,
            KafkaOutboxEventCustomRepository {
  Page<KafkaOutboxEvent> findByBlockedFalseOrBlockedNull(Pageable pageable);
}

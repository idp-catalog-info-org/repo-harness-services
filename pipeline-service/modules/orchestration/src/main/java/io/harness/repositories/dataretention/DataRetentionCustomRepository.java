/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.dataretention;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.accountoverrides.DataRetentionEntity;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface DataRetentionCustomRepository {
  Optional<DataRetentionEntity> findByAccountIdentifier(String accountId);

  /**
   * Updates DataRetentionEntity using accountIdentifier, creates record if not found
   * Uses - accountIdentifier_1 idx
   * @param accountIdentifier accountIdentifier
   * @param updateOps updates to put
   * @return DataRetentionEntity
   */
  DataRetentionEntity findAndModify(String accountIdentifier, Update updateOps);

  /**
   * Updates DataRetentionEntity using accountIdentifier, throws exception if record not found
   * Uses - accountIdentifier_1 idx
   * @param accountIdentifier accountIdentifier
   * @param updateOps updates to put
   * @return DataRetentionEntity
   */
  DataRetentionEntity update(String accountIdentifier, Update updateOps);

  /**
   * Fetches all the DataRetentionEntity with projections
   * @param criteria
   * @param fieldsToInclude
   * @return
   */
  Stream<DataRetentionEntity> fetchFromSecondaryWithProjections(Criteria criteria, Set<String> fieldsToInclude);
}

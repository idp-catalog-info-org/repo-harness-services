/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service.impl;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.logging.AutoLogContext;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponse;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetInlineHcMigrationService;
import io.harness.repositories.inputset.PMSInputSetRepository;

import com.google.inject.Inject;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PMSInputSetInlineHcMigrationServiceImpl implements PMSInputSetInlineHcMigrationService {
  private final PMSInputSetRepository inputSetRepository;

  @Override
  public RollbackResponse rollbackInputSetsFromInlineHCToInline(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("Account identifier is required");
    }

    try (AutoLogContext ignore = new AutoLogContext(Map.of(ACCOUNT_KEY, accountIdentifier), OVERRIDE_NESTS)) {
      log.info("Starting migration of input sets from INLINE_HC to INLINE");

      try {
        Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InputSetEntityKeys.storeType)
                                .is(StoreType.INLINE_HC)
                                .and(InputSetEntityKeys.deleted)
                                .is(false);

        Update update = new Update()
                            .set(InputSetEntityKeys.storeType, StoreType.INLINE)
                            .unset(InputSetEntityKeys.filePath)
                            .unset(InputSetEntityKeys.repo)
                            .unset(InputSetEntityKeys.repoURL)
                            .unset(InputSetEntityKeys.connectorRef)
                            .unset(InputSetEntityKeys.fallBackBranch);

        Long modifiedCount = inputSetRepository.updateInputSetMetadataBulk(criteria, update);

        if (modifiedCount > 0) {
          log.info("Successfully migrated {} input sets from INLINE_HC to INLINE", modifiedCount);
        } else {
          log.info("No input sets with storeType INLINE_HC found");
        }

        return RollbackResponse.builder().migratedCount(modifiedCount).build();
      } catch (Exception e) {
        log.error("Error migrating input sets from INLINE_HC to INLINE", e);
        throw e;
      }
    }
  }
}

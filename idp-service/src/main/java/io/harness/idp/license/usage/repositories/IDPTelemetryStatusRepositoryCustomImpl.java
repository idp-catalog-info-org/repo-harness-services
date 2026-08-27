/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.repositories;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus;
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus.IDPTelemetrySentStatusKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IDPTelemetryStatusRepositoryCustomImpl implements IDPTelemetryStatusRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public void updateLastSent(String accountId, long lastSent) {
    Query query = new Query().addCriteria(new Criteria().and(IDPTelemetrySentStatusKeys.accountId).is(accountId));
    Update update = new Update().set(IDPTelemetrySentStatusKeys.lastSent, lastSent);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
    try {
      // Atomic lock acquiring attempt
      // Everything after this line is critical section
      mongoTemplate.findAndModify(query, update, options, IDPTelemetrySentStatus.class);
    } catch (DuplicateKeyException ignored) {
      // ignored
    }
  }
}

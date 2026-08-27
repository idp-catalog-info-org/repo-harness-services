/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.distribution.constraint.Consumer;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;

import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@HarnessRepo
public class ResourceRestraintInstanceCustomRepositoryImpl implements ResourceRestraintInstanceCustomRepository {
  private final MongoTemplate secondaryMongoTemplate;

  @Autowired
  public ResourceRestraintInstanceCustomRepositoryImpl(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  @Override
  public List<ResourceRestraintInstance> findAllByState(EnumSet<Consumer.State> states) {
    Criteria criteria = Criteria.where(ResourceRestraintInstanceKeys.state).in(states);
    Query query = new Query(criteria);
    return secondaryMongoTemplate.find(query, ResourceRestraintInstance.class);
  }
}

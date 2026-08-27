/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline.yamlConversion;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineYamlConversionRepositoryCustomImpl implements PipelineYamlConversionRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public void upsert(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    mongoTemplate.upsert(query, update, PipelineYamlConversionEntity.class);
  }

  @Override
  public PipelineYamlConversionEntity findOne(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, PipelineYamlConversionEntity.class);
  }
}

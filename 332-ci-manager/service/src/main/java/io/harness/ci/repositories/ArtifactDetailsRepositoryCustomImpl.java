/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static java.util.Objects.isNull;

import io.harness.annotation.HarnessRepo;
import io.harness.app.beans.entities.artifacts.ArtifactDetails;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@HarnessRepo
public class ArtifactDetailsRepositoryCustomImpl implements ArtifactDetailsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<ArtifactDetails> findOneByCriteria(Criteria criteria) {
    Query query = new Query(criteria);
    ArtifactDetails artifactDetails = mongoTemplate.findOne(query, ArtifactDetails.class);

    if (isNull(artifactDetails)) {
      return Optional.empty();
    }
    return Optional.of(artifactDetails);
  }
}

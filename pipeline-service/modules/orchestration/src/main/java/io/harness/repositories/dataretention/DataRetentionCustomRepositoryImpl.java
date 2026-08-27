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
import io.harness.entity.accountoverrides.DataRetentionEntity.DataRetentionEntityKeys;
import io.harness.exception.EntityNotFoundException;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class DataRetentionCustomRepositoryImpl implements DataRetentionCustomRepository {
  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate mongoTemplate;

  @Inject
  public DataRetentionCustomRepositoryImpl(
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder, MongoTemplate mongoTemplate) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<DataRetentionEntity> findByAccountIdentifier(String accountId) {
    Criteria criteria = Criteria.where(DataRetentionEntityKeys.accountIdentifier).is(accountId);
    return Optional.ofNullable(secondaryMongoTemplate.findOne(new Query(criteria), DataRetentionEntity.class));
  }

  /**
   * This method is used while updating account overrides. This guarantees that we update the value, if the override
   * exists for an account else, it creates a new data retention entity with the new values.
   */
  @Override
  public DataRetentionEntity findAndModify(String accountIdentifier, Update updateOps) {
    Query query = new Query(Criteria.where(DataRetentionEntityKeys.accountIdentifier).is(accountIdentifier));
    updateOps.set(DataRetentionEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    updateOps.setOnInsert(DataRetentionEntityKeys.createdAt, System.currentTimeMillis());
    return mongoTemplate.findAndModify(
        query, updateOps, FindAndModifyOptions.options().returnNew(true).upsert(true), DataRetentionEntity.class);
  }

  /**
   * This method is used while updating account overrides.
   * This will only update the entity if the query matches the response else will return an exception
   */
  @Override
  public DataRetentionEntity update(String accountIdentifier, Update updateOps) {
    Query query = new Query(Criteria.where(DataRetentionEntityKeys.accountIdentifier).is(accountIdentifier));
    updateOps.set(DataRetentionEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    DataRetentionEntity updatedEntity = mongoTemplate.findAndModify(
        query, updateOps, FindAndModifyOptions.options().returnNew(true), DataRetentionEntity.class);
    if (updatedEntity == null) {
      throw new EntityNotFoundException(
          String.format("Couldn't find any existing account override for the given account: %s", accountIdentifier));
    }
    return updatedEntity;
  }

  @Override
  public Stream<DataRetentionEntity> fetchFromSecondaryWithProjections(Criteria criteria, Set<String> fieldsToInclude) {
    Query query = new Query(criteria);
    for (String fieldName : fieldsToInclude) {
      query.fields().include(fieldName);
    }
    return secondaryMongoTemplate.stream(query, DataRetentionEntity.class);
  }
}

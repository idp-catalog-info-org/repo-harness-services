/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.repositories;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity.CatalogCustomPropertyKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CatalogCustomPropertiesRepositoryCustomImpl implements CatalogCustomPropertiesRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  private static final String ID_KEY = "_id";

  private static final String COUNT = "count";

  @Override
  public long deleteMulti(String accountIdentifier, List<String> entityRefs, String field) {
    Criteria criteria = Criteria.where(CatalogCustomPropertyKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CatalogCustomPropertyKeys.entityRef)
                            .in(entityRefs)
                            .and(CatalogCustomPropertyKeys.field)
                            .is(field);
    Query query = new Query(criteria);
    DeleteResult result = mongoTemplate.remove(query, CatalogCustomPropertyEntity.class);
    return result.getDeletedCount();
  }

  @Override
  public long deleteMulti(String accountIdentifier, String entityRef, List<String> fields) {
    Criteria criteria = Criteria.where(CatalogCustomPropertyKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CatalogCustomPropertyKeys.entityRef)
                            .is(entityRef)
                            .and(CatalogCustomPropertyKeys.field)
                            .in(fields);
    Query query = new Query(criteria);
    DeleteResult result = mongoTemplate.remove(query, CatalogCustomPropertyEntity.class);
    return result.getDeletedCount();
  }

  @Override
  public List<FieldAndCount> getCustomPropertiesFieldEntities(String accountIdentifier) {
    Criteria criteria = Criteria.where(CatalogCustomPropertyKeys.accountIdentifier).is(accountIdentifier);

    ProjectionOperation projectionOperation =
        Aggregation.project().andExpression(ID_KEY).as(CatalogCustomPropertyKeys.field).andExpression(COUNT).as(COUNT);

    GroupOperation groupOperation = Aggregation.group(CatalogCustomPropertyKeys.field).count().as(COUNT);

    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.match(criteria), groupOperation, projectionOperation);

    return mongoTemplate.aggregate(aggregation, "catalogCustomProperties", FieldAndCount.class).getMappedResults();
  }

  @Override
  public List<String> findUniqueEntityRefs(String accountIdentifier) {
    Criteria criteria = Criteria.where(CatalogCustomPropertyKeys.accountIdentifier).is(accountIdentifier);
    Query query = new Query(criteria);
    return mongoTemplate.query(CatalogCustomPropertyEntity.class)
        .distinct(CatalogCustomPropertyKeys.entityRef)
        .matching(query)
        .as(String.class)
        .all();
  }

  @Override
  public UpdateResult updateEntityRef(String accountIdentifier, String entityRef, String modifiedEntityRef) {
    Criteria criteria = Criteria.where(CatalogCustomPropertyKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(CatalogCustomPropertyKeys.entityRef)
                            .is(entityRef);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CatalogCustomPropertyKeys.entityRef, modifiedEntityRef);
    return mongoTemplate.updateMulti(query, update, CatalogCustomPropertyEntity.class);
  }
}

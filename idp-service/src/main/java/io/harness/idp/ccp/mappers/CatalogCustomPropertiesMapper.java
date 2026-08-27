/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.JacksonUtils.readValueForSingleEntity;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyEntitiesCount;
import io.harness.spec.server.idp.v1.model.CustomPropertyResponse;
import io.harness.spec.server.idp.v1.model.EntityValue;
import io.harness.spec.server.idp.v1.model.PropertyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CatalogCustomPropertiesMapper {
  public static CustomPropertyByFieldDeleteResponse toDeleteResponse(String field, List<String> entityRefsToDelete) {
    CustomPropertyByFieldDeleteResponse response = new CustomPropertyByFieldDeleteResponse();
    CustomPropertyEntitiesCount entitiesWithDeletion = new CustomPropertyEntitiesCount();
    entitiesWithDeletion.setCount(entityRefsToDelete.size());
    entitiesWithDeletion.setEntityRefs(entityRefsToDelete.stream()
                                           .map(entityRef -> entityRef.replace("template:", "workflow:"))
                                           .collect(Collectors.toList()));
    response.property(field);
    response.entitiesWithDeletion(entitiesWithDeletion);
    return response;
  }

  public static CustomPropertyByFieldResponse toResponse(String field, List<CatalogCustomPropertyEntity> entitiesToAdd,
      List<CatalogCustomPropertyEntity> entitiesToUpdate) {
    CustomPropertyByFieldResponse response = new CustomPropertyByFieldResponse();
    CustomPropertyEntitiesCount entitiesWithAdditions = new CustomPropertyEntitiesCount();
    CustomPropertyEntitiesCount entitiesWithUpdates = new CustomPropertyEntitiesCount();
    entitiesWithAdditions.setCount(entitiesToAdd.size());
    entitiesWithUpdates.setCount(entitiesToUpdate.size());
    entitiesWithAdditions.entityRefs(
        entitiesToAdd.stream()
            .map(catalogCustomPropertyEntity
                -> catalogCustomPropertyEntity.getEntityRef().replace("template:", "workflow:"))
            .collect(Collectors.toList()));
    entitiesWithUpdates.entityRefs(
        entitiesToUpdate.stream()
            .map(catalogCustomPropertyEntity
                -> catalogCustomPropertyEntity.getEntityRef().replace("template:", "workflow:"))
            .collect(Collectors.toList()));
    response.setProperty(field);
    response.setEntitiesWithAdditions(entitiesWithAdditions);
    response.setEntitiesWithUpdates(entitiesWithUpdates);
    return response;
  }

  public static CustomPropertyResponse toEntitySaveResponse(String entityRef, int size) {
    CustomPropertyResponse response = new CustomPropertyResponse();
    response.setStatus(CustomPropertyResponse.StatusEnum.SUCCESS);
    if (size > 1) {
      response.setMessage(
          String.format("All %s properties have been updated successfully for entity %s", size, entityRef));
    } else {
      response.setMessage(String.format("Property has been updated successfully for entity %s", entityRef));
    }
    return response;
  }

  public static CustomPropertyResponse toEntityDeleteResponse(String entityRef, List<String> properties) {
    CustomPropertyResponse response = new CustomPropertyResponse();
    if (properties.size() >= 1) {
      response.setStatus(CustomPropertyResponse.StatusEnum.SUCCESS);
      response.setMessage(String.format("Property(s) %s deleted successfully for entity %s", properties, entityRef));
    } else {
      response.setStatus(CustomPropertyResponse.StatusEnum.ERROR);
      response.setMessage(String.format("No property found to be deleted for entity %s", entityRef));
    }
    return response;
  }

  public static CustomPropertyResponse toPropertySaveResponse(String field, int size) {
    CustomPropertyResponse response = new CustomPropertyResponse();
    response.setStatus(CustomPropertyResponse.StatusEnum.SUCCESS);
    if (size > 1) {
      response.setMessage(
          String.format("All %s entities have been updated successfully with property %s", size, field));
    } else {
      response.setMessage(String.format("Entity has been updated successfully with property %s", field));
    }
    return response;
  }

  public static CustomPropertyResponse toPropertyDeleteResponse(String field, List<String> entityRefs) {
    CustomPropertyResponse response = new CustomPropertyResponse();
    if (entityRefs.size() >= 1) {
      response.setStatus(CustomPropertyResponse.StatusEnum.SUCCESS);
      response.setMessage(String.format("Entity(s) %s deleted successfully for property %s", entityRefs, field));
    } else {
      response.setStatus(CustomPropertyResponse.StatusEnum.ERROR);
      response.setMessage(String.format("No entity found to be deleted for property %s", field));
    }
    return response;
  }

  public static CustomPropertyByEntityGetResponse toEntityGetResponse(List<CatalogCustomPropertyEntity> entitiesInDB) {
    CustomPropertyByEntityGetResponse response = new CustomPropertyByEntityGetResponse();
    List<PropertyValue> propertyValues = new ArrayList<>();
    entitiesInDB.forEach(propertyInDB -> {
      PropertyValue propertyValue = new PropertyValue();
      propertyValue.setProperty(propertyInDB.getField());
      propertyValue.setValue(readValueForSingleEntity(propertyInDB.getValue(), Object.class));
      propertyValue.setMode(PropertyValue.ModeEnum.fromValue(propertyInDB.getMode().toString()));
      propertyValues.add(propertyValue);
    });
    response.setProperties(propertyValues);
    return response;
  }

  public static CustomPropertyByFieldGetResponse toFieldGetResponse(List<CatalogCustomPropertyEntity> entitiesInDB) {
    CustomPropertyByFieldGetResponse response = new CustomPropertyByFieldGetResponse();
    List<EntityValue> entityValues = new ArrayList<>();
    entitiesInDB.forEach(entityInDB -> {
      EntityValue entityValue = new EntityValue();
      entityValue.setEntityRef(entityInDB.getEntityRef().replace("template:", "workflow:"));
      entityValue.setValue(readValueForSingleEntity(entityInDB.getValue(), Object.class));
      entityValues.add(entityValue);
    });
    response.setEntityRefs(entityValues);
    if (!isEmpty(entitiesInDB)) {
      response.setMode(entitiesInDB.get(0).getMode().toString());
    }
    return response;
  }
}

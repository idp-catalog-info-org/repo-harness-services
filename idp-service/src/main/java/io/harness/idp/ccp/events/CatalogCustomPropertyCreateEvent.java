/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_CUSTOM_PROPERTIES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class CatalogCustomPropertyCreateEvent implements Event {
  public static final String CATALOG_CUSTOM_PROPERTY_CREATED = "CatalogCustomPropertyCreated";
  private String accountIdentifier;
  private CatalogCustomPropertyEntity entity;

  public CatalogCustomPropertyCreateEvent(String accountIdentifier, CatalogCustomPropertyEntity entity) {
    this.accountIdentifier = accountIdentifier;
    this.entity = entity;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, entity.getEntityRef());
    return Resource.builder()
        .identifier(entity.getAccountIdentifier() + "_" + entity.getEntityRef() + "_" + entity.getField())
        .type(IDP_CATALOG_CUSTOM_PROPERTIES)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return CATALOG_CUSTOM_PROPERTY_CREATED;
  }
}

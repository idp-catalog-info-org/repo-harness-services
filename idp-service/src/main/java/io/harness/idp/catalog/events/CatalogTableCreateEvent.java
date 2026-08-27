/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_TABLE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class CatalogTableCreateEvent implements Event {
  public static final String IDP_CATALOG_TABLE_CREATED = "IDPCatalogTableCreated";
  private String accountIdentifier;
  private EntityTableResponse entityTableResponse;

  public CatalogTableCreateEvent(String accountIdentifier, EntityTableResponse entityTableResponse) {
    this.accountIdentifier = accountIdentifier;
    this.entityTableResponse = entityTableResponse;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, entityTableResponse.getName());
    return Resource.builder()
        .identifier(entityTableResponse.getIdentifier())
        .type(IDP_CATALOG_TABLE)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return IDP_CATALOG_TABLE_CREATED;
  }
}

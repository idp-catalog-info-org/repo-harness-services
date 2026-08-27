/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_ENTITY_LINK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
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
public class EntityLinkDeleteEvent implements Event {
  public static final String ENTITY_LINK_DELETED = "EntityLinkDeleted";

  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String entityRef;
  private String oldEntityLinkJson;

  public EntityLinkDeleteEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String entityRef, String oldEntityLinkJson) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.entityRef = entityRef;
    this.oldEntityLinkJson = oldEntityLinkJson;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (projectIdentifier != null) {
      return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
    } else if (orgIdentifier != null) {
      return new OrgScope(accountIdentifier, orgIdentifier);
    }
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, entityRef);
    return Resource.builder().identifier(entityRef).type(IDP_ENTITY_LINK).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return ENTITY_LINK_DELETED;
  }
}

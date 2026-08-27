/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.settings.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_PERMISSIONS;
import static io.harness.idp.settings.Constants.IDP_PERMISSIONS_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class PermissionsCreateEvent implements Event {
  public static final String PERMISSIONS_CREATED = "PermissionsCreated";

  private BackstagePermissions newBackstagePermissions;
  private String accountIdentifier;

  public PermissionsCreateEvent(String accountIdentifier, BackstagePermissions newBackstagePermissions) {
    this.newBackstagePermissions = newBackstagePermissions;
    this.accountIdentifier = accountIdentifier;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, IDP_PERMISSIONS_NAME);
    return Resource.builder()
        .identifier(IDP_PERMISSIONS + "_" + accountIdentifier)
        .type(IDP_PERMISSIONS)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return PERMISSIONS_CREATED;
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_HOMEPAGE_LAYOUT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class HomePageLayoutUpdateEvent implements Event {
  public static final String HOMEPAGE_LAYOUT_UPDATED = "HomePageLayoutUpdated";

  private HomePageLayoutResponse newHomePageLayout;
  private HomePageLayoutResponse oldHomePageLayout;
  private String accountIdentifier;

  public HomePageLayoutUpdateEvent(
      HomePageLayoutResponse newHomePageLayout, HomePageLayoutResponse oldHomePageLayout, String accountIdentifier) {
    this.newHomePageLayout = newHomePageLayout;
    this.oldHomePageLayout = oldHomePageLayout;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, "IDP Home Page Layout");
    return Resource.builder()
        .identifier(accountIdentifier + "idpHomePageLayout")
        .type(IDP_HOMEPAGE_LAYOUT)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return HOMEPAGE_LAYOUT_UPDATED;
  }
}
/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.branding.events;

import static io.harness.audit.ResourceTypeConstants.BRANDING_SETTINGS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;

import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@Getter
@NoArgsConstructor
public abstract class BrandingEvent implements Event {
  public static final String BRANDING_SETTINGS_CREATED = "branding_settings_created";
  public static final String BRANDING_SETTINGS_UPDATED = "branding_settings_updated";

  String accountIdentifier;
  Branding branding;
  public BrandingEvent(String accountIdentifier, Branding branding) {
    this.accountIdentifier = accountIdentifier;
    this.branding = branding;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    return Resource.builder()
        .identifier(branding.getId())
        .uniqueId(branding.getUniqueId())
        .type(BRANDING_SETTINGS)
        .build();
  }

  @Override public abstract String getEventType();
}

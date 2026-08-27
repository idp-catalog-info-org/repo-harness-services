/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.outbox.branding.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.branding.entities.Branding;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
public class BrandingUpdateEvent extends BrandingEvent {
  Branding oldBranding;

  public BrandingUpdateEvent(String accountIdentifier, Branding newBranding, Branding oldBranding) {
    super(accountIdentifier, newBranding);
    this.oldBranding = oldBranding;
  }

  public String getEventType() {
    return BRANDING_SETTINGS_UPDATED;
  }
}

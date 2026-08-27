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

import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@NoArgsConstructor
public class BrandingCreateEvent extends BrandingEvent {
  public BrandingCreateEvent(String accountIdentifier, Branding branding) {
    super(accountIdentifier, branding);
  }

  public String getEventType() {
    return BRANDING_SETTINGS_CREATED;
  }
}

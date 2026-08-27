/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.beans.TimeUnit;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.repositories.invites.spring.InviteRepository;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;

@OwnedBy(PL)
public class InviteRateLimitRestrictionUsageImpl implements RestrictionUsageInterface<RateLimitRestrictionMetadataDTO> {
  @Inject private InviteRepository inviteRepository;

  @Override
  public long getCurrentValue(String accountIdentifier, RateLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    TimeUnit timeUnit = restrictionMetadataDTO.getTimeUnit();
    Duration window = timeUnit.getUnit().getDuration().multipliedBy(timeUnit.getNumberOfUnits());
    long windowStart = Instant.now().minus(window).toEpochMilli();
    return inviteRepository.countByAccountIdentifierAndCreatedAtAfter(accountIdentifier, windowStart);
  }
}

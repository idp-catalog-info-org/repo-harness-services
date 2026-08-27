/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves owner strings that are email addresses into Harness user UUIDs.
 * FME APIs expect user IDs (UUIDs), but pipeline expressions like
 * {@code <+pipeline.triggeredBy.email>} produce email strings. This resolver
 * bridges the gap by detecting email-format owners and calling the user service
 * to look up the corresponding UUID.
 */
@Singleton
@OwnedBy(HarnessTeam.FME)
@Slf4j
public class FmeOwnerResolver {
  private final UserClient userClient;

  @Inject
  public FmeOwnerResolver(UserClient userClient) {
    this.userClient = userClient;
  }

  public List<String> resolveOwners(List<String> owners) {
    if (EmptyPredicate.isEmpty(owners)) {
      return Collections.emptyList();
    }
    return owners.stream().map(this::resolveIfEmail).collect(Collectors.toList());
  }

  private String resolveIfEmail(String owner) {
    if (owner == null || owner.isEmpty()) {
      return owner;
    }

    // Prefixed owners like "User:id" or "Group:id" are already in the expected format
    if (owner.contains(":")) {
      return owner;
    }

    if (!looksLikeEmail(owner)) {
      return owner;
    }

    try {
      Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.getUserByEmailId(owner));
      if (userInfo.isPresent()) {
        String uuid = userInfo.get().getUuid();
        log.debug("Resolved owner email to user ID '{}'", uuid);
        return uuid;
      }
      log.debug("Could not resolve owner email to a Harness user ID — no user found. Using as-is.");
    } catch (Exception e) {
      log.debug("Failed to resolve owner email to user ID: {}. Using as-is.", e.getMessage());
    }
    return owner;
  }

  private static boolean looksLikeEmail(String value) {
    int atIdx = value.indexOf('@');
    return atIdx > 0 && atIdx < value.length() - 1;
  }
}

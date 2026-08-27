/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.support;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;

import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/**
 * Identifier helpers shared across the onboarding provisioning steps: sanitization of caller-supplied identifiers and
 * generation of stable/unique identifiers for the resources onboarding creates. This is a stateless static utility
 * (like {@code OnboardingYamlUtils}); the logic is a verbatim move out of the former god object so behavior is
 * unchanged.
 */
@OwnedBy(HarnessTeam.CDC)
public final class OnboardingIdentifiers {
  private OnboardingIdentifiers() {}

  /** Harness identifiers allow only alphanumerics and underscores; coerce anything else to '_'. */
  public static String sanitizeIdentifier(String raw) {
    if (StringUtils.isBlank(raw)) {
      throw new InvalidRequestException("Encountered a blank identifier while onboarding");
    }
    return raw.trim().replaceAll("[^a-zA-Z0-9_]", "_");
  }

  /**
   * Generates a service identifier when the caller did not supply one. Derives a readable prefix from
   * the service name (falling back to "onboarding_service") and appends a short unique suffix so a new
   * service is created on every call. Harness identifiers must start with a letter or underscore.
   */
  public static String generateServiceIdentifier(OnboardingContextDTO context) {
    String base = StringUtils.isNotBlank(context.getServiceName()) ? sanitizeIdentifier(context.getServiceName())
                                                                   : "onboarding_service";
    if (!base.matches("^[a-zA-Z_].*")) {
      base = "_" + base;
    }
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return base + "_" + suffix;
  }

  /**
   * Resolves the infrastructure identifier: the sanitized caller-supplied infra_id, or a generated one when absent.
   * A caller-supplied id is stable across retries, so deriving the connector id from it makes repeat calls idempotent.
   */
  public static String resolveInfraId(OnboardingContextDTO context) {
    return StringUtils.isBlank(context.getInfraId()) ? generateIdentifier("onboarding_infra")
                                                     : sanitizeIdentifier(context.getInfraId());
  }

  /** Derives the K8s cluster connector identifier from the infra id, so a repeat call upserts the same connector. */
  public static String k8sConnectorIdentifier(String infraId) {
    return sanitizeIdentifier("k8s_connector_" + infraId);
  }

  /** Auto-generates a unique identifier from a base prefix and a short unique suffix. */
  public static String generateIdentifier(String base) {
    return base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  /** Auto-generates a unique 'release-'-prefixed Kubernetes release name. */
  public static String generateReleaseName() {
    return "release-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}

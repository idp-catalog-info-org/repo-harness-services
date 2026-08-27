/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.fme.governance.FmeGovernancePolicyDetails;
import io.harness.fme.governance.FmeGovernancePolicySetDetails;
import io.harness.fme.governance.FmeGovernanceResult;
import io.harness.fme.governance.GovernanceStatus;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Utility class for formatting FME governance results for logging and UI display.
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
public class FmeGovernanceFormatter {
  /**
   * Formats governance result for pipeline execution logs (human-readable).
   * Used when API returns success with governance warnings.
   */
  public static String formatForLog(FmeGovernanceResult result) {
    if (result == null || result.getDetails() == null) {
      return "No governance details available";
    }

    StringBuilder sb = new StringBuilder(256);
    sb.append("=== Governance Evaluation Result ===\n")
        .append(String.format("Status: %s | Action: %s | Type: %s\n",
            result.getStatus() != null ? result.getStatus().getValue() : "unknown",
            result.getAction() != null ? result.getAction().getAction() : "unknown",
            result.getType() != null ? result.getType().getType() : "unknown"))
        .append('\n');

    for (FmeGovernancePolicySetDetails policySet : result.getDetails()) {
      sb.append(formatPolicySetForLog(policySet));
    }

    return sb.toString();
  }

  private static String formatPolicySetForLog(FmeGovernancePolicySetDetails policySet) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Policy Set: %s [%s]\n", policySet.getName(),
        policySet.getStatus() != null ? policySet.getStatus().getValue() : "unknown"));
    if (policySet.getDescription() != null) {
      sb.append(String.format("  Description: %s\n", policySet.getDescription()));
    }

    if (policySet.getDetails() != null) {
      for (FmeGovernancePolicyDetails policy : policySet.getDetails()) {
        sb.append(formatPolicyForLog(policy));
      }
    }
    sb.append('\n');
    return sb.toString();
  }

  private static String formatPolicyForLog(FmeGovernancePolicyDetails policy) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("  - Policy: %s [%s]\n", policy.getName(),
        policy.getStatus() != null ? policy.getStatus().getValue() : "unknown"));

    if (policy.getDenyMessages() != null && !policy.getDenyMessages().isEmpty()) {
      for (String msg : policy.getDenyMessages()) {
        sb.append(String.format("    * %s\n", msg));
      }
    }
    if (policy.getError() != null) {
      sb.append(String.format("    Error: %s\n", policy.getError()));
    }
    return sb.toString();
  }

  /**
   * Formats governance result as exception message for UI display.
   * Creates a concise but informative message showing which policies failed.
   */
  public static String formatForExceptionMessage(FmeGovernanceResult result) {
    if (result == null || result.getDetails() == null) {
      return "Policy evaluation failed";
    }

    List<String> failedPolicies =
        result.getDetails()
            .stream()
            .filter(ps -> ps.getStatus() == GovernanceStatus.ERROR)
            .flatMap(ps -> ps.getDetails() != null ? ps.getDetails().stream() : Stream.empty())
            .filter(p -> p.getStatus() == GovernanceStatus.ERROR)
            .map(FmeGovernanceFormatter::formatPolicyViolation)
            .collect(Collectors.toList());

    if (failedPolicies.isEmpty()) {
      return "Policy evaluation denied the operation";
    }

    return String.format("Policy denied: %s", String.join(", ", failedPolicies));
  }

  private static String formatPolicyViolation(FmeGovernancePolicyDetails policy) {
    if (policy.getDenyMessages() != null && !policy.getDenyMessages().isEmpty()) {
      return String.format("%s (%s)", policy.getName(), String.join("; ", policy.getDenyMessages()));
    }
    return String.format("%s (policy violated)", policy.getName());
  }

  /**
   * Creates hint text for the exception to help user remediate.
   */
  public static String getHintMessage(FmeGovernanceResult result) {
    return "Review the policy requirements and ensure your feature flag configuration complies. "
        + "Contact your governance administrator if you need policy exceptions.";
  }

  /**
   * Creates explanation text for the exception.
   */
  public static String getExplanationMessage(FmeGovernanceResult result) {
    if (result == null) {
      return "The FME governance policies prevented this operation.";
    }

    int policySetCount = result.getDetails() != null ? result.getDetails().size() : 0;
    long failedCount = result.getDetails() != null
        ? result.getDetails().stream().filter(ps -> ps.getStatus() == GovernanceStatus.ERROR).count()
        : 0;

    return String.format("FME governance evaluated %d policy set(s). %d policy set(s) denied the operation.",
        policySetCount, failedCount);
  }
}

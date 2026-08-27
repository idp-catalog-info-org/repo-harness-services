/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityRouterType;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared helpers for private connectivity control plane.
 */
@OwnedBy(CI)
@UtilityClass
public class PrivateConnectivityHelpers {
  public static final String CI_RUNNER_TAG = "tag:ci-runner";
  public static final String HELPER_TAG = "tag:harness-delegate";
  public static final String CUSTOMER_APPLIANCE_TAG = "tag:pc-appliance";
  public static final String CUSTOMER_KEY_OPERATION_PREFIX = "pc-key-";
  public static final String HELPER_KEY_OPERATION_PREFIX = "pc-helper-";

  /** One reusable customer credential supports subnet-router, app-connector, and combined appliances. */
  public static List<String> customerEnrollmentTags() {
    return List.of(CUSTOMER_APPLIANCE_TAG);
  }

  /**
   * State required to enroll a hosted VM, independent of the optional connector helper. This
   * performs no provider call and is safe on execution paths.
   */
  public static boolean hasCompleteEnrollmentBinding(PrivateConnectivityConfig config) {
    if (config == null
        || StringUtils.isAnyBlank(config.getAccountIdentifier(), config.getProviderNetworkRef(),
            config.getProviderNetworkName(), config.getProviderTailnetOAuthClientId(),
            config.getProviderTailnetOAuthSecretRef(), config.getWifCredentialId(), config.getWifClientId(),
            config.getWifAudience())) {
      return false;
    }
    return true;
  }

  /** A reusable key may enroll several appliances with independently selected configured routes. */
  public static final String MULTI_SUBNET_ROUTER_HINT =
      "# The credential may be reused for multiple subnet-router replicas; configure each replica "
      + "to advertise only CIDRs from the setup configuration.";
  public static final String LINUX_FORWARDING_HINT =
      "# Before using a Linux subnet-router or App Connector role, enable IP forwarding as required by Tailscale.";

  public static List<String> buildInstallHints(PrivateConnectivityRouterType routerType) {
    if (routerType == null) {
      throw new IllegalArgumentException("A private connectivity mode is required");
    }
    String base = "# Install official Tailscale using your approved package-management process";
    String shell = "# Run the remaining commands with Bash on each Linux appliance; paste the returned credential "
        + "at the prompt";
    String suspendXtrace =
        "case \"$-\" in *x*) TS_PC_RESTORE_XTRACE=true; set +x ;; *) TS_PC_RESTORE_XTRACE=false ;; esac";
    String secret = "read -r -s -p 'Tailscale auth key: ' TS_AUTH_KEY; printf '\\n'";
    String stageSecret = "TS_AUTH_FILE=$(umask 077; mktemp); trap 'rm -f \"$TS_AUTH_FILE\"' EXIT; "
        + "printf '%s' \"$TS_AUTH_KEY\" > \"$TS_AUTH_FILE\"; unset TS_AUTH_KEY";
    String cleanup = "rm -f \"$TS_AUTH_FILE\"; trap - EXIT; unset TS_AUTH_FILE; "
        + "if [ \"$TS_PC_RESTORE_XTRACE\" = true ]; then unset TS_PC_RESTORE_XTRACE; set -x; "
        + "else unset TS_PC_RESTORE_XTRACE; fi";
    List<String> hints = new ArrayList<>(List.of(base, shell, suspendXtrace, secret, stageSecret));
    hints.add(LINUX_FORWARDING_HINT);
    if (routerType == PrivateConnectivityRouterType.BOTH) {
      hints.add("# On each node, choose exactly one appliance recipe below; do not run all three on the same node");
    }
    if (routerType == PrivateConnectivityRouterType.SUBNET_ROUTER || routerType == PrivateConnectivityRouterType.BOTH) {
      hints.add(MULTI_SUBNET_ROUTER_HINT);
      hints.add("# Subnet-router appliance");
      hints.add("sudo tailscale up --auth-key=\"file:$TS_AUTH_FILE\""
          + " --accept-dns=false"
          + " --advertise-routes=<cidrs>"
          + " --hostname=harness-subnet-$(hostname -s)");
    }
    if (routerType == PrivateConnectivityRouterType.APP_CONNECTOR || routerType == PrivateConnectivityRouterType.BOTH) {
      hints.add("# The credential may be reused for multiple App Connector replicas");
      hints.add("# App Connector appliance");
      hints.add("sudo tailscale up --auth-key=\"file:$TS_AUTH_FILE\""
          + " --accept-dns=false"
          + " --advertise-connector"
          + " --hostname=harness-connector-$(hostname -s)");
    }
    if (routerType == PrivateConnectivityRouterType.BOTH) {
      hints.add("# Combined subnet-router and App Connector appliance");
      hints.add("sudo tailscale up --auth-key=\"file:$TS_AUTH_FILE\""
          + " --accept-dns=false"
          + " --advertise-connector --advertise-routes=<cidrs>"
          + " --hostname=harness-private-connectivity-$(hostname -s)");
    }
    hints.add(cleanup);
    return List.copyOf(hints);
  }
}

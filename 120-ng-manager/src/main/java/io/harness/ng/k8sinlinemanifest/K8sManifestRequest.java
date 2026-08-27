/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.k8sinlinemanifest;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Request DTO for applying K8s manifests via inline manifest service.
 * Used by chaos-manager to submit manifest apply requests.
 *
 * New fields for container health monitoring are optional with sensible defaults
 * for backward compatibility.
 *
 * Note: @Jacksonized ensures @Builder.Default values are respected during JSON deserialization.
 */
@Value
@Builder
@Jacksonized
public class K8sManifestRequest {
  String accountId;
  String orgId;
  String projectId;
  String delegateId;
  String k8sConnectorId;
  String k8sManifest;
  String releaseIdentifier;
  String infrastructureId;
  String environmentId;
  String uid;
  boolean showDetailedDiagnosticLogs;

  // ==================== Container Health Check Configuration ====================
  // These fields are optional for backward compatibility

  /**
   * Enable container health monitoring after steady state check.
   * Default: true (enabled for enhanced error propagation)
   */
  @Builder.Default boolean enableContainerHealthCheck = true;

  /**
   * Maximum time in seconds to wait for containers to become healthy.
   * Default: 480 seconds (8 minutes, matches CI's POD_MAX_WAIT_UNTIL_READY_SECS)
   */
  @Builder.Default int containerHealthCheckTimeoutSecs = 8 * 60;

  /**
   * Interval in seconds between container health checks.
   * Default: 2 seconds
   */
  @Builder.Default int containerHealthCheckIntervalSecs = 2;

  /**
   * Prefix of the target pod name to monitor (e.g., "ddci-").
   * The full pod name is constructed as: targetPodNamePrefix + uid
   * This is only used as a fallback when no workloads (Deployment/StatefulSet/DaemonSet)
   * are found in the manifest. Primary discovery uses workload-based pod discovery.
   */
  String targetPodNamePrefix;
}

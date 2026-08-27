/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "KubeconfigContextDescriptor",
    description = "Per-context connector-creation hint reverse-engineered from a kubeconfig")
public class KubeconfigContextDescriptorDTO {
  @Schema(description = "kubeconfig context name") String name;
  @Schema(description = "Resolved cluster name referenced by the context") String clusterName;
  @Schema(description = "Namespace declared on the context, if any") String namespace;
  @Schema(description = "Detected Harness auth type; UNKNOWN when unsupported "
          + "(one of SERVICE_ACCOUNT, USER_PASSWORD, CLIENT_KEY_CERT, OPEN_ID_CONNECT, UNKNOWN)")
  String type;
  @Schema(description = "Whether the descriptor can create a connector as-is "
          + "(COMPLETE or UNSUPPORTED)")
  KubeconfigImportStatus importStatus;
  @Schema(description = "Auth-type-specific fields, keyed to connector credential-DTO field names")
  Map<String, Object> spec;
  @Schema(description = "spec keys whose values are secrets and must become Harness secret refs") List<String> secrets;
  @Schema(description = "Blocking reasons this context cannot be imported (present when importStatus is UNSUPPORTED)")
  List<String> errors;
  @Schema(description = "Lossy / non-portable notes for an importable context") List<String> warnings;
}

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "KubeconfigUploadResponse", description = "Result of a kubeconfig onboarding upload")
public class KubeconfigUploadResponseDTO {
  @Schema(description = "The current-context declared in the uploaded kubeconfig") String currentContext;
  @Schema(description = "Per-context connector-creation hints reverse-engineered from the kubeconfig")
  List<KubeconfigContextDescriptorDTO> contexts;
}

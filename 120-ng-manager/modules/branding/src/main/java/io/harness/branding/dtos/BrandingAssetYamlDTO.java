/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.spec.server.ng.v1.model.BrandingAssetsDTO;

import lombok.Builder;
import lombok.Getter;

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
public class BrandingAssetYamlDTO implements YamlDTO {
  BrandingAssetsDTO brandingAssetsDTO;
}

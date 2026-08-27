/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.mapper;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.overrides.SingleOverrideConvertorResponseDTO;

import lombok.experimental.UtilityClass;

/**
 * Use TemplateBasedOverridesMapper instead for template-based override conversion.
 *
 * @deprecated This class relies on deleted POJO mappers (UnifiedManifestMapper, UnifiedStageServiceMapper).
 *             All callers should migrate to template-based flow using TemplateBasedOverridesMapper.
 */
@UtilityClass
@Deprecated
public class UnifiedOverridesMapper {
  /**
   * DEPRECATED: POJO-based override conversion has been removed.
   *
   * @param overridesNg the override configuration
   * @return never returns, always throws exception
   * @throws InvalidRequestException always thrown to indicate POJO path removal

   *             Use TemplateBasedOverridesMapper.toUnifiedOverridesWithTemplate() instead.
   */
  @Deprecated
  public static SingleOverrideConvertorResponseDTO toUnifiedOverrides(NGServiceOverrideConfigV2 overridesNg) {
    throw new InvalidRequestException("POJO-based override conversion has been removed"
        + "Please use template-based flow. This endpoint should be updated to use TemplateBasedOverridesMapper.");
  }
}

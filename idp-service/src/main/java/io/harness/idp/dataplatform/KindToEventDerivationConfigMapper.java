/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.platform.schema.service.api.v1.EntityType;

@OwnedBy(HarnessTeam.IDP)
public interface KindToEventDerivationConfigMapper {
  /**
   * Event derivation config fragment owned by IDP custom-kind publish path (entities-only for phase 1).
   */
  EventDerivationConfig toEventDerivationConfig(KindEntity kindEntity, EntityType entityType);
}

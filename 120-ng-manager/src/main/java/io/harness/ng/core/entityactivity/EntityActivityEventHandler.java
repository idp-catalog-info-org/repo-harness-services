/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity;

import static io.harness.ng.core.activityhistory.NGActivityType.ENTITY_UPDATE;

import io.harness.beans.IdentifierRef;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.entityactivity.connector.ConnectorEntityActivityEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EntityActivityEventHandler {
  @Inject ConnectorEntityActivityEventHandler connectorEntityActivityEventHandler;

  public void updateActivityResultInEntity(NGActivityDTO ngActivityDTO) {
    if ((ENTITY_UPDATE).equals(ngActivityDTO.getType())) {
      IdentifierRef entityRef = connectorEntityActivityEventHandler.getEntityRef(ngActivityDTO);
      connectorEntityActivityEventHandler.resetPerpetualTasksForConnector(entityRef);
    }
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/licenses/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ActionLifecycleValidator {
  public static void validateStatusTransition(ActionStatus current, ActionStatus target) {
    if (current == target) {
      return;
    }
    switch (current) {
      case DRAFT:
        if (target != ActionStatus.PUBLISHED) {
          throw new InvalidRequestException("DRAFT actions can only transition to PUBLISHED");
        }
        break;
      case PUBLISHED:
        if (target != ActionStatus.DEPRECATED) {
          throw new InvalidRequestException("PUBLISHED actions can only transition to DEPRECATED");
        }
        break;
      case DEPRECATED:
        throw new InvalidRequestException("DEPRECATED is a terminal state and cannot transition");
      default:
        throw new InvalidRequestException("Unknown status: " + current);
    }
  }

  public static void validateReadyToPublish(Action action) {
    switch (action.getType()) {
      case HTTP:
        if (action.getHttpConfig() == null || isEmpty(action.getHttpConfig().getMethod())
            || (isEmpty(action.getHttpConfig().getPath()) && isEmpty(action.getHttpConfig().getUrl()))) {
          throw new InvalidRequestException(
              "HTTP actions require httpConfig with method and path (or url) before publishing");
        }
        break;
      case BUILTIN:
        if (action.getBuiltinConfig() == null || isEmpty(action.getBuiltinConfig().getHandler())) {
          throw new InvalidRequestException("Builtin actions require builtinConfig with handler set before publishing");
        }
        break;
      default:
        throw new InvalidRequestException("Unknown action type: " + action.getType());
    }
  }
}

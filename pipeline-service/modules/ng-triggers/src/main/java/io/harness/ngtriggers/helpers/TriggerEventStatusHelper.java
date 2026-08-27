/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.SKIPPED_STATUSES;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.SUCCESS_STATUSES;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.getFailedStatus;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.isSkippedResponse;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.isSuccessResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@UtilityClass
public class TriggerEventStatusHelper {
  public TriggerEventStatus toStatus(TriggerEventResponse.FinalStatus finalStatus) {
    if (!isSuccessResponse(finalStatus)) {
      if (finalStatus != null) {
        if (isSkippedResponse(finalStatus)) {
          return TriggerEventStatus.builder()
              .status(TriggerEventStatus.FinalResponse.SKIPPED)
              .message(finalStatus.getMessage())
              .build();
        }
        return TriggerEventStatus.builder()
            .status(TriggerEventStatus.FinalResponse.FAILED)
            .message(finalStatus.getMessage())
            .build();
      } else {
        return TriggerEventStatus.builder()
            .status(TriggerEventStatus.FinalResponse.FAILED)
            .message("Unknown status")
            .build();
      }
    }
    return TriggerEventStatus.builder()
        .status(TriggerEventStatus.FinalResponse.SUCCESS)
        .message(finalStatus.getMessage())
        .build();
  }

  public Set<TriggerEventResponse.FinalStatus> toListOfStatus(List<String> status) {
    Set<TriggerEventResponse.FinalStatus> finalStatus = new HashSet<>();
    for (String finalResponse : status) {
      if (TriggerEventStatus.FinalResponse.SUCCESS.toString().equals(finalResponse)) {
        finalStatus.addAll(SUCCESS_STATUSES);
      } else if (TriggerEventStatus.FinalResponse.SKIPPED.toString().equals(finalResponse)) {
        finalStatus.addAll(SKIPPED_STATUSES);
      } else if (TriggerEventStatus.FinalResponse.FAILED.toString().equals(finalResponse)) {
        finalStatus.addAll(getFailedStatus());
      }
    }
    return finalStatus;
  }
}

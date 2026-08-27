/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class KafkaEventTimeUtils {
  public String getISOFormatTime(long timeStamp) {
    return Instant.ofEpochMilli(timeStamp).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
  }

  public String getDurationInMillis(long endTs, long startTs) {
    return String.valueOf(endTs - startTs);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.ZoneId;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CronTriggerSpec implements ScheduledTriggerSpec {
  String type;
  String expression;
  ZoneId timezone;

  public void setTimeZone(String timeZoneId) {
    if (timeZoneId != null) {
      this.timezone = ZoneId.of(timeZoneId); // Throws ZoneRulesException if invalid
    } else {
      this.timezone = ZoneId.of("UTC"); // default to UTC
    }
  }
}

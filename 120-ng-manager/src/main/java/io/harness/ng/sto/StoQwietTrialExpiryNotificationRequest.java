/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.sto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoQwietTrialExpiryNotificationRequest {
  @NotNull String accountId;
  // Trial start/end as Unix epoch millis. ng-manager formats these for the email
  // and derives the trial duration (days) from their difference. expiryTimestamp
  // also gates telemetry so it fires only on the first cron attempt (within 12h
  // of expiry), avoiding the duplicate emit the repeated 12h cron would cause.
  Long startTimestamp;
  Long expiryTimestamp;
  // Scanner capabilities the trial had; used to render only the relevant badges.
  @JsonProperty("isSASTEnabled") Boolean isSASTEnabled;
  @JsonProperty("isSCAEnabled") Boolean isSCAEnabled;
  // Stable key from STO, used as the notification id so notification-service
  // deduplicates repeated cron attempts (only the first email is delivered).
  String idempotencyKey;
  String correlationId;
  List<String> internalRecipients;
}

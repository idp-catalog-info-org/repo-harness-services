/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api.aitestautomation;

import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.AI)
public interface AiTestAutomationCIBuildCallbackService {
  boolean notifyCompletion(AiTestAutomationPlaywrightCallbackRequest callbackRequest);
  String generateTiToken(String accountId);
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TemplatingStepPassThroughData implements PassThroughData {
  ChainLink completedLink;
  boolean templatingSkipped;
  Map<String, String> outputVars;
  // logKey for each post-hook stepId; computed once in startChainLinkAfterRbac so the correct
  // ambiance-based key is used both for UI registration and for K8 task submission.
  Map<String, String> postHookLogKeys;

  public enum ChainLink { PRE_HOOKS, TEMPLATING, POST_HOOKS }
}

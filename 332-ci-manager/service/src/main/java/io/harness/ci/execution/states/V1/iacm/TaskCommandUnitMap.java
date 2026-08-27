
/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.iacm;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import java.util.HashMap;
import lombok.Builder;

@Builder
@RecasterAlias("io.harness.ci.states.V1.iacm.TaskCommandUnitMap")
@OwnedBy(HarnessTeam.IACM)
public class TaskCommandUnitMap extends HashMap<String, String> implements ExecutionSweepingOutput {}
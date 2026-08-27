/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.plan.RunModeConfig;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class RunModeConfigKryoSerializer extends ProtobufKryoSerializer<RunModeConfig> {
  private static RunModeConfigKryoSerializer instance;

  private RunModeConfigKryoSerializer() {}

  public static synchronized RunModeConfigKryoSerializer getInstance() {
    if (instance == null) {
      instance = new RunModeConfigKryoSerializer();
    }
    return instance;
  }
}

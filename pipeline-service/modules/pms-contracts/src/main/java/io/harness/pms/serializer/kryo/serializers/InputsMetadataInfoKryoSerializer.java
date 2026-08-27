/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.InputsMetadataInfo;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

@OwnedBy(PIPELINE)
public class InputsMetadataInfoKryoSerializer extends ProtobufKryoSerializer<InputsMetadataInfo> {
  private static InputsMetadataInfoKryoSerializer instance;

  private InputsMetadataInfoKryoSerializer() {}

  public static synchronized InputsMetadataInfoKryoSerializer getInstance() {
    if (instance == null) {
      instance = new InputsMetadataInfoKryoSerializer();
    }
    return instance;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.executionContext;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bson.types.Binary;
import org.springframework.data.convert.ReadingConverter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Singleton
@ReadingConverter
public class ExecutionContextReadConverter extends ProtoReadConverter<ExecutionContext> {
  public ExecutionContextReadConverter() {
    super(ExecutionContext.class);
  }

  @Override
  public ExecutionContext getDeserializedValue(Binary bytes) throws InvalidProtocolBufferException {
    return ExecutionContext.parseFrom(bytes.getData());
  }
}
